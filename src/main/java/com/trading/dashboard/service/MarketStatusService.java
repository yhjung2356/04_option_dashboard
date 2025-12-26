package com.trading.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 시장 상태 판단 서비스
 * KOSPI200 선물옵션 거래시간: 평일 08:45 ~ 15:45 (주간, 15:35~15:45 동시호가), 18:00 ~ 익일 05:00
 * (야간)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketStatusService {

    private final TradingCalendarService tradingCalendarService;

    /**
     * 시장 상태 조회
     */
    public MarketStatus getMarketStatus() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();
        LocalDate today = now.toLocalDate();

        // 주말 체크
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return MarketStatus.CLOSED_WEEKEND;
        }

        // 공휴일 체크
        if (isHoliday(today)) {
            return MarketStatus.CLOSED_HOLIDAY;
        }

        // 주간 거래: 08:45 ~ 15:45 (15:35~15:45 동시호가 포함)
        if (time.isAfter(LocalTime.of(8, 45)) && time.isBefore(LocalTime.of(15, 45))) {
            return MarketStatus.OPEN_DAY_SESSION;
        }

        // 야간 거래: 18:00 ~ 익일 05:00
        if (time.isAfter(LocalTime.of(18, 0)) || time.isBefore(LocalTime.of(5, 0))) {
            return MarketStatus.OPEN_NIGHT_SESSION;
        }

        // 장 마감 시간 (15:45 ~ 18:00, 05:00 ~ 08:45)
        return MarketStatus.CLOSED_BETWEEN_SESSIONS;
    }

    /**
     * 공휴일 여부 확인
     */
    private boolean isHoliday(LocalDate date) {
        String tradingDay = tradingCalendarService.getPreviousTradingDay(date.plusDays(1));
        String today = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return !tradingDay.equals(today);
    }

    /**
     * 시장 상태 Enum
     */
    public enum MarketStatus {
        OPEN_DAY_SESSION("주간장", "거래중", true),
        OPEN_NIGHT_SESSION("야간장", "거래중", true),
        CLOSED_BETWEEN_SESSIONS("휴장", "장 마감", false),
        CLOSED_WEEKEND("휴장", "주말", false),
        CLOSED_HOLIDAY("휴장", "공휴일", false);

        private final String displayName;
        private final String description;
        private final boolean isOpen;

        MarketStatus(String displayName, String description, boolean isOpen) {
            this.displayName = displayName;
            this.description = description;
            this.isOpen = isOpen;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public boolean isOpen() {
            return isOpen;
        }

        public String getFullText() {
            if (isOpen) {
                return displayName + " " + description;
            }
            return displayName;
        }
    }
}
