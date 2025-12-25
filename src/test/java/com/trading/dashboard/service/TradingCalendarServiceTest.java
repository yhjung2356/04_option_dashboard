package com.trading.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TradingCalendarService 단위 테스트
 */
class TradingCalendarServiceTest {

    private TradingCalendarService calendarService;

    @BeforeEach
    void setUp() {
        calendarService = new TradingCalendarService();
    }

    @Test
    void testGetPreviousTradingDay_FromMonday() {
        // Given: 월요일 (2025-01-06)
        LocalDate monday = LocalDate.of(2025, 1, 6);

        // When: 전거래일 조회
        String previous = calendarService.getPreviousTradingDay(monday);

        // Then: 금요일 (20250103)
        assertEquals("20250103", previous);
    }

    @Test
    void testGetPreviousTradingDay_SkipsWeekend() {
        // Given: 화요일 (2025-01-07)
        LocalDate tuesday = LocalDate.of(2025, 1, 7);

        // When: 전거래일 조회
        String previous = calendarService.getPreviousTradingDay(tuesday);

        // Then: 월요일 (20250106)
        assertEquals("20250106", previous);
    }

    @Test
    void testGetPreviousTradingDay_SkipsHoliday() {
        // Given: 1월 2일 (2025-01-02) - 다음날이 신정 연휴
        LocalDate afterNewYear = LocalDate.of(2025, 1, 2);

        // When: 전거래일 조회
        String previous = calendarService.getPreviousTradingDay(afterNewYear);

        // Then: 2024-12-31 (20241231)
        assertEquals("20241231", previous);
    }
}
