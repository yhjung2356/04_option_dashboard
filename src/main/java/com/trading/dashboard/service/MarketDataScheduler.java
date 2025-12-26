package com.trading.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * 시장 데이터 자동 갱신 스케줄러
 * - 야간장 개장 (18:00): 데이터 갱신
 * - 주간장 개장 (09:00): 데이터 갱신
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final KisApiService kisApiService;
    private final MarketDataService marketDataService;

    /**
     * 야간장 개장 시간 (월~금 18:00)
     * cron: 초 분 시 일 월 요일
     * "0 0 18 * * MON-FRI" = 월요일~금요일 18:00:00
     * 야간장은 다음날 05:00까지 운영 (금요일 18:00 → 토요일 05:00)
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
    public void refreshNightMarketData() {
        log.info("========================================");
        log.info("[SCHEDULE] Night Market Opening (18:00) - Auto Refresh");
        log.info("========================================");

        try {
            // 0. 주간장 데이터 삭제 (야간장 데이터로 교체)
            log.info("[SCHEDULE] Clearing day session data...");
            kisApiService.clearAllData();

            // 1. KIS API로 최신 데이터 로드
            kisApiService.loadKospi200Futures();
            kisApiService.loadKospi200Options();
            log.info("[SCHEDULE] Market data refreshed successfully");

            // 2. WebSocket 연결 시작/재시작
            marketDataService.startWebSocketIfNeeded();
            log.info("[SCHEDULE] WebSocket subscriptions updated");

        } catch (Exception e) {
            log.error("[SCHEDULE] Failed to refresh night market data: {}", e.getMessage(), e);
        }
    }

    /**
     * 주간장 개장 시간 (월~금 08:45)
     * "0 45 8 * * MON-FRI" = 월요일~금요일 08:45:00
     */
    @Scheduled(cron = "0 45 8 * * MON-FRI", zone = "Asia/Seoul")
    public void refreshDayMarketData() {
        log.info("========================================");
        log.info("[SCHEDULE] Day Market Opening (08:45) - Auto Refresh");
        log.info("========================================");

        try {
            // 0. 야간장 데이터 삭제 (주간장 데이터로 교체)
            log.info("[SCHEDULE] Clearing night session data...");
            kisApiService.clearAllData();

            // 1. KIS API로 최신 데이터 로드
            kisApiService.loadKospi200Futures();
            kisApiService.loadKospi200Options();
            log.info("[SCHEDULE] Market data refreshed successfully");

            // 2. WebSocket 연결 시작/재시작
            marketDataService.startWebSocketIfNeeded();
            log.info("[SCHEDULE] WebSocket subscriptions updated");

        } catch (Exception e) {
            log.error("[SCHEDULE] Failed to refresh day market data: {}", e.getMessage(), e);
        }
    }

    /**
     * 매 1시간마다 데이터 갱신 (장중)
     * 장중 시간대에만 동작하도록 조건 체크
     * initialDelay: 서버 시작 후 5분 후부터 시작 (초기 로딩과 겹치지 않도록)
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 300000) // 1시간마다, 5분 후 시작
    public void refreshMarketDataHourly() {
        LocalTime now = LocalTime.now();

        // 야간장: 18:00 ~ 익일 05:00 (다음날 05:00 전까지)
        boolean isNightSession = now.isAfter(LocalTime.of(18, 0)) || now.isBefore(LocalTime.of(5, 0));

        // 주간장: 08:45 ~ 15:30 (동시호가 제외)
        boolean isDaySession = now.isAfter(LocalTime.of(8, 45)) && now.isBefore(LocalTime.of(15, 30));

        if (isNightSession || isDaySession) {
            log.info("[SCHEDULE] Hourly data refresh...");
            try {
                kisApiService.loadKospi200Futures();
                kisApiService.loadKospi200Options();
                log.info("[SCHEDULE] Hourly refresh completed");
            } catch (Exception e) {
                log.error("[SCHEDULE] Failed to refresh data hourly: {}", e.getMessage());
            }
        }
    }
}
