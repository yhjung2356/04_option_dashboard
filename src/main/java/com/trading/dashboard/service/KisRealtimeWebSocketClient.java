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
 * 
 * 주의: @Component 제거 - 각 TR_ID마다 인스턴스를 직접 생성해야 함
 */
@Slf4j
public class KisRealtimeWebSocketClient extends WebSocketClient {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final KisApiService kisApiService;
    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final SymbolMasterService symbolMasterService;
    private final String trId; // 이 WebSocket이 처리할 TR_ID

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
            OptionDataRepository optionDataRepository,
            SymbolMasterService symbolMasterService,
            String trId) {
        super(URI.create("ws://ops.koreainvestment.com:21000")); // 임시 URI (initialize에서 재연결)
        this.messagingTemplate = messagingTemplate;
        this.kisApiService = kisApiService;
        this.objectMapper = objectMapper;
        this.futuresDataRepository = futuresDataRepository;
        this.optionDataRepository = optionDataRepository;
        this.symbolMasterService = symbolMasterService;
        this.trId = trId;
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
    public boolean initialize(String approvalKey) {
        try {
            log.info("[{}] KIS 실시간 WebSocket 초기화 중...", trId);

            // 1. Approval Key 설정
            this.approvalKey = approvalKey;
            if (approvalKey == null || approvalKey.isEmpty()) {
                log.error("[{}] Approval Key 획득 실패", trId);
                return false;
            }
            log.info("[{}] ✓ Approval Key 획득 완료", trId);

            // 2. approval_key를 포함한 URL로 재연결
            String wsUrl = String.format(
                    "ws://ops.koreainvestment.com:21000?approval_key=%s&custtype=P&tr_type=1&content-type=utf-8",
                    approvalKey);

            // URI 재설정 및 연결
            this.uri = URI.create(wsUrl);
            this.connectLatch = new CountDownLatch(1);

            log.info("[{}] WebSocket 연결 시도: ws://ops.koreainvestment.com:21000?approval_key=***&custtype=P", trId);
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
     * TR_ID: this.trId (생성자에서 주입된 TR_ID 사용)
     */
    public void subscribeFuturesPrice(String futuresCode) {
        try {
            // 연결 상태 확인 및 대기
            if (!isOpen()) {
                log.warn("⚠️ WebSocket 연결 대기 중... ({})", futuresCode);
                Thread.sleep(1000); // 1초 대기

                if (!isOpen()) {
                    log.error("❌ WebSocket 미연결 상태 - 구독 실패: {}", futuresCode);
                    return;
                }
            }

            // FIXED: 생성자에서 주입된 this.trId 사용
            String subscribeMessage = buildSubscribeMessage(this.trId, futuresCode);

            send(subscribeMessage);
            subscribedSymbols.put(futuresCode, this.trId);

            log.info("✓ 선물 실시간 체결가 구독: {}", futuresCode);
        } catch (Exception e) {
            log.error("선물 체결가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 실시간 선물 호가 구독
     * TR_ID: this.trId (생성자에서 주입된 TR_ID 사용)
     */
    public void subscribeFuturesQuote(String futuresCode) {
        try {
            // 연결 상태 확인 및 대기
            if (!isOpen()) {
                log.warn("⚠️ WebSocket 연결 대기 중... ({})", futuresCode);
                Thread.sleep(1000); // 1초 대기

                if (!isOpen()) {
                    log.error("❌ WebSocket 미연결 상태 - 구독 실패: {}", futuresCode);
                    return;
                }
            }

            // FIXED: 생성자에서 주입된 this.trId 사용
            String subscribeMessage = buildSubscribeMessage(this.trId, futuresCode);

            send(subscribeMessage);
            subscribedSymbols.put(futuresCode + "_QUOTE", this.trId);

            log.info("✓ 선물 실시간 호가 구독: {}", futuresCode);
        } catch (Exception e) {
            log.error("선물 호가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 실시간 옵션 체결가 구독
     * TR_ID: this.trId (생성자에서 주입된 TR_ID 사용)
     */
    public void subscribeOptionsPrice(String optionCode) {
        try {
            // 연결 상태 확인 및 대기
            if (!isOpen()) {
                log.warn("⚠️ WebSocket 연결 대기 중... ({})", optionCode);
                Thread.sleep(1000); // 1초 대기

                if (!isOpen()) {
                    log.error("❌ WebSocket 미연결 상태 - 구독 실패: {}", optionCode);
                    return;
                }
            }

            // FIXED: 생성자에서 주입된 this.trId 사용 (H0IOCNT0 주간장 / H0EUCNT0 야간장)
            String subscribeMessage = buildSubscribeMessage(this.trId, optionCode);

            send(subscribeMessage);
            subscribedSymbols.put(optionCode, this.trId);

            log.info("✓ 옵션 실시간 체결가 구독: {}", optionCode);
        } catch (Exception e) {
            log.error("옵션 체결가 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 실시간 옵션 호가 구독
     * TR_ID: this.trId (생성자에서 주입된 TR_ID 사용)
     */
    public void subscribeOptionsQuote(String optionCode) {
        try {
            // 연결 상태 확인 및 대기
            if (!isOpen()) {
                log.warn("⚠️ WebSocket 연결 대기 중... ({})", optionCode);
                Thread.sleep(1000); // 1초 대기

                if (!isOpen()) {
                    log.error("❌ WebSocket 미연결 상태 - 구독 실패: {}", optionCode);
                    return;
                }
            }

            // FIXED: 생성자에서 주입된 this.trId 사용
            String subscribeMessage = buildSubscribeMessage(this.trId, optionCode);

            send(subscribeMessage);
            subscribedSymbols.put(optionCode + "_QUOTE", this.trId);

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
                case "H0IOCNT0": // 지수옵션 체결가 (주간장) ✅ 추가!
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

            // 원본 데이터 로깅 (디버깅용)
            log.info("📥 선물 원본 데이터 [{}]: fields.length={} | [5]={} [10]={} [11]={} [18]={}",
                    symbol, fields.length,
                    fields.length > 5 ? fields[5] : "N/A",
                    fields.length > 10 ? fields[10] : "N/A",
                    fields.length > 11 ? fields[11] : "N/A",
                    fields.length > 18 ? fields[18] : "N/A");

            // H0IFCNT0 선물 실시간 체결가 필드 매핑 (API 가이드 기준):
            // fields[5] = FUTS_PRPR (선물 현재가)
            // fields[10] = ACML_VOL (누적 거래량)
            // fields[11] = ACML_TR_PBMN (누적 거래대금, 천원 단위)
            // fields[18] = HTS_OTST_STPL_QTY (HTS 미결제 약정 수량)
            // fields[19] = OTST_STPL_QTY_ICDC (미결제 약정 수량 증감)
            // fields[35] = FUTS_ASKP1 (선물 매도호가1)
            // fields[36] = FUTS_BIDP1 (선물 매수호가1)
            // fields[37] = ASKP_RSQN1 (매도호가 잔량1)
            // fields[38] = BIDP_RSQN1 (매수호가 잔량1)
            String currentPriceStr = fields.length > 5 ? fields[5] : "0";
            String changeStr = fields.length > 2 ? fields[2] : "0";
            String volumeStr = fields.length > 10 ? fields[10] : "0";
            String tradingValueStr = fields.length > 11 ? fields[11] : "0";
            String openInterestStr = fields.length > 18 ? fields[18] : "0";
            String openInterestChangeStr = fields.length > 19 ? fields[19] : "0";

            // 호가 데이터
            String askPriceStr = fields.length > 35 ? fields[35] : "0";
            String bidPriceStr = fields.length > 36 ? fields[36] : "0";
            String askVolumeStr = fields.length > 37 ? fields[37] : "0";
            String bidVolumeStr = fields.length > 38 ? fields[38] : "0";

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
                    log.info("💾 선물 DB 업데이트: {} | 가격={} 거래량={} 거래대금={}(억)",
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
            priceData.put("openInterest", openInterestStr);
            priceData.put("openInterestChange", openInterestChangeStr);
            priceData.put("askPrice", askPriceStr);
            priceData.put("bidPrice", bidPriceStr);
            priceData.put("askVolume", askVolumeStr);
            priceData.put("bidVolume", bidVolumeStr);
            priceData.put("timestamp", System.currentTimeMillis());

            // STOMP로 전송
            messagingTemplate.convertAndSend("/topic/futures/realtime", priceData);

            log.debug("✅ 선물 체결가 전송: {} | 가격={} 매도={} 매수={} 거래량={} 미결제={}",
                    symbol, currentPriceStr, askPriceStr, bidPriceStr, volumeStr, openInterestStr);

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

            // 원본 데이터 로깅 (디버깅용 - 처음 5개만)
            if (Math.random() < 0.05) { // 5% 확률로 로깅 (너무 많은 로그 방지)
                log.info("📥 옵션 원본 데이터 [{}]: fields.length={} | [2]={} [10]={} [11]={} [13]={} [28]={} [29]={}",
                        symbol, fields.length,
                        fields.length > 2 ? fields[2] : "N/A",
                        fields.length > 10 ? fields[10] : "N/A",
                        fields.length > 11 ? fields[11] : "N/A",
                        fields.length > 13 ? fields[13] : "N/A",
                        fields.length > 28 ? fields[28] : "N/A",
                        fields.length > 29 ? fields[29] : "N/A");
            }

            // H0IOCNT0 옵션 실시간 체결가 필드 매핑 (API 가이드 기준):
            // fields[2] = OPTN_PRPR (옵션 현재가)
            // fields[4] = OPTN_PRDY_VRSS (옵션 전일 대비)
            // fields[10] = ACML_VOL (누적 거래량)
            // fields[11] = ACML_TR_PBMN (누적 거래대금, 천원 단위)
            // fields[12] = HTS_THPR (HTS 이론가)
            // fields[13] = HTS_OTST_STPL_QTY (HTS 미결제 약정 수량)
            // fields[14] = OTST_STPL_QTY_ICDC (미결제 약정 수량 증감)
            // fields[26] = INVL_VAL (내재가치 값)
            // fields[27] = TMVL_VAL (시간가치 값)
            // fields[28] = DELTA (델타)
            // fields[29] = GAMA (감마)
            // fields[30] = VEGA (베가)
            // fields[31] = THETA (세타)
            // fields[32] = RHO (로우)
            // fields[33] = HTS_INTS_VLTL (HTS 내재 변동성)
            // fields[41] = OPTN_ASKP1 (옵션 매도호가1)
            // fields[42] = OPTN_BIDP1 (옵션 매수호가1)
            // fields[43] = ASKP_RSQN1 (매도호가 잔량1)
            // fields[44] = BIDP_RSQN1 (매수호가 잔량1)
            String currentPriceStr = fields.length > 2 ? fields[2] : "0";
            String changeStr = fields.length > 4 ? fields[4] : "0";

            // 거래량/거래대금/미결제
            String volumeStr = fields.length > 10 ? fields[10] : "0";
            String tradingValueStr = fields.length > 11 ? fields[11] : "0";
            String openInterestStr = fields.length > 13 ? fields[13] : "0";
            String openInterestChangeStr = fields.length > 14 ? fields[14] : "0";

            // 이론가/내재가치/시간가치
            String theoreticalPriceStr = fields.length > 12 ? fields[12] : null;
            String intrinsicValueStr = fields.length > 26 ? fields[26] : null;
            String timeValueStr = fields.length > 27 ? fields[27] : null;

            // Greeks 데이터
            String deltaStr = fields.length > 28 ? fields[28] : null;
            String gammaStr = fields.length > 29 ? fields[29] : null;
            String vegaStr = fields.length > 30 ? fields[30] : null;
            String thetaStr = fields.length > 31 ? fields[31] : null;
            String rhoStr = fields.length > 32 ? fields[32] : null;
            String impliedVolatilityStr = fields.length > 33 ? fields[33] : null;

            // 호가 데이터
            String askPriceStr = fields.length > 41 ? fields[41] : "0";
            String bidPriceStr = fields.length > 42 ? fields[42] : "0";
            String askVolumeStr = fields.length > 43 ? fields[43] : "0";
            String bidVolumeStr = fields.length > 44 ? fields[44] : "0";

            // DB 업데이트
            try {
                OptionData option = optionDataRepository.findBySymbol(symbol);
                if (option != null) {
                    try {
                        BigDecimal currentPrice = new BigDecimal(currentPriceStr);
                        option.setCurrentPrice(currentPrice);

                        // 호가 데이터 설정
                        if (!"0".equals(askPriceStr) && !askPriceStr.isEmpty()) {
                            option.setAskPrice(new BigDecimal(askPriceStr));
                        }
                        if (!"0".equals(bidPriceStr) && !bidPriceStr.isEmpty()) {
                            option.setBidPrice(new BigDecimal(bidPriceStr));
                        }

                        // 호가 잔량 설정
                        if (!"0".equals(askVolumeStr) && !askVolumeStr.isEmpty()) {
                            option.setAskVolume(Integer.parseInt(askVolumeStr));
                        }
                        if (!"0".equals(bidVolumeStr) && !bidVolumeStr.isEmpty()) {
                            option.setBidVolume(Integer.parseInt(bidVolumeStr));
                        }

                        // 이론가/내재가치/시간가치 설정
                        if (theoreticalPriceStr != null && !theoreticalPriceStr.isEmpty()
                                && !"0".equals(theoreticalPriceStr)) {
                            option.setTheoreticalPrice(new BigDecimal(theoreticalPriceStr));
                        }
                        if (intrinsicValueStr != null && !intrinsicValueStr.isEmpty()
                                && !"0".equals(intrinsicValueStr)) {
                            option.setIntrinsicValue(new BigDecimal(intrinsicValueStr));
                        }
                        if (timeValueStr != null && !timeValueStr.isEmpty() && !"0".equals(timeValueStr)) {
                            option.setTimeValue(new BigDecimal(timeValueStr));
                        }

                        // Greeks 데이터 설정 (실시간 업데이트)
                        if (deltaStr != null && !deltaStr.isEmpty() && !"0".equals(deltaStr)) {
                            option.setDelta(new BigDecimal(deltaStr));
                        }
                        if (gammaStr != null && !gammaStr.isEmpty() && !"0".equals(gammaStr)) {
                            option.setGamma(new BigDecimal(gammaStr));
                        }
                        if (vegaStr != null && !vegaStr.isEmpty() && !"0".equals(vegaStr)) {
                            option.setVega(new BigDecimal(vegaStr));
                        }
                        if (thetaStr != null && !thetaStr.isEmpty() && !"0".equals(thetaStr)) {
                            option.setTheta(new BigDecimal(thetaStr));
                        }
                        if (rhoStr != null && !rhoStr.isEmpty() && !"0".equals(rhoStr)) {
                            option.setRho(new BigDecimal(rhoStr));
                        }
                        if (impliedVolatilityStr != null && !impliedVolatilityStr.isEmpty()
                                && !"0".equals(impliedVolatilityStr)) {
                            option.setImpliedVolatility(new BigDecimal(impliedVolatilityStr));
                        }

                        // 미결제 증감 설정
                        if (openInterestChangeStr != null && !openInterestChangeStr.isEmpty()
                                && !"0".equals(openInterestChangeStr)) {
                            option.setOpenInterestChange(Long.parseLong(openInterestChangeStr));
                        }

                        // 미결제약정 설정
                        if (!"0".equals(openInterestStr) && !openInterestStr.isEmpty()) {
                            option.setOpenInterest(Long.parseLong(openInterestStr));
                        }

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
                        log.info("💾 옵션 DB 업데이트: {} | 가격={} 거래량={} 거래대금={}(억)",
                                symbol, currentPrice, volume, tradingValueInEokWon);
                    } catch (NumberFormatException e) {
                        log.warn("데이터 변환 실패: {} | 가격={} 거래량={} 거래대금={}",
                                symbol, currentPriceStr, volumeStr, tradingValueStr);
                    }
                } else {
                    log.warn("⚠️ DB에 종목 없음: {} - 거래량={}", symbol, volumeStr);
                }
            } catch (IllegalStateException e) {
                // ApplicationContext가 종료된 경우 - 정상적인 종료 과정
                log.debug("⚠️ ApplicationContext 종료됨 - DB 업데이트 스킵: {}", symbol);
                return; // DB 업데이트 실패시 STOMP 전송도 스킵
            }

            // STOMP로 전송
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("symbol", symbol);
            priceData.put("currentPrice", currentPriceStr);
            priceData.put("change", changeStr);
            priceData.put("volume", volumeStr);
            priceData.put("tradingValue", tradingValueStr);
            priceData.put("openInterest", openInterestStr);
            priceData.put("openInterestChange", openInterestChangeStr);
            priceData.put("askPrice", askPriceStr);
            priceData.put("bidPrice", bidPriceStr);
            priceData.put("askVolume", askVolumeStr);
            priceData.put("bidVolume", bidVolumeStr);
            priceData.put("theoreticalPrice", theoreticalPriceStr);
            priceData.put("intrinsicValue", intrinsicValueStr);
            priceData.put("timeValue", timeValueStr);
            priceData.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/options/realtime", priceData);

            log.debug("✅ 옵션 실시간 체결가: {} | 가격={} 매도={} 매수={} 거래량={} 미결제={}",
                    symbol, currentPriceStr, askPriceStr, bidPriceStr, volumeStr, openInterestStr);

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
