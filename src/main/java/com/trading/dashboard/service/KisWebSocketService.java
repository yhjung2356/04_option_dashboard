package com.trading.dashboard.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.trading.dashboard.config.KisApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 한국투자증권 WebSocket 실시간 시세 수신 (멀티 연결 지원)
 * - 단일 연결당 최대 40개 종목 구독 가능
 * - 122개 옵션 구독을 위해 3-4개 연결 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketService {

    private final KisApiConfig config;
    private final KisApiService kisApiService;
    private final Gson gson = new Gson();

    // 멀티 연결 관리
    private final List<WebSocketClient> clients = new CopyOnWriteArrayList<>();
    private static final int MAX_SUBSCRIPTIONS_PER_CONNECTION = 40;
    private static final String WS_URL = "ws://ops.koreainvestment.com:21000";

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Initializing KIS WebSocket Service (Multi-Connection)...");
        log.info("Max subscriptions per connection: {}", MAX_SUBSCRIPTIONS_PER_CONNECTION);
        log.info("========================================");
    }

    /**
     * WebSocket 다중 연결 시작
     * 
     * @param symbols 구독할 모든 종목 코드 리스트 (선물 + 옵션)
     */
    public void connectAll(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.warn("No symbols to subscribe");
            return;
        }

        // 40개씩 분할
        List<List<String>> batches = partitionList(symbols, MAX_SUBSCRIPTIONS_PER_CONNECTION);

        log.info("========================================");
        log.info("Starting {} WebSocket connections for {} symbols", batches.size(), symbols.size());
        log.info("========================================");

        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            int connectionId = i + 1;

            log.info("[Connection {}] Subscribing to {} symbols", connectionId, batch.size());

            connectBatch(batch, connectionId);

            // 연결 간 딜레이
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 리스트를 지정된 크기로 분할
     */
    private List<List<String>> partitionList(List<String> list, int size) {
        List<List<String>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(new ArrayList<>(
                    list.subList(i, Math.min(i + size, list.size()))));
        }
        return partitions;
    }

    /**
     * 배치 단위 WebSocket 연결
     */
    private void connectBatch(List<String> symbols, int connectionId) {
        try {
            log.info("[Connection {}] Connecting to {}", connectionId, WS_URL);

            // WebSocket 전용 approval_key 발급
            final String approvalKey = kisApiService.getWebSocketApprovalKey();

            WebSocketClient client = new WebSocketClient(new URI(WS_URL)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("✓ [Connection {}] WebSocket connected!", connectionId);

                    try {
                        // 승인 요청
                        sendApprovalRequest(this, approvalKey);

                        // 종목 구독
                        Thread.sleep(500); // 승인 후 대기

                        for (String symbol : symbols) {
                            subscribeSymbol(this, symbol, approvalKey);
                            Thread.sleep(50); // 구독 간 딜레이
                        }

                        log.info("✓ [Connection {}] Subscribed to {} symbols", connectionId, symbols.size());

                    } catch (Exception e) {
                        log.error("[Connection {}] Failed to subscribe: {}", connectionId, e.getMessage(), e);
                    }
                }

                @Override
                public void onMessage(String message) {
                    handleRealtimeData(message, connectionId);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("[Connection {}] WebSocket closed: {} - {}", connectionId, code, reason);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("[Connection {}] WebSocket error: {}", connectionId, ex.getMessage());
                }
            };

            client.connect();
            clients.add(client);

        } catch (Exception e) {
            log.error("[Connection {}] Failed to connect: {}", connectionId, e.getMessage(), e);
        }
    }

    /**
     * 승인 요청
     */
    private void sendApprovalRequest(WebSocketClient client, String approvalKey) throws Exception {
        Map<String, Object> approval = new HashMap<>();
        approval.put("header", Map.of(
                "approval_key", approvalKey,
                "custtype", "P", // 개인
                "tr_type", "1", // 등록
                "content-type", "utf-8"));

        String approvalJson = gson.toJson(approval);
        client.send(approvalJson);
    }

    /**
     * 종목 구독 (선물/옵션 자동 판별)
     */
    private void subscribeSymbol(WebSocketClient client, String code, String approvalKey) {
        // 코드로 선물/옵션 판별
        String trId;
        if (code.startsWith("A")) {
            trId = "H0STCNT0"; // 선물 실시간 체결가
        } else {
            trId = "H0STCNI0"; // 옵션 실시간 체결가
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
        client.send(requestJson);
    }

    /**
     * 실시간 데이터 처리
     */
    private void handleRealtimeData(String message, int connectionId) {
        try {
            // KIS WebSocket 응답 파싱
            JsonObject json = gson.fromJson(message, JsonObject.class);

            if (json.has("header")) {
                String trId = json.getAsJsonObject("header").get("tr_id").getAsString();

                if ("H0STCNT0".equals(trId)) {
                    // 선물 체결 데이터
                    handleFuturesData(json, connectionId);
                } else if ("H0STCNI0".equals(trId)) {
                    // 옵션 체결 데이터
                    handleOptionData(json, connectionId);
                }
            }

        } catch (Exception e) {
            log.debug("[Connection {}] Parsing message: {}", connectionId, message);
        }
    }

    /**
     * 선물 실시간 데이터 처리
     */
    private void handleFuturesData(JsonObject json, int connectionId) {
        try {
            JsonObject body = json.getAsJsonObject("body");
            String code = body.get("MKSC_SHRN_ISCD").getAsString();
            String price = body.get("STCK_PRPR").getAsString();
            String volume = body.get("CNTG_VOL").getAsString();

            log.debug("📈 [Conn{}|FUTURES] {} - Price: {}, Volume: {}", connectionId, code, price, volume);

            // TODO: DB 업데이트 또는 WebSocket 브로드캐스트

        } catch (Exception e) {
            log.debug("[Connection {}] Error handling futures data: {}", connectionId, e.getMessage());
        }
    }

    /**
     * 옵션 실시간 데이터 처리
     */
    private void handleOptionData(JsonObject json, int connectionId) {
        try {
            JsonObject body = json.getAsJsonObject("body");
            String code = body.get("MKSC_SHRN_ISCD").getAsString();
            String price = body.get("STCK_PRPR").getAsString();
            String volume = body.get("CNTG_VOL").getAsString();

            log.debug("📊 [Conn{}|OPTION] {} - Price: {}, Volume: {}", connectionId, code, price, volume);

            // TODO: DB 업데이트 또는 WebSocket 브로드캐스트

        } catch (Exception e) {
            log.debug("[Connection {}] Error handling option data: {}", connectionId, e.getMessage());
        }
    }

    /**
     * 모든 WebSocket 연결 해제
     */
    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting {} WebSocket connection(s)...", clients.size());

        for (int i = 0; i < clients.size(); i++) {
            WebSocketClient client = clients.get(i);
            if (client != null && client.isOpen()) {
                client.close();
                log.info("✓ [Connection {}] Disconnected", i + 1);
            }
        }

        clients.clear();
        log.info("All KIS WebSocket connections closed");
    }

    /**
     * 연결 상태 조회
     */
    public Map<String, Object> getConnectionStatus() {
        int totalConnections = clients.size();
        int activeConnections = (int) clients.stream()
                .filter(c -> c != null && c.isOpen())
                .count();

        return Map.of(
                "totalConnections", totalConnections,
                "activeConnections", activeConnections,
                "maxPerConnection", MAX_SUBSCRIPTIONS_PER_CONNECTION);
    }
}
