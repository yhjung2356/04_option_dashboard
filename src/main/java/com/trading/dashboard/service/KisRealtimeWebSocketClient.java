package com.trading.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.dashboard.model.FuturesData;
import com.trading.dashboard.model.OptionData;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 한국투자증권 실시간 WebSocket 클라이언트
 * 선물/옵션 실시간 체결가 및 호가 데이터 수신
 */
@Slf4j
@Component
public class KisRealtimeWebSocketClient extends WebSocketClient {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final KisApiService kisApiService;
    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;

    private String approvalKey;
    private CountDownLatch connectLatch;
    private boolean isConnected = false;

    // 구독 중인 종목 코드 관리
    private final Map<String, String> subscribedSymbols = new HashMap<>();

    public KisRealtimeWebSocketClient(
            SimpMessagingTemplate messagingTemplate,
            KisApiService kisApiService,
            ObjectMapper objectMapper,
            FuturesDataRepository futuresDataRepository,
            OptionDataRepository optionDataRepository) {
        super(URI.create("ws://ops.koreainvestment.com:21000")); // 임시 URI (initialize에서 재연결)
        this.messagingTemplate = messagingTemplate;
        this.kisApiService = kisApiService;
        this.objectMapper = objectMapper;
        this.futuresDataRepository = futuresDataRepository;
        this.optionDataRepository = optionDataRepository;
        this.connectLatch = new CountDownLatch(1);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("✅ KIS WebSocket 연결 성공!");
        isConnected = true;
        connectLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        try {
            // ⭐ 모든 서버 응답 무조건 로깅
            log.info("📩 KIS 서버 응답: {}", message);

            // 메시지 형식 확인
            if (message.startsWith("0|") || message.startsWith("1|")) {
                // 체결가 또는 호가 데이터
                parseRealtimeData(message);
            } else {
                // 구독 응답 또는 에러 메시지
                log.warn("⚠️ 비실시간 데이터 메시지: {}", message);
            }
        } catch (Exception e) {
            log.error("실시간 데이터 처리 오류: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("❌ KIS WebSocket 연결 종료: code={}, reason={}, remote={}", code, reason, remote);
        isConnected = false;

        // 재연결 시도
        attemptReconnect();
    }

    @Override
    public void onError(Exception ex) {
        log.error("❌ KIS WebSocket 오류: {}", ex.getMessage(), ex);
    }

    /**
     * WebSocket 연결 초기화 및 인증
     */
    public boolean initialize() {
        try {
            log.info("KIS 실시간 WebSocket 초기화 중...");

            // 1. Approval Key 획득
            this.approvalKey = kisApiService.getApprovalKey();
            if (approvalKey == null || approvalKey.isEmpty()) {
                log.error("Approval Key 획득 실패");
                return false;
            }
            log.info("✓ Approval Key 획득 완료");

            // 2. approval_key를 포함한 URL로 재연결
            String wsUrl = String.format(
                    "ws://ops.koreainvestment.com:21000?approval_key=%s&custtype=P&tr_type=1&content-type=utf-8",
                    approvalKey);

            // URI 재설정 및 연결
            this.uri = URI.create(wsUrl);
            this.connectLatch = new CountDownLatch(1);

            log.info("WebSocket 연결 시도: ws://ops.koreainvestment.com:21000?approval_key=***&custtype=P");
            connect();

            // 3. 연결 대기 (최대 10초)
            boolean connected = connectLatch.await(10, TimeUnit.SECONDS);
            if (!connected) {
                log.error("WebSocket 연결 타임아웃");
                return false;
            }

            log.info("✅ KIS 실시간 WebSocket 초기화 완료!");
            return true;

        } catch (Exception e) {
            log.error("WebSocket 초기화 실패: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 실시간 선물 체결가 구독
     * TR_ID: H0MFCNT0 (KRX야간선물 실시간종목체결)
     */
    public void subscribeFuturesPrice(String futuresCode) {
        try {
            String trId = "H0MFCNT0"; // KRX야간선물 실시간체결가 [실시간-064]
            String subscribeMessage = buildSubscribeMessage(trId, futuresCode);

            send(subscribeMessage);
            subscribedSymbols.put(futuresCode, trId);

            log.info("✓ 선물 실시간 체결가 구독: {}", futuresCode);
        } catch (Exception e) {
            log.error("선물 체결가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 실시간 선물 호가 구독
     * TR_ID: H0MFASP0 (KRX야간선물 실시간호가)
     */
    public void subscribeFuturesQuote(String futuresCode) {
        try {
            String trId = "H0MFASP0"; // KRX야간선물 실시간호가 [실시간-065]
            String subscribeMessage = buildSubscribeMessage(trId, futuresCode);

            send(subscribeMessage);
            subscribedSymbols.put(futuresCode + "_QUOTE", trId);

            log.info("✓ 선물 실시간 호가 구독: {}", futuresCode);
        } catch (Exception e) {
            log.error("선물 호가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 실시간 옵션 체결가 구독
     * TR_ID: H0EUCNT0 (KRX야간옵션 실시간체결가)
     */
    public void subscribeOptionsPrice(String optionCode) {
        try {
            String trId = "H0EUCNT0"; // KRX야간옵션 실시간체결가 [실시간-032]
            String subscribeMessage = buildSubscribeMessage(trId, optionCode);

            send(subscribeMessage);
            subscribedSymbols.put(optionCode, trId);

            log.info("✓ 옵션 실시간 체결가 구독: {}", optionCode);
        } catch (Exception e) {
            log.error("옵션 체결가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 실시간 옵션 호가 구독
     * TR_ID: H0EUASP0 (KRX야간옵션 실시간호가)
     */
    public void subscribeOptionsQuote(String optionCode) {
        try {
            String trId = "H0EUASP0"; // KRX야간옵션 실시간호가 [실시간-033]
            String subscribeMessage = buildSubscribeMessage(trId, optionCode);

            send(subscribeMessage);
            subscribedSymbols.put(optionCode + "_QUOTE", trId);

            log.info("✓ 옵션 실시간 호가 구독: {}", optionCode);
        } catch (Exception e) {
            log.error("옵션 호가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 구독 메시지 생성
     */
    private String buildSubscribeMessage(String trId, String symbol) {
        try {
            // KIS WebSocket 구독 메시지 표준 형식
            Map<String, Object> header = new HashMap<>();
            header.put("approval_key", approvalKey);
            header.put("custtype", "P"); // P=개인
            header.put("tr_type", "1"); // 1: 등록, 2: 해제
            header.put("content-type", "utf-8");

            // ⭐ body.input 객체로 감싸기 (필수!)
            Map<String, Object> input = new HashMap<>();
            input.put("tr_id", trId);
            input.put("tr_key", symbol);

            Map<String, Object> body = new HashMap<>();
            body.put("input", input);

            Map<String, Object> message = new HashMap<>();
            message.put("header", header);
            message.put("body", body);

            String jsonMessage = objectMapper.writeValueAsString(message);
            log.info("📤 구독 메시지 전송: {}", jsonMessage);

            return jsonMessage;

        } catch (Exception e) {
            log.error("구독 메시지 생성 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 실시간 데이터 파싱 및 전송
     */
    private void parseRealtimeData(String message) {
        try {
            // 실제 KIS 메시지 형식: 0|H0IFASP0|001|A01603^141132^581.20^...
            // parts[0] = "0" (응답 타입)
            // parts[1] = "H0IFASP0" (TR_ID)
            // parts[2] = "001" (추가 필드, 무시)
            // parts[3] = "A01603^141132^..." (실제 데이터)

            String[] parts = message.split("\\|", 4);
            if (parts.length < 4) {
                log.warn("⚠️ 잘못된 메시지 형식 (4개 부분 필요): {}", message);
                return;
            }

            String responseType = parts[0]; // 0: 실시간, 1: 스냅샷
            String trId = parts[1];
            // parts[2] 무시
            String dataWithSymbol = parts[3]; // "A01603^141132^581.20^..."

            // 심볼과 데이터 분리
            String[] dataParts = dataWithSymbol.split("\\^", 2);
            if (dataParts.length < 2) {
                log.warn("⚠️ 심볼/데이터 분리 실패: {}", dataWithSymbol);
                return;
            }

            String symbol = dataParts[0]; // "A01603"
            String data = dataParts[1]; // "141132^581.20^..."

            log.debug("📊 실시간 데이터: {} | {} | {}", trId, symbol, data.substring(0, Math.min(50, data.length())));

            // TR_ID에 따라 데이터 처리
            switch (trId) {
                case "H0IFCNT0": // 선물 체결가 (정규장)
                case "H0MFCNT0": // 선물 체결가 (야간장)
                    processFuturesPrice(symbol, data);
                    break;
                case "H0IFASP0": // 선물 호가 (정규장)
                case "H0MFASP0": // 선물 호가 (야간장)
                    processFuturesQuote(symbol, data);
                    break;
                case "H0OPCNT0": // 옵션 체결가 (정규장)
                case "H0EUCNT0": // 옵션 체결가 (야간장)
                    processOptionsPrice(symbol, data);
                    break;
                case "H0OPASP0": // 옵션 호가 (정규장)
                case "H0EUASP0": // 옵션 호가 (야간장)
                    processOptionsQuote(symbol, data);
                    break;
                default:
                    log.debug("알 수 없는 TR_ID: {}", trId);
            }

        } catch (Exception e) {
            log.error("실시간 데이터 파싱 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 선물 체결가 데이터 처리 (주간장 & 야간장 공통)
     * TR_ID: H0IFCNT0 (주간 선물), H0MFCNT0 (야간 선물)
     * 형식: 시간^현재가^전일대비^거래량^...
     * 주의: KIS API는 주간장/야간장 모두 순수 거래량/거래대금을 전송합니다!
     */
    private void processFuturesPrice(String symbol, String data) {
        try {
            // 데이터 파싱 (구분자: ^)
            String[] fields = data.split("\\^");

            // KIS API 선물 체결 필드 매핑 (주간/야간 동일):
            // fields[1] = 영업시간 (bsop_hour), fields[5] = 현재가 (futs_prpr)
            // fields[10] = 누적 거래량 (acml_vol) ✅ 주간/야간 순수 거래량!
            // fields[11] = 누적 거래대금 (acml_tr_pbmn) ✅ 주간/야간 순수 거래대금!
            String currentPriceStr = fields.length > 5 ? fields[5] : "0";
            String changeStr = fields.length > 2 ? fields[2] : "0";
            String volumeStr = fields.length > 10 ? fields[10] : "0";
            String tradingValueStr = fields.length > 11 ? fields[11] : "0";

            // DB 업데이트 (KIS API가 이미 야간장 순수 거래량을 보내주므로 그대로 사용)
            FuturesData futures = futuresDataRepository.findBySymbol(symbol);
            if (futures != null) {
                try {
                    BigDecimal currentPrice = new BigDecimal(currentPriceStr);
                    futures.setCurrentPrice(currentPrice);

                    // KIS API가 이미 주간/야간 순수 거래량/거래대금을 전송하므로 그대로 사용
                    Long volume = Long.parseLong(volumeStr);
                    futures.setVolume(volume);

                    // 거래대금 변환: 천원 -> 억원 (100,000으로 나누기)
                    // KIS API는 거래대금을 천원 단위로 전송 (예: 482235237 = 482,235,237천원 = 4,822억원)
                    BigDecimal tradingValueInThousandWon = new BigDecimal(tradingValueStr);
                    BigDecimal tradingValueInEokWon = tradingValueInThousandWon.divide(new BigDecimal("100000"), 2,
                            RoundingMode.HALF_UP);
                    futures.setTradingValue(tradingValueInEokWon);

                    futuresDataRepository.save(futures);
                    log.debug("💾 선물 DB 업데이트: {} | 가격={} 거래량={} 거래대금={}(억)",
                            symbol, currentPrice, volume, tradingValueInEokWon);
                } catch (NumberFormatException e) {
                    log.warn("⚠️ 데이터 변환 실패: {} | 가격={} 거래량={} 거래대금={}",
                            symbol, currentPriceStr, volumeStr, tradingValueStr);
                }
            } else {
                log.warn("⚠️ DB에 종목 없음: {} - 거래량={}", symbol, volumeStr);
            }

            // STOMP로 전송
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("symbol", symbol);
            priceData.put("currentPrice", currentPriceStr);
            priceData.put("change", changeStr);
            priceData.put("volume", volumeStr);
            priceData.put("tradingValue", tradingValueStr);
            priceData.put("timestamp", System.currentTimeMillis());

            // STOMP로 전송
            messagingTemplate.convertAndSend("/topic/futures/realtime", priceData);

            log.debug("✅ 선물 체결가 전송: {} | 가격={} 거래량={} 거래대금={}",
                    symbol, priceData.get("currentPrice"), volumeStr, tradingValueStr);

        } catch (Exception e) {
            log.error("선물 체결가 처리 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 선물 호가 데이터 처리
     * 형식: 시간^매도1^매도2^...^매도5^매수1^매수2^...^매수5^매도수량1^...^매수수량1^...
     */
    private void processFuturesQuote(String symbol, String data) {
        try {
            String[] fields = data.split("\\^");

            Map<String, Object> quoteData = new HashMap<>();
            quoteData.put("symbol", symbol);
            // fields[0] = 시간 (무시)
            // fields[1] = 매도호가1, fields[6] = 매수호가1
            quoteData.put("askPrice1", fields.length > 1 ? fields[1] : "0");
            quoteData.put("bidPrice1", fields.length > 6 ? fields[6] : "0");
            // fields[11] = 매도수량1, fields[16] = 매수수량1
            quoteData.put("askVolume1", fields.length > 11 ? fields[11] : "0");
            quoteData.put("bidVolume1", fields.length > 16 ? fields[16] : "0");
            quoteData.put("timestamp", System.currentTimeMillis());

            // STOMP로 전송
            messagingTemplate.convertAndSend("/topic/futures/quote", quoteData);

            log.info("✅ 선물 호가 전송: {} | 매수={} 매도={}", symbol, quoteData.get("bidPrice1"), quoteData.get("askPrice1"));

        } catch (Exception e) {
            log.error("선물 호가 처리 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 옵션 체결가 데이터 처리 (주간장 & 야간장 공통)
     * TR_ID: H0OPCNT0 (주간 옵션), H0EUCNT0 (야간 옵션)
     * 주의: KIS API는 주간장/야간장 모두 순수 거래량/거래대금을 전송합니다!
     */
    private void processOptionsPrice(String symbol, String data) {
        try {
            String[] fields = data.split("\\^");

            // KIS API 옵션 체결 필드 매핑 (주간/야간 동일):
            // fields[2] = 현재가 (optn_prpr), fields[4] = 전일대비 (optn_prdy_vrss)
            // fields[10] = 누적 거래량 (acml_vol) ✅ 주간/야간 순수 거래량!
            // fields[11] = 누적 거래대금 (acml_tr_pbmn) ✅ 주간/야간 순수 거래대금!
            String currentPriceStr = fields.length > 2 ? fields[2] : "0";
            String changeStr = fields.length > 4 ? fields[4] : "0";
            String volumeStr = fields.length > 10 ? fields[10] : "0";
            String tradingValueStr = fields.length > 11 ? fields[11] : "0";

            // DB 업데이트
            OptionData option = optionDataRepository.findBySymbol(symbol);
            if (option != null) {
                try {
                    BigDecimal currentPrice = new BigDecimal(currentPriceStr);
                    option.setCurrentPrice(currentPrice);

                    // KIS API가 이미 주간/야간 순수 거래량/거래대금을 전송하므로 그대로 사용
                    Long volume = Long.parseLong(volumeStr);
                    option.setVolume(volume);

                    // 거래대금 변환: 천원 -> 억원 (100,000으로 나누기)
                    // KIS API는 거래대금을 천원 단위로 전송
                    BigDecimal tradingValueInThousandWon = new BigDecimal(tradingValueStr);
                    BigDecimal tradingValueInEokWon = tradingValueInThousandWon.divide(new BigDecimal("100000"), 2,
                            RoundingMode.HALF_UP);
                    option.setTradingValue(tradingValueInEokWon);

                    optionDataRepository.save(option);
                    log.debug("💾 옵션 DB 업데이트: {} | 가격={} 거래량={} 거래대금={}(억)",
                            symbol, currentPrice, volume, tradingValueInEokWon);
                } catch (NumberFormatException e) {
                    log.warn("데이터 변환 실패: {} | 가격={} 거래량={} 거래대금={}",
                            symbol, currentPriceStr, volumeStr, tradingValueStr);
                }
            } else {
                log.warn("⚠️ DB에 종목 없음: {} - 거래량={}", symbol, volumeStr);
            }

            // STOMP로 전송
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("symbol", symbol);
            priceData.put("currentPrice", currentPriceStr);
            priceData.put("change", changeStr);
            priceData.put("volume", volumeStr);
            priceData.put("tradingValue", tradingValueStr);
            priceData.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/options/realtime", priceData);

            log.debug("✅ 옵션 실시간 체결가: {} | 가격={} 거래량={} 거래대금={}",
                    symbol, currentPriceStr, volumeStr, tradingValueStr);

        } catch (Exception e) {
            log.error("옵션 체결가 처리 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 옵션 호가 데이터 처리
     */
    private void processOptionsQuote(String symbol, String data) {
        try {
            String[] fields = data.split("\\^");

            Map<String, Object> quoteData = new HashMap<>();
            quoteData.put("symbol", symbol);
            quoteData.put("bidPrice1", fields.length > 0 ? fields[0] : "0");
            quoteData.put("askPrice1", fields.length > 1 ? fields[1] : "0");
            quoteData.put("bidVolume1", fields.length > 2 ? fields[2] : "0");
            quoteData.put("askVolume1", fields.length > 3 ? fields[3] : "0");
            quoteData.put("timestamp", System.currentTimeMillis());

            // STOMP로 전송
            messagingTemplate.convertAndSend("/topic/options/quote", quoteData);

            log.debug("옵션 실시간 호가: {} = {}/{}", symbol, quoteData.get("bidPrice1"), quoteData.get("askPrice1"));

        } catch (Exception e) {
            log.error("옵션 호가 처리 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 재연결 시도
     */
    private void attemptReconnect() {
        // WebSocket 스레드가 아닌 별도 스레드에서 재연결 실행
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 5초 대기
                log.info("WebSocket 재연결 시도...");

                connectLatch = new CountDownLatch(1);
                reconnect();

            } catch (Exception e) {
                log.error("재연결 실패: {}", e.getMessage(), e);
            }
        }, "WebSocket-Reconnect-Thread").start();
    }

    public boolean isConnected() {
        return isConnected && isOpen();
    }
}
