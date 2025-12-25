package com.trading.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KIS 실시간 WebSocket 관리 서비스
 * 애플리케이션 시작 시 자동으로 WebSocket 연결 및 구독
 * TR_ID별로 별도의 WebSocket 연결 관리
 */
@Slf4j
@Service
public class KisRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;
    private final KisApiService kisApiService;
    private final ObjectMapper objectMapper;
    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final SymbolMasterService symbolMasterService;
    private final TradingCalendarService tradingCalendarService;

    // TR_ID별 WebSocket 클라이언트 관리
    private final Map<String, KisRealtimeWebSocketClient> webSocketClients = new HashMap<>();

    public KisRealtimeService(
            SimpMessagingTemplate messagingTemplate,
            KisApiService kisApiService,
            ObjectMapper objectMapper,
            FuturesDataRepository futuresDataRepository,
            OptionDataRepository optionDataRepository,
            SymbolMasterService symbolMasterService,
            TradingCalendarService tradingCalendarService) {
        this.messagingTemplate = messagingTemplate;
        this.kisApiService = kisApiService;
        this.objectMapper = objectMapper;
        this.futuresDataRepository = futuresDataRepository;
        this.optionDataRepository = optionDataRepository;
        this.symbolMasterService = symbolMasterService;
        this.tradingCalendarService = tradingCalendarService;

        // TR_ID별 WebSocket 클라이언트 생성
        initializeWebSocketClients();
    }

    /**
     * TR_ID별 WebSocket 클라이언트 초기화
     * 
     * 주간장(09:00-15:45):
     * - H0IOCNT0: 옵션 체결가 (현재가, 거래량)
     * - H0IFASP0: 선물 호가 (매수/매도 가격, 잔량)
     * 
     * 야간장(18:00-05:00):
     * - H0EUCNT0: KRX야간옵션 실시간체결가
     * - H0MFCNT0: KRX야간선물 실시간종목체결
     */
    private void initializeWebSocketClients() {
        // 현재 시간 기준으로 주간/야간 TR_ID 선택
        String[] trIds = getActiveTrIds();

        log.info("현재 시간대: {} / 사용할 TR_ID: {}",
                isNightSession() ? "야간장" : "주간장",
                String.join(", ", trIds));

        for (String trId : trIds) {
            KisRealtimeWebSocketClient client = new KisRealtimeWebSocketClient(
                    messagingTemplate,
                    kisApiService,
                    objectMapper,
                    futuresDataRepository,
                    optionDataRepository,
                    symbolMasterService,
                    trId);
            webSocketClients.put(trId, client);
            log.info("✓ WebSocket 클라이언트 생성: {}", trId);
        }
    }

    /**
     * 현재 시간이 야간장인지 확인
     * 주간장: 09:00 - 15:50
     * 야간장: 18:00 - 익일 05:00
     */
    private boolean isNightSession() {
        LocalTime now = LocalTime.now();
        LocalTime dayStart = LocalTime.of(9, 0); // 09:00
        LocalTime dayEnd = LocalTime.of(15, 50); // 15:50 (선물 마감)

        // 주간장 시간이 아니면 야간장
        // 주간장: 09:00 ~ 15:50
        boolean isDaySession = !now.isBefore(dayStart) && !now.isAfter(dayEnd);
        return !isDaySession;
    }

    /**
     * 현재 시간대에 맞는 TR_ID 배열 반환
     */
    private String[] getActiveTrIds() {
        if (isNightSession()) {
            // 야간장 TR_ID
            log.info("🌙 야간장 모드: H0EUCNT0 (옵션), H0MFCNT0 (선물)");
            return new String[] { "H0EUCNT0", "H0MFCNT0" };
        } else {
            // 주간장 TR_ID (체결가에 호가 포함)
            log.info("☀️ 주간장 모드: H0IOCNT0 (옵션), H0IFCNT0 (선물)");
            return new String[] { "H0IOCNT0", "H0IFCNT0" };
        }
    }

    /**
     * 애플리케이션 시작 후 WebSocket 자동 연결
     * 각 WebSocket은 독립적인 approval key 사용
     * 휴장일에는 WebSocket 연결하지 않음
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeRealtimeConnection() {
        try {
            // 휴장일 체크
            if (!tradingCalendarService.isTradingDay()) {
                log.info("=".repeat(60));
                log.info("📅 오늘은 휴장일입니다. 실시간 WebSocket 연결을 생략합니다.");
                log.info("📊 전거래일 데이터가 표시됩니다.");
                log.info("=".repeat(60));
                return;
            }

            log.info("=".repeat(60));
            log.info("KIS 실시간 WebSocket 연결 초기화 시작...");
            log.info("=".repeat(60));

            // 각 WebSocket 클라이언트별로 독립적인 approval key 발급 및 연결
            for (Map.Entry<String, KisRealtimeWebSocketClient> entry : webSocketClients.entrySet()) {
                String trId = entry.getKey();
                KisRealtimeWebSocketClient client = entry.getValue();

                log.info("🔑 [{}] Approval Key 발급 중...", trId);
                String approvalKey = kisApiService.getApprovalKey();
                if (approvalKey == null || approvalKey.isEmpty()) {
                    log.error("❌ [{}] Approval Key 획득 실패", trId);
                    continue;
                }
                log.info("✅ [{}] Approval Key 획득 성공: {}***", trId, approvalKey.substring(0, 8));

                log.info("🔌 [{}] WebSocket 연결 중...", trId);
                boolean connected = client.initialize(approvalKey); // 독립적인 key 사용

                if (!connected) {
                    log.error("❌ [{}] WebSocket 연결 실패", trId);
                } else {
                    log.info("✅ [{}] WebSocket 연결 완료", trId);
                }

                Thread.sleep(2000); // 연결 간격 (각 approval key 발급 후 대기)
            }

            // 3초 대기 (연결 안정화)
            Thread.sleep(3000);

            // 선물 실시간 구독
            subscribeAllFutures();

            // 2초 대기
            Thread.sleep(2000);

            // 옵션 실시간 구독
            subscribeAllOptions();

            log.info("=".repeat(60));
            log.info("✅ KIS 실시간 시세 구독 완료!");
            log.info("=".repeat(60));

        } catch (Exception e) {
            log.error("실시간 연결 초기화 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 모든 선물 종목 실시간 구독
     * 주간장: H0IFCNT0 (체결가) ← H0IFASP0(호가)에서 변경
     * 야간장: H0MFCNT0 (체결가)
     */
    private void subscribeAllFutures() {
        try {
            // DB에서 실제 종목 코드 가져오기
            var futures = futuresDataRepository.findAll();

            if (futures.isEmpty()) {
                log.warn("⚠️ DB에 선물 종목이 없습니다!");
                return;
            }

            // 현재 시간대에 따라 자동 전환
            boolean nightSession = isNightSession();
            String trId = nightSession ? "H0MFCNT0" : "H0IFCNT0";
            KisRealtimeWebSocketClient futuresClient = webSocketClients.get(trId);

            if (futuresClient == null) {
                log.error("선물 WebSocket 클라이언트를 찾을 수 없습니다: {}", trId);
                return;
            }

            String sessionType = nightSession ? "야간장" : "주간장";
            log.info("📊 {} 전체 선물 종목 실시간 구독 시작 ({})", sessionType, trId);

            for (var future : futures) {
                String restSymbol = future.getSymbol(); // A01603 같은 REST API 심볼

                // REST 코드를 WebSocket 코드로 변환
                String wsSymbol = symbolMasterService.convertToWebSocketCode(restSymbol);

                log.info("✓ {}선물 실시간 구독: {} → {} ({})", sessionType, restSymbol, wsSymbol, trId);

                // 체결가 구독
                futuresClient.subscribeFuturesPrice(wsSymbol);
                Thread.sleep(300);
            }

            log.info("✓ {}선물 {} 종목 실시간 구독 완료 ({})", sessionType, futures.size(), trId);

        } catch (Exception e) {
            log.error("선물 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 모든 옵션 종목 실시간 구독
     * 주간장: H0IOCNT0 (체결가) - 10자리 코드 (B01601570)
     * 야간장: H0EUCNT0 (체결가) - 8자리 코드 (101W9000) - 야간장 전용 코드!
     */
    private void subscribeAllOptions() {
        try {
            // 현재 시간대에 따라 자동 전환
            boolean nightSession = isNightSession();
            String trId = nightSession ? "H0EUCNT0" : "H0IOCNT0";
            KisRealtimeWebSocketClient optionsClient = webSocketClients.get(trId);

            if (optionsClient == null) {
                log.error("옵션 WebSocket 클라이언트를 찾을 수 없습니다: {}", trId);
                return;
            }

            if (nightSession) {
                // 야간장: 8자리 코드 사용 (101S015850 형식)
                List<String> nightOptionCodes = symbolMasterService.getActiveNightOptionCodes();

                log.info("🌃 야간옵션 종목 코드: {} 종목 (TR_ID: {})", nightOptionCodes.size(), trId);

                if (nightOptionCodes.isEmpty()) {
                    log.warn("⚠️ 야간 활성 옵션 종목이 없습니다!");
                    return;
                }

                log.info("🌙 야간 옵션 종목 실시간 구독 시작 ({}) - {} 종목", trId, nightOptionCodes.size());

                for (String symbol : nightOptionCodes) {
                    log.info("✓ 야간옵션 실시간 체결가 구독: {} ({})", symbol, trId);
                    optionsClient.subscribeOptionsPrice(symbol);
                    Thread.sleep(300);
                }

                log.info("✓ 야간옵션 {} 종목 실시간 구독 완료 ({})", nightOptionCodes.size(), trId);

            } else {
                // 주간장: 9자리 코드 사용 (B01601570 형식)
                BigDecimal currentIndex = symbolMasterService.getCurrentKospi200Index();
                List<SymbolMasterService.OptionCodeInfo> optionCodes = symbolMasterService
                        .getActiveOptionCodes(currentIndex);

                log.info("📊 주간옵션 종목 코드: {} 종목 (TR_ID: {})", optionCodes.size(), trId);

                if (optionCodes.isEmpty()) {
                    log.warn("⚠️ 주간 활성 옵션 종목이 없습니다!");
                    return;
                }

                log.info("📈 주간 옵션 종목 실시간 구독 시작 ({}) - KOSPI200={}, {} 종목",
                        trId, currentIndex, optionCodes.size());

                for (SymbolMasterService.OptionCodeInfo optionInfo : optionCodes) {
                    String symbol = optionInfo.code;

                    log.info("✓ 주간옵션 실시간 체결가 구독: {} ({})", symbol, trId);

                    // 체결가 구독
                    optionsClient.subscribeOptionsPrice(symbol);
                    Thread.sleep(300);
                }

                log.info("✓ 주간옵션 {} 종목 실시간 체결가 구독 완료 ({})", optionCodes.size(), trId);
                log.info("💡 H0IOCNT0 데이터에 호가 정보 포함 (fields[6]=매도호가, fields[7]=매수호가)");
            }

        } catch (Exception e) {
            log.error("옵션 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 선물 종목 실시간 구독 (H0IFASP0 호가만)
     */
    public void subscribeFutures(String futuresCode) {
        KisRealtimeWebSocketClient futuresQuoteClient = webSocketClients.get("H0IFASP0");

        if (futuresQuoteClient == null || !futuresQuoteClient.isConnected()) {
            log.warn("[H0IFASP0] WebSocket이 연결되지 않았습니다");
            return;
        }

    }

    /**
     * 특정 옵션 종목 실시간 구독 (H0IOCNT0 체결가만)
     */
    public void subscribeOption(String optionCode) {
        KisRealtimeWebSocketClient optionsPriceClient = webSocketClients.get("H0IOCNT0");

        if (optionsPriceClient == null || !optionsPriceClient.isConnected()) {
            log.warn("[H0IOCNT0] WebSocket이 연결되지 않았습니다");
            return;
        }

        optionsPriceClient.subscribeOptionsPrice(optionCode);
    }

    /**
     * WebSocket 연결 상태 확인
     */
    public boolean isConnected() {
        // 모든 클라이언트가 연결되어 있는지 확인
        return webSocketClients.values().stream()
                .allMatch(KisRealtimeWebSocketClient::isConnected);
    }
}
