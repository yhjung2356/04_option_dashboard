package com.trading.dashboard.service;

import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * KIS 실시간 WebSocket 관리 서비스
 * 애플리케이션 시작 시 자동으로 WebSocket 연결 및 구독
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisRealtimeService {

    private final KisRealtimeWebSocketClient realtimeWebSocketClient;
    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;

    /**
     * 애플리케이션 시작 후 WebSocket 자동 연결
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeRealtimeConnection() {
        try {
            log.info("=".repeat(60));
            log.info("KIS 실시간 WebSocket 연결 초기화 시작...");
            log.info("=".repeat(60));

            // WebSocket 연결
            boolean connected = realtimeWebSocketClient.initialize();
            if (!connected) {
                log.error("❌ KIS 실시간 WebSocket 연결 실패");
                return;
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
     */
    private void subscribeAllFutures() {
        try {
            log.info("📊 전체 선물 종목 실시간 구독 시작...");

            // DB에서 실제 종목 코드 가져오기
            var futures = futuresDataRepository.findAll();

            if (futures.isEmpty()) {
                log.warn("⚠️ DB에 선물 종목이 없습니다!");
                return;
            }

            for (var future : futures) {
                String symbol = future.getSymbol(); // A01603 같은 심볼

                log.info("✓ 선물 실시간 체결가 구독: {}", symbol);

                // 체결가 구독
                realtimeWebSocketClient.subscribeFuturesPrice(symbol);
                Thread.sleep(200);

                // 호가 구독
                realtimeWebSocketClient.subscribeFuturesQuote(symbol);
                Thread.sleep(200);
            }

            log.info("✓ 선물 {} 종목 실시간 구독 완료", futures.size());

        } catch (Exception e) {
            log.error("선물 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 모든 옵션 종목 실시간 구독
     */
    private void subscribeAllOptions() {
        try {
            log.info("📈 전체 옵션 종목 실시간 구독 시작...");

            // DB에서 실제 종목 코드 가져오기
            var options = optionDataRepository.findAll();

            if (options.isEmpty()) {
                log.warn("⚠️ DB에 옵션 종목이 없습니다!");
                return;
            }

            for (var option : options) {
                String symbol = option.getSymbol();

                log.info("✓ 옵션 실시간 체결가 구독: {}", symbol);

                // 체결가 구독
                realtimeWebSocketClient.subscribeOptionsPrice(symbol);
                Thread.sleep(200);

                // 호가 구독
                realtimeWebSocketClient.subscribeOptionsQuote(symbol);
                Thread.sleep(200);
            }

            log.info("✓ 옵션 {} 종목 실시간 구독 완료", options.size());

        } catch (Exception e) {
            log.error("옵션 구독 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 선물 종목 실시간 구독
     */
    public void subscribeFutures(String futuresCode) {
        if (!realtimeWebSocketClient.isConnected()) {
            log.warn("WebSocket이 연결되지 않았습니다");
            return;
        }

        realtimeWebSocketClient.subscribeFuturesPrice(futuresCode);
        realtimeWebSocketClient.subscribeFuturesQuote(futuresCode);
    }

    /**
     * 특정 옵션 종목 실시간 구독
     */
    public void subscribeOption(String optionCode) {
        if (!realtimeWebSocketClient.isConnected()) {
            log.warn("WebSocket이 연결되지 않았습니다");
            return;
        }

        realtimeWebSocketClient.subscribeOptionsPrice(optionCode);
        realtimeWebSocketClient.subscribeOptionsQuote(optionCode);
    }

    /**
     * WebSocket 연결 상태 확인
     */
    public boolean isConnected() {
        return realtimeWebSocketClient.isConnected();
    }
}
