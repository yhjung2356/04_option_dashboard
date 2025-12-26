package com.trading.dashboard.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.trading.dashboard.config.KisApiConfig;
import com.trading.dashboard.model.FuturesData;
import com.trading.dashboard.model.OptionData;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 한국투자증권 WebSocket 실시간 시세 수신 (단일 연결)
 * - KIS API 제약: 하나의 appkey로 동시에 하나의 WebSocket 연결만 가능
 * - 최대 40개 종목 구독 가능 (선물 1개 + 옵션 39개)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketService {

    private final KisApiConfig config;
    private final KisApiService kisApiService;
    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final Gson gson = new Gson();

    // 단일 연결 관리
    private WebSocketClient client;
    private static final int MAX_SUBSCRIPTIONS = 40;
    private static final String WS_URL = "ws://ops.koreainvestment.com:21000";

    // 구독 응답 대기용
    private final Map<String, CountDownLatch> subscriptionLatches = new ConcurrentHashMap<>();
    private final Map<String, Boolean> subscriptionResults = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[WS] KIS WebSocket Service ready (Max: {} subscriptions)", MAX_SUBSCRIPTIONS);
    }

    /**
     * WebSocket 단일 연결 시작
     * 
     * @param symbols 구독할 종목 코드 리스트 (최대 40개로 제한)
     */
    public void connectAll(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.warn("No symbols to subscribe");
            return;
        }

        // KIS API 제약: appkey당 1개 연결만 가능
        // 최대 40개로 제한
        List<String> limitedSymbols = symbols.size() > MAX_SUBSCRIPTIONS
                ? symbols.subList(0, MAX_SUBSCRIPTIONS)
                : symbols;

        if (symbols.size() > MAX_SUBSCRIPTIONS) {
            log.warn("========================================");
            log.warn("⚠️ Symbol limit exceeded: {} symbols requested", symbols.size());
            log.warn("⚠️ Subscribing to first {} symbols only", MAX_SUBSCRIPTIONS);
            log.warn("⚠️ Reason: KIS API allows only 1 WebSocket per appkey");
            log.warn("========================================");
        }

        log.info("[WS] Starting WebSocket connection for {} symbols", limitedSymbols.size());
        connectSingle(limitedSymbols);
    }

    /**
     * 단일 WebSocket 연결
     */
    private void connectSingle(List<String> symbols) {
        try {
            // 기존 연결이 있다면 먼저 정리
            if (client != null && client.isOpen()) {
                log.warn("⚠️ Closing existing WebSocket connection...");
                try {
                    client.closeBlocking(); // 블로킹 방식으로 확실하게 종료
                    log.info("[WS] Previous connection closed successfully");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while closing connection");
                }
                Thread.sleep(3000); // 서버 측 정리 충분히 대기 (2초 → 3초)
            }

            log.info("[WS] Connecting to {}", WS_URL);

            // WebSocket 전용 approval_key 발급
            final String approvalKey = kisApiService.getWebSocketApprovalKey();

            client = new WebSocketClient(new URI(WS_URL)) {
                private boolean subscriptionComplete = false;

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("[WS] WebSocket connected!");

                    // 별도 스레드에서 구독 처리
                    new Thread(() -> {
                        try {
                            // 연결 안정화 대기
                            Thread.sleep(1000); // 500ms → 1000ms로 증가

                            if (!this.isOpen()) {
                                log.error("Connection closed before subscription");
                                return;
                            }

                            log.info("[WS] Starting subscription for {} symbols...", symbols.size());

                            // 배치 처리: 10개씩 나눠서 구독
                            int batchSize = 10;
                            int totalBatches = (symbols.size() + batchSize - 1) / batchSize;
                            int successCount = 0;
                            int failCount = 0;

                            for (int batch = 0; batch < totalBatches; batch++) {
                                int start = batch * batchSize;
                                int end = Math.min(start + batchSize, symbols.size());
                                List<String> batchSymbols = symbols.subList(start, end);

                                log.info("[WS] Subscribing batch {}/{} ({} symbols)",
                                        batch + 1, totalBatches, batchSymbols.size());

                                for (String symbol : batchSymbols) {
                                    if (!this.isOpen()) {
                                        log.error("Connection closed during subscription");
                                        break;
                                    }

                                    // 구독 요청 전 Latch 생성
                                    String key = symbol;
                                    CountDownLatch latch = new CountDownLatch(1);
                                    subscriptionLatches.put(key, latch);

                                    subscribeSymbol(this, symbol, approvalKey);

                                    // 구독 응답 대기 (최대 3초)
                                    try {
                                        boolean received = latch.await(3, TimeUnit.SECONDS);
                                        if (!received) {
                                            log.warn("[WS] Subscription timeout for {}", symbol);
                                            failCount++;
                                        } else {
                                            Boolean success = subscriptionResults.get(key);
                                            if (Boolean.TRUE.equals(success)) {
                                                log.debug("[WS] Subscribed: {}", symbol);
                                                successCount++;
                                            } else {
                                                log.error("[WS] Failed: {}", symbol);
                                                failCount++;
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        log.error("[WS] Subscription interrupted");
                                        break;
                                    } finally {
                                        subscriptionLatches.remove(key);
                                        subscriptionResults.remove(key);
                                    }

                                    Thread.sleep(150); // 100ms → 150ms로 증가
                                }

                                // 배치 사이 대기 (서버 부하 방지)
                                if (batch < totalBatches - 1) {
                                    Thread.sleep(500);
                                }
                            }

                            subscriptionComplete = true;
                            log.info("[WS] Subscription completed! Success: {}, Failed: {}, Total: {}",
                                    successCount, failCount, symbols.size());

                        } catch (Exception e) {
                            log.error("Failed to subscribe: {}", e.getMessage(), e);
                        }
                    }).start();
                }

                @Override
                public void onMessage(String message) {
                    log.debug("Received: {}", message);
                    handleRealtimeData(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    String status = subscriptionComplete ? "after subscription" : "during subscription";
                    log.warn("WebSocket closed: {} - {} ({})", code, reason, status);

                    // 구독 중에 끊겼다면 재연결 시도
                    if (!subscriptionComplete && code == 1006) {
                        log.warn("[WS] Connection lost during subscription. This may be due to:");
                        log.warn("[WS] 1. Another WebSocket connection using the same appkey");
                        log.warn("[WS] 2. Network issues");
                        log.warn("[WS] 3. KIS server rejecting the connection");
                        log.warn("[WS] Please check if another instance is running with the same API key.");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket error: {}", ex.getMessage());
                }
            };

            client.connect();

        } catch (Exception e) {
            log.error("Failed to connect: {}", e.getMessage(), e);
        }
    }

    /**
     * 종목 구독 (선물/옵션 자동 판별)
     */
    private void subscribeSymbol(WebSocketClient client, String code, String approvalKey) {
        // 코드로 선물/옵션 판별
        // A로 시작: 선물 (A01603, A01606...)
        // B로 시작: 콜옵션 (B01601560...)
        // C로 시작: 풋옵션 (C01601560...)

        // 시장 시간에 따라 TR_ID 선택
        boolean isFutures = code.startsWith("A");
        boolean isNightSession = LocalTime.now().getHour() >= 18 || LocalTime.now().getHour() < 9;

        String trId;
        if (isNightSession) {
            // 야간장: H0MFCNT0 (선물), H0EUCNT0 (옵션)
            trId = isFutures ? "H0MFCNT0" : "H0EUCNT0";
        } else {
            // 주간장: H0STCNT0 (선물/옵션 통합)
            trId = "H0STCNT0";
        }

        Map<String, Object> request = new HashMap<>();
        request.put("header", Map.of(
                "approval_key", approvalKey,
                "custtype", "P",
                "tr_type", "1",
                "content-type", "utf-8"));
        request.put("body", Map.of(
                "input", Map.of(
                        "tr_id", trId,
                        "tr_key", code)));

        String requestJson = gson.toJson(request);
        log.debug("[Subscribe] {} - {}", trId, code);
        client.send(requestJson);
    }

    /**
     * 실시간 데이터 처리
     */
    private void handleRealtimeData(String message) {
        try {
            // 파이프 구분자 실시간 데이터 처리: 0|H0MFCNT0|001|A01603^...
            if (message.startsWith("0|")) {
                String[] parts = message.split("\\|", 4);
                if (parts.length >= 4) {
                    String trId = parts[1]; // H0MFCNT0 or H0EUCNT0
                    String data = parts[3]; // A01603^210919^...

                    if ("H0STCNT0".equals(trId) || "H0MFCNT0".equals(trId)) {
                        // 선물 체결 데이터
                        handleFuturesPipeData(data);
                    } else if ("H0STCNI0".equals(trId) || "H0EUCNT0".equals(trId)) {
                        // 옵션 체결 데이터
                        handleOptionPipeData(data);
                    }
                }
                return;
            }

            // JSON 형식 (구독 응답 등)
            JsonObject json = gson.fromJson(message, JsonObject.class);

            if (json.has("header") && json.has("body")) {
                JsonObject body = json.getAsJsonObject("body");

                // 구독 응답 처리
                if (body.has("msg1")) {
                    String msg1 = body.get("msg1").getAsString();
                    if ("SUBSCRIBE SUCCESS".equals(msg1)) {
                        String trKey = json.getAsJsonObject("header").get("tr_key").getAsString();
                        log.debug("[WS] SUBSCRIBE SUCCESS - {}", trKey);

                        // Latch 해제 (구독 성공)
                        CountDownLatch latch = subscriptionLatches.get(trKey);
                        if (latch != null) {
                            subscriptionResults.put(trKey, true);
                            latch.countDown();
                        }
                        return;
                    } else if (msg1.contains("INVALID") || msg1.contains("ALREADY")) {
                        String trKey = json.getAsJsonObject("header").has("tr_key")
                                ? json.getAsJsonObject("header").get("tr_key").getAsString()
                                : "UNKNOWN";
                        log.error("[Response] {} - {}", msg1, trKey);

                        // Latch 해제 (구독 실패)
                        CountDownLatch latch = subscriptionLatches.get(trKey);
                        if (latch != null) {
                            subscriptionResults.put(trKey, false);
                            latch.countDown();
                        }
                        return;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Parsing error: {}", message);
        }
    }

    /**
     * 선물 실시간 데이터 처리 (파이프 구분자 형식)
     * 형식:
     * A01603^210919^0.75^2^0.13^590.70^590.30^590.80^589.90^1^1288^190102062^...
     */
    @Transactional
    private void handleFuturesPipeData(String data) {
        try {
            String[] fields = data.split("\\^");
            if (fields.length < 12) {
                log.debug("[FUTURES] Invalid data format: {}", data);
                return;
            }

            String code = fields[0]; // A01603
            String price = fields[5]; // 현재가 (index 5)
            String volume = fields[10]; // 누적거래량 (index 10)

            log.debug("[FUTURES WS] {} - Price: {}, Volume: {}", code, price, volume);

            // DB 업데이트
            futuresDataRepository.findOptionalBySymbol(code).ifPresent(futures -> {
                futures.setCurrentPrice(new BigDecimal(price));
                futures.setVolume(Long.parseLong(volume));
                futures.setTimestamp(LocalDateTime.now());
                futuresDataRepository.save(futures);

                log.debug("[FUTURES DB] Updated {} - Price: {}, Volume: {}",
                        code, futures.getCurrentPrice(), futures.getVolume());
            });

        } catch (Exception e) {
            log.error("[FUTURES] Parse error: {}", data, e);
        }
    }

    /**
     * 옵션 실시간 데이터 처리 (파이프 구분자 형식)
     */
    @Transactional
    private void handleOptionPipeData(String data) {
        try {
            String[] fields = data.split("\\^");
            if (fields.length < 12) {
                log.debug("[OPTION] Invalid data format: {}", data);
                return;
            }

            String code = fields[0];
            String price = fields[2]; // 현재가
            String volume = fields[10]; // 누적거래량

            log.debug("[OPTION WS] {} - Price: {}, Volume: {}", code, price, volume);

            // DB 업데이트
            optionDataRepository.findOptionalBySymbol(code).ifPresent(option -> {
                option.setCurrentPrice(new BigDecimal(price));
                option.setVolume(Long.parseLong(volume));
                option.setTimestamp(LocalDateTime.now());
                optionDataRepository.save(option);

                log.debug("[OPTION DB] Updated {} - Price: {}, Volume: {}",
                        code, option.getCurrentPrice(), option.getVolume());
            });

        } catch (Exception e) {
            log.error("[OPTION] Parse error: {}", data, e);
        }
    }

    /**
     * 선물 실시간 데이터 처리
     * 가격, 거래량 실시간 업데이트
     */
    @Transactional
    private void handleFuturesData(JsonObject json) {
        try {
            JsonObject body = json.getAsJsonObject("body");
            String code = body.get("MKSC_SHRN_ISCD").getAsString();
            String price = body.get("STCK_PRPR").getAsString();

            // 누적 거래량 (acml_vol) - KIS API 필드명 (소문자)
            String volume = body.has("acml_vol") ? body.get("acml_vol").getAsString() : null;

            log.debug("[FUTURES WS] {} - Price: {}, Volume: {}", code, price, volume);

            // DB 업데이트: 가격 및 거래량
            futuresDataRepository.findOptionalBySymbol(code).ifPresent(futures -> {
                futures.setCurrentPrice(new BigDecimal(price));

                // 거래량이 있으면 업데이트
                if (volume != null && !volume.isEmpty()) {
                    try {
                        futures.setVolume(Long.parseLong(volume));
                    } catch (NumberFormatException e) {
                        log.debug("Invalid volume format: {}", volume);
                    }
                }

                futures.setTimestamp(LocalDateTime.now());
                futuresDataRepository.save(futures);

                log.debug("[FUTURES DB] Updated {} - Price: {}, Volume: {}",
                        code, futures.getCurrentPrice(), futures.getVolume());
            });

        } catch (Exception e) {
            log.debug("Error handling futures data: {}", e.getMessage());
        }
    }

    /**
     * 옵션 실시간 데이터 처리
     * 가격, 거래량 실시간 업데이트
     */
    @Transactional
    private void handleOptionData(JsonObject json) {
        try {
            JsonObject body = json.getAsJsonObject("body");
            String code = body.get("MKSC_SHRN_ISCD").getAsString();
            String price = body.get("STCK_PRPR").getAsString();

            // 누적 거래량 (acml_vol) - KIS API 필드명 (소문자)
            String volume = body.has("acml_vol") ? body.get("acml_vol").getAsString() : null;

            log.debug("[OPTION WS] {} - Price: {}, Volume: {}", code, price, volume);

            // DB 업데이트: 가격 및 거래량
            optionDataRepository.findBySymbol(code).ifPresent(option -> {
                option.setCurrentPrice(new BigDecimal(price));

                // 거래량이 있으면 업데이트
                if (volume != null && !volume.isEmpty()) {
                    try {
                        option.setVolume(Long.parseLong(volume));
                    } catch (NumberFormatException e) {
                        log.debug("Invalid volume format: {}", volume);
                    }
                }

                option.setTimestamp(LocalDateTime.now());
                optionDataRepository.save(option);

                log.debug("[OPTION DB] Updated {} - Price: {}, Volume: {}",
                        code, option.getCurrentPrice(), option.getVolume());
            });

        } catch (Exception e) {
            log.debug("Error handling option data: {}", e.getMessage());
        }
    }

    /**
     * WebSocket 연결 해제
     */
    @PreDestroy
    public void disconnect() {
        log.warn("========================================");
        log.warn("Shutting down WebSocket...");

        if (client != null && client.isOpen()) {
            try {
                log.info("[WS] Closing connection...");
                client.closeBlocking(); // 블로킹 방식으로 확실하게 종료
                log.info("[WS] WebSocket disconnected successfully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while disconnecting");
            } catch (Exception e) {
                log.error("Error while disconnecting: {}", e.getMessage());
            }
        }

        log.warn("KIS WebSocket connection closed");
        log.warn("========================================");
    }

    /**
     * 연결 상태 조회
     */
    public Map<String, Object> getConnectionStatus() {
        boolean isConnected = client != null && client.isOpen();

        return Map.of(
                "connected", isConnected,
                "maxSubscriptions", MAX_SUBSCRIPTIONS);
    }
}
