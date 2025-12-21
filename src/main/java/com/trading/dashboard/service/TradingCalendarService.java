package com.trading.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * 거래일 계산 서비스
 * 전거래일 계산 (주말 및 공휴일 제외)
 */
@Slf4j
@Service
public class TradingCalendarService {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Set<LocalDate> holidays = new HashSet<>();

    public TradingCalendarService() {
        loadHolidays();
    }

    /**
     * 전거래일 계산
     */
    public String getPreviousTradingDay() {
        return getPreviousTradingDay(LocalDate.now());
    }

    /**
     * 특정 날짜 기준 전거래일 계산
     */
    public String getPreviousTradingDay(LocalDate baseDate) {
        LocalDate previousDay = baseDate.minusDays(1);

        // 주말과 공휴일을 건너뛰기
        while (isNonTradingDay(previousDay)) {
            previousDay = previousDay.minusDays(1);
        }

        String result = previousDay.format(formatter);
        log.info("Previous trading day calculated: {} (base: {})", result, baseDate);
        return result;
    }

    /**
     * 거래일이 아닌지 확인 (주말 또는 공휴일)
     */
    private boolean isNonTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY 
            || dayOfWeek == DayOfWeek.SUNDAY 
            || holidays.contains(date);
    }

    /**
     * 2025년 대한민국 공휴일 로드
     */
    private void loadHolidays() {
        log.info("Loading market holidays for year 2025...");
        
        // 2025년 공휴일
        holidays.add(LocalDate.of(2025, 1, 1));   // 신정
        holidays.add(LocalDate.of(2025, 1, 28));  // 설날 연휴
        holidays.add(LocalDate.of(2025, 1, 29));  // 설날
        holidays.add(LocalDate.of(2025, 1, 30));  // 설날 연휴
        holidays.add(LocalDate.of(2025, 3, 1));   // 삼일절
        holidays.add(LocalDate.of(2025, 3, 3));   // 대체휴일
        holidays.add(LocalDate.of(2025, 5, 5));   // 어린이날
        holidays.add(LocalDate.of(2025, 5, 6));   // 대체휴일
        holidays.add(LocalDate.of(2025, 6, 6));   // 현충일
        holidays.add(LocalDate.of(2025, 8, 15));  // 광복절
        holidays.add(LocalDate.of(2025, 10, 3));  // 개천절
        holidays.add(LocalDate.of(2025, 10, 6));  // 추석 연휴
        holidays.add(LocalDate.of(2025, 10, 7));  // 추석
        holidays.add(LocalDate.of(2025, 10, 8));  // 추석 연휴
        holidays.add(LocalDate.of(2025, 10, 9));  // 한글날
        holidays.add(LocalDate.of(2025, 12, 25)); // 크리스마스

        log.info("Loaded {} holidays for 2025", holidays.size());
    }
}
