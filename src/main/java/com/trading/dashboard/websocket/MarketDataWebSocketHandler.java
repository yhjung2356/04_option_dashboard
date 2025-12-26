package com.trading.dashboard.websocket;

import com.trading.dashboard.dto.MarketOverviewDTO;
import com.trading.dashboard.dto.OptionChainAnalysisDTO;
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
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataService marketDataService;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Value("${trading.demo-mode:false}")
    private boolean demoMode;

    @Value("${trading.market-hours.enabled:true}")
    private boolean marketHoursEnabled;

    private MarketOverviewDTO lastOverviewBroadcast;
    private OptionChainAnalysisDTO lastOptionChainBroadcast;
    private long lastOverviewBroadcastTime = 0;
    private long lastOptionChainBroadcastTime = 0;

    /**
     * 장 시간인지 체크
     */
    private boolean isMarketOpen() {
        if (demoMode || !marketHoursEnabled) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now(KST);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        boolean isDaySession = time.isAfter(LocalTime.of(8, 45)) &&
                time.isBefore(LocalTime.of(15, 45));
        boolean isNightSession = time.isAfter(LocalTime.of(18, 0)) ||
                time.isBefore(LocalTime.of(5, 0));

        return isDaySession || isNightSession;
    }

    /**
     * 1초마다 시장 개요를 웹소켓으로 전송
     * 장이 닫히면 브로드캐스트 중지 (클라이언트는 마지막 데이터 유지)
     * 변경 감지: 데이터가 변경된 경우에만 브로드캐스트
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastMarketOverview() {
        try {
            // 장이 닫혔으면 브로드캐스트 중지
            if (!isMarketOpen()) {
                log.debug("Market closed. Keeping last trading day data on client.");
                return;
            }

            MarketOverviewDTO overview = marketDataService.getMarketOverview();

            // 변경 감지: 데이터가 변경된 경우에만 브로드캐스트
            if (hasOverviewDataChanged(overview)) {
                messagingTemplate.convertAndSend("/topic/market-overview", overview);
                lastOverviewBroadcast = overview;
                lastOverviewBroadcastTime = System.currentTimeMillis();
                log.debug("Market overview broadcasted (changed)");
            } else {
                // 30초마다 한 번씩은 강제로 브로드캐스트 (연결 유지)
                if (System.currentTimeMillis() - lastOverviewBroadcastTime > 30000) {
                    messagingTemplate.convertAndSend("/topic/market-overview", overview);
                    lastOverviewBroadcastTime = System.currentTimeMillis();
                    log.debug("Market overview broadcasted (keepalive)");
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting market overview", e);
        }
    }

    /**
     * 1초마다 옵션 체인 데이터를 웹소켓으로 전송
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastOptionChain() {
        try {
            // 장이 닫혔으면 브로드캐스트 중지
            if (!isMarketOpen()) {
                return;
            }

            OptionChainAnalysisDTO optionChain = marketDataService.getOptionChainAnalysis();

            // 변경 감지: 데이터가 변경된 경우에만 브로드캐스트
            if (hasOptionChainChanged(optionChain)) {
                messagingTemplate.convertAndSend("/topic/option-chain", optionChain);
                lastOptionChainBroadcast = optionChain;
                lastOptionChainBroadcastTime = System.currentTimeMillis();
                log.debug("Option chain broadcasted (changed)");
            } else {
                // 30초마다 한 번씩은 강제로 브로드캐스트 (연결 유지)
                if (System.currentTimeMillis() - lastOptionChainBroadcastTime > 30000) {
                    messagingTemplate.convertAndSend("/topic/option-chain", optionChain);
                    lastOptionChainBroadcastTime = System.currentTimeMillis();
                    log.debug("Option chain broadcasted (keepalive)");
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting option chain", e);
        }
    }

    /**
     * 시장 개요 데이터 변경 감지
     */
    private boolean hasOverviewDataChanged(MarketOverviewDTO newData) {
        if (lastOverviewBroadcast == null) {
            return true;
        }

        // 주요 필드 비교 (거래량, 거래대금이 변경되었는지)
        return !newData.getTotalFuturesVolume().equals(lastOverviewBroadcast.getTotalFuturesVolume())
                || !newData.getTotalOptionsVolume().equals(lastOverviewBroadcast.getTotalOptionsVolume())
                || !newData.getTotalFuturesTradingValue().equals(lastOverviewBroadcast.getTotalFuturesTradingValue())
                || !newData.getTotalOptionsTradingValue().equals(lastOverviewBroadcast.getTotalOptionsTradingValue());
    }

    /**
     * 옵션 체인 데이터 변경 감지
     */
    private boolean hasOptionChainChanged(OptionChainAnalysisDTO newData) {
        if (lastOptionChainBroadcast == null) {
            return true;
        }

        // 기초자산 가격이나 ATM 변경 확인
        if (!newData.getUnderlyingPrice().equals(lastOptionChainBroadcast.getUnderlyingPrice())) {
            return true;
        }

        // 행사가별 가격이 변경되었는지 확인 (첫 번째 몇 개만 샘플링)
        if (newData.getStrikeChain() != null && lastOptionChainBroadcast.getStrikeChain() != null) {
            int checkCount = Math.min(3, Math.min(newData.getStrikeChain().size(),
                    lastOptionChainBroadcast.getStrikeChain().size()));
            for (int i = 0; i < checkCount; i++) {
                var newStrike = newData.getStrikeChain().get(i);
                var oldStrike = lastOptionChainBroadcast.getStrikeChain().get(i);

                if (!newStrike.getCallPrice().equals(oldStrike.getCallPrice()) ||
                        !newStrike.getPutPrice().equals(oldStrike.getPutPrice())) {
                    return true;
                }
            }
        }

        return false;
    }
}
