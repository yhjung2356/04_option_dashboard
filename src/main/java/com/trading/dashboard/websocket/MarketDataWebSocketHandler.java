package com.trading.dashboard.websocket;

import com.trading.dashboard.dto.MarketOverviewDTO;
import com.trading.dashboard.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataService marketDataService;

    @Value("${trading.demo-mode:false}")
    private boolean demoMode;

    @Value("${trading.market-hours.enabled:true}")
    private boolean marketHoursEnabled;

    private MarketOverviewDTO lastBroadcast;
    private long lastBroadcastTime = 0;

    /**
     * 장 시간인지 체크
     */
    private boolean isMarketOpen() {
        if (demoMode || !marketHoursEnabled) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        boolean isDaySession = time.isAfter(LocalTime.of(9, 0)) &&
                time.isBefore(LocalTime.of(15, 45));
        boolean isNightSession = time.isAfter(LocalTime.of(18, 0)) ||
                time.isBefore(LocalTime.of(5, 0));

        return isDaySession || isNightSession;
    }

    /**
     * 1초마다 시장 데이터를 웹소켓으로 전송
     * 장이 닫히면 브로드캐스트 중지 (클라이언트는 마지막 데이터 유지)
     * 변경 감지: 데이터가 변경된 경우에만 브로드캐스트
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastMarketData() {
        try {
            // 장이 닫혔으면 브로드캐스트 중지
            if (!isMarketOpen()) {
                log.debug("Market closed. Keeping last trading day data on client.");
                return;
            }

            MarketOverviewDTO overview = marketDataService.getMarketOverview();

            // 변경 감지: 데이터가 변경된 경우에만 브로드캐스트
            if (hasDataChanged(overview)) {
                messagingTemplate.convertAndSend("/topic/market-overview", overview);
                lastBroadcast = overview;
                lastBroadcastTime = System.currentTimeMillis();
                log.debug("Market data broadcasted (changed)");
            } else {
                // 30초마다 한 번씩은 강제로 브로드캐스트 (연결 유지)
                if (System.currentTimeMillis() - lastBroadcastTime > 30000) {
                    messagingTemplate.convertAndSend("/topic/market-overview", overview);
                    lastBroadcastTime = System.currentTimeMillis();
                    log.debug("Market data broadcasted (keepalive)");
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting market data", e);
        }
    }

    /**
     * 데이터 변경 감지
     */
    private boolean hasDataChanged(MarketOverviewDTO newData) {
        if (lastBroadcast == null) {
            return true;
        }

        // 주요 필드 비교 (거래량, 거래대금이 변경되었는지)
        return !newData.getTotalFuturesVolume().equals(lastBroadcast.getTotalFuturesVolume())
                || !newData.getTotalOptionsVolume().equals(lastBroadcast.getTotalOptionsVolume())
                || !newData.getTotalFuturesTradingValue().equals(lastBroadcast.getTotalFuturesTradingValue())
                || !newData.getTotalOptionsTradingValue().equals(lastBroadcast.getTotalOptionsTradingValue());
    }
}
