package com.trading.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * 거래일 계산 서비스
 * 전거래일 계산 (주말 및 공휴일 제외)
 * 특별 거래시간 처리 (수능날 등)
 */
@Slf4j
@Service
public class TradingCalendarService {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Set<LocalDate> holidays = new HashSet<>();
    private final Set<LocalDate> delayedOpenDays = new HashSet<>(); // 개장 지연일 (수능날 등)

    public TradingCalendarService() {
        loadHolidays();
        loadSpecialTradingDays();
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
     * 특정 날짜가 거래일인지 확인
     */
    public boolean isTradingDay(LocalDate date) {
        return !isNonTradingDay(date);
    }

    /**
     * 오늘이 거래일인지 확인
     */
    public boolean isTradingDay() {
        return isTradingDay(LocalDate.now());
    }

    /**
     * 특정 날짜가 공휴일인지 확인
     */
    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    /**
     * 오늘이 공휴일인지 확인
     */
    public boolean isHoliday() {
        return isHoliday(LocalDate.now());
    }

    /**
     * 특정 날짜가 개장 지연일인지 확인 (수능날 등)
     */
    public boolean isDelayedOpenDay(LocalDate date) {
        return delayedOpenDays.contains(date);
    }

    /**
     * 오늘이 개장 지연일인지 확인
     */
    public boolean isDelayedOpenDay() {
        return isDelayedOpenDay(LocalDate.now());
    }

    /**
     * 개장 지연일의 실제 개장 시간 (수능날은 10시 개장)
     */
    public LocalTime getMarketOpenTime(LocalDate date) {
        if (isDelayedOpenDay(date)) {
            return LocalTime.of(10, 0); // 수능날은 10:00 개장
        }
        return LocalTime.of(9, 0); // 일반적으로 09:00 개장
    }

    /**
     * 오늘의 개장 시간
     */
    public LocalTime getMarketOpenTime() {
        return getMarketOpenTime(LocalDate.now());
    }

    /**
     * 특별 거래일 로드 (개장 지연일 등)
     */
    private void loadSpecialTradingDays() {
        log.info("Loading special trading days (delayed open, etc.)...");

        // 수능날 (매년 11월 중순 목요일)
        // 2025년 수능: 11월 13일 (목)
        delayedOpenDays.add(LocalDate.of(2025, 11, 13));

        // 2026년 수능: 11월 12일 (목) - 예상
        delayedOpenDays.add(LocalDate.of(2026, 11, 12));

        log.info("Loaded {} special trading days (delayed open)", delayedOpenDays.size());
    }

    /**
     * 2025년, 2026년 대한민국 공휴일 로드
     */
    private void loadHolidays() {
        log.info("Loading market holidays for year 2025 and 2026...");

        // 2025년 공휴일
        holidays.add(LocalDate.of(2025, 1, 1)); // 신정
        holidays.add(LocalDate.of(2025, 1, 28)); // 설날 연휴
        holidays.add(LocalDate.of(2025, 1, 29)); // 설날
        holidays.add(LocalDate.of(2025, 1, 30)); // 설날 연휴
        holidays.add(LocalDate.of(2025, 3, 1)); // 삼일절
        holidays.add(LocalDate.of(2025, 3, 3)); // 대체휴일
        holidays.add(LocalDate.of(2025, 5, 5)); // 어린이날
        holidays.add(LocalDate.of(2025, 5, 6)); // 대체휴일
        holidays.add(LocalDate.of(2025, 6, 6)); // 현충일
        holidays.add(LocalDate.of(2025, 8, 15)); // 광복절
        holidays.add(LocalDate.of(2025, 10, 3)); // 개천절
        holidays.add(LocalDate.of(2025, 10, 6)); // 추석 연휴
        holidays.add(LocalDate.of(2025, 10, 7)); // 추석
        holidays.add(LocalDate.of(2025, 10, 8)); // 추석 연휴
        holidays.add(LocalDate.of(2025, 10, 9)); // 한글날
        holidays.add(LocalDate.of(2025, 12, 25)); // 크리스마스
        holidays.add(LocalDate.of(2025, 12, 31)); // 연말휴장

        // 2026년 공휴일
        holidays.add(LocalDate.of(2026, 1, 1)); // 신정
        // 2026년 1월 2일 = 첫 거래일 (휴장 아님)
        holidays.add(LocalDate.of(2026, 2, 16)); // 설날 연휴
        holidays.add(LocalDate.of(2026, 2, 17)); // 설날
        holidays.add(LocalDate.of(2026, 2, 18)); // 설날 연휴
        holidays.add(LocalDate.of(2026, 3, 1)); // 삼일절
        holidays.add(LocalDate.of(2026, 3, 2)); // 대체휴일
        holidays.add(LocalDate.of(2026, 5, 5)); // 어린이날
        holidays.add(LocalDate.of(2026, 6, 6)); // 현충일
        holidays.add(LocalDate.of(2026, 6, 8)); // 대체휴일
        holidays.add(LocalDate.of(2026, 8, 15)); // 광복절
        holidays.add(LocalDate.of(2026, 9, 24)); // 추석 연휴
        holidays.add(LocalDate.of(2026, 9, 25)); // 추석
        holidays.add(LocalDate.of(2026, 9, 26)); // 추석 연휴
        holidays.add(LocalDate.of(2026, 10, 3)); // 개천절
        holidays.add(LocalDate.of(2026, 10, 5)); // 대체휴일
        holidays.add(LocalDate.of(2026, 10, 9)); // 한글날
        holidays.add(LocalDate.of(2026, 12, 25)); // 크리스마스

        log.info("Loaded {} holidays (2025: 17, 2026: 17)", holidays.size());
    }
}
