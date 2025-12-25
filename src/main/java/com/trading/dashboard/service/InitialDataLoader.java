package com.trading.dashboard.service;

import com.trading.dashboard.model.*;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 애플리케이션 시작 시 초기 데이터 로드
 * - 거래일: KIS API (실시간 데이터)
 * - 휴장일: KRX API (전거래일 데이터)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialDataLoader implements CommandLineRunner {

    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final KisApiService kisApiService;
    private final KrxDataService krxDataService;
    private final SymbolMasterService symbolMasterService;
    private final TradingCalendarService tradingCalendarService;
    private final Random random = new Random();

    @Override
    public void run(String... args) throws Exception {
        // 시작 시 전거래일 데이터를 로드
        loadInitialData();
    }

    private void loadInitialData() {
        log.info("========================================");
        log.info("Loading initial market data...");
        log.info("========================================");

        // 기존 데이터가 있으면 skip (재시작 시에만 로드)
        if (futuresDataRepository.count() > 0 || optionDataRepository.count() > 0) {
            log.info("Data already exists. Skipping initial load.");
            return;
        }

        // 📅 휴장일 체크: 휴장일이면 KIS API로 전거래일 데이터 조회
        if (!tradingCalendarService.isTradingDay()) {
            log.info("=".repeat(60));
            log.info("📅 오늘은 휴장일입니다.");
            log.info("📊 전거래일({}) 데이터를 KIS API에서 조회합니다...",
                    tradingCalendarService.getPreviousTradingDay());
            log.info("=".repeat(60));

            loadPreviousTradingDayData();
            return;
        }

        // 거래일: KIS API 실시간 데이터 로드
        log.info("📈 거래일입니다. 실시간 데이터를 로드합니다...");

        // 1단계: 한국투자증권 KIS API 시도 (실시간 데이터)
        try {
            log.info("Attempting to load KIS API data (Korea Investment & Securities)...");
            kisApiService.loadKospi200Futures();
            kisApiService.loadKospi200Options();

            // 데이터가 성공적으로 로드되었는지 확인
            if (futuresDataRepository.count() > 0) {
                log.info("✓ KIS API data loaded successfully!");
                log.info("Total: {} futures, {} options",
                        futuresDataRepository.count(), optionDataRepository.count());

                log.info("========================================");
                log.info("Initial data from REST API - Current market status");
                log.info("WebSocket will update real-time changes");
                log.info("========================================");

                return;
            }
        } catch (Exception e) {
            log.warn("Could not load KIS API data: {}", e.getMessage());
        }

        // 2단계: KIS API 실패 시 전거래일 데이터로 fallback
        log.warn("KIS API failed. Loading previous trading day data as fallback...");
        loadPreviousTradingDayData();
    }

    /**
     * 전거래일 데이터 로드 (KIS API 기간별시세 사용)
     */
    private void loadPreviousTradingDayData() {
        try {
            log.info("Attempting to load KIS previous trading day data...");

            // KIS API의 기간별시세 API를 사용하여 전거래일 데이터 조회
            String previousDate = tradingCalendarService.getPreviousTradingDay();
            kisApiService.loadHistoricalData(previousDate);

            // 데이터가 성공적으로 로드되었는지 확인
            if (futuresDataRepository.count() > 0 || optionDataRepository.count() > 0) {
                log.info("✓ KIS previous trading day data loaded successfully!");
                log.info("Total: {} futures, {} options",
                        futuresDataRepository.count(),
                        optionDataRepository.count());

                // 전일 데이터이므로 거래량/거래대금 초기화 불필요
                // (전일 마감 기준 데이터 유지)

                return;
            }
        } catch (Exception e) {
            log.warn("Could not load KRX data: {}", e.getMessage());
        }

        // 3단계: 모두 실패 시 샘플 데이터 생성
        log.info("Loading sample data as fallback...");
        loadSampleData();
    }

    /**
     * 샘플 데이터 강제 로드 (API 호출용)
     */
    public void forceLoadSampleData() {
        log.info("Force loading sample data...");

        // 기존 데이터 삭제
        futuresDataRepository.deleteAll();
        optionDataRepository.deleteAll();

        // 샘플 데이터 로드
        loadSampleData();

        log.info("✓ Sample data force loaded: {} futures, {} options",
                futuresDataRepository.count(),
                optionDataRepository.count());
    }

    /**
     * 샘플 데이터 생성 (KRX 데이터를 가져올 수 없을 때)
     */
    private void loadSampleData() {
        LocalDateTime timestamp = LocalDateTime.now();
        List<FuturesData> futuresList = new ArrayList<>();

        // SymbolMasterService로 동적 종목코드 생성
        List<String> futureCodes = symbolMasterService.getActiveFuturesCodes();
        String[] months = { "3월물", "6월물", "9월물", "12월물" };
        double basePrice = 582.0;

        for (int i = 0; i < Math.min(futureCodes.size(), 4); i++) {
            double price = basePrice + (i * 0.5) + (random.nextDouble() - 0.5);
            double change = (random.nextDouble() * 2 - 1);
            double changePercent = (random.nextDouble() * 2 - 1);

            FuturesData futures = FuturesData.builder()
                    .symbol(futureCodes.get(i))
                    .name("KOSPI200 선물 " + months[i])
                    .currentPrice(java.math.BigDecimal.valueOf(price))
                    .changeAmount(java.math.BigDecimal.valueOf(change))
                    .changePercent(java.math.BigDecimal.valueOf(changePercent))
                    .volume((long) (50000 + random.nextInt(100000)))
                    .tradingValue(java.math.BigDecimal.valueOf(price * 1000000 * (10 + random.nextInt(20))))
                    .openInterest((long) (100000 + random.nextInt(50000)))
                    .highPrice(java.math.BigDecimal.valueOf(price + random.nextDouble() * 2))
                    .lowPrice(java.math.BigDecimal.valueOf(price - random.nextDouble() * 2))
                    .openPrice(java.math.BigDecimal.valueOf(price + (random.nextDouble() - 0.5)))
                    .bidPrice(java.math.BigDecimal.valueOf(price - 0.05))
                    .askPrice(java.math.BigDecimal.valueOf(price + 0.05))
                    .bidVolume(random.nextInt(1000) + 100)
                    .askVolume(random.nextInt(1000) + 100)
                    .timestamp(timestamp)
                    .build();
            futuresList.add(futures);
        }

        futuresDataRepository.saveAll(futuresList);
        log.info("✓ Loaded {} futures contracts", futuresList.size());

        // 옵션 데이터 생성 (SymbolMasterService로 동적 생성)
        List<OptionData> optionsList = new ArrayList<>();
        double underlyingPrice = 567.0;

        // 현재 KOSPI200 지수 기준으로 동적 옵션 종목 생성
        java.math.BigDecimal currentIndex = symbolMasterService.getCurrentKospi200Index();
        List<SymbolMasterService.OptionCodeInfo> optionCodes = symbolMasterService.getActiveOptionCodes(currentIndex);

        for (SymbolMasterService.OptionCodeInfo optionInfo : optionCodes) {
            OptionData option = createOptionWithSymbol(
                    optionInfo.code,
                    optionInfo.strikePrice.doubleValue(),
                    optionInfo.type,
                    underlyingPrice,
                    timestamp);
            optionsList.add(option);
        }

        optionDataRepository.saveAll(optionsList);
        log.info("✓ Loaded {} option contracts", optionsList.size());

        log.info("========================================");
        log.info("Initial data load completed!");
        log.info("Total: {} futures, {} options", futuresList.size(), optionsList.size());
        log.info("You can now view the dashboard at http://localhost:8080");
        log.info("========================================");
    }

    private OptionData createOptionWithSymbol(String symbol, double strikePrice, OptionType optionType,
            double underlyingPrice, LocalDateTime timestamp) {
        // ATM 기준 내재가치 계산
        double intrinsicValue = 0;
        if (optionType == OptionType.CALL) {
            intrinsicValue = Math.max(0, underlyingPrice - strikePrice);
        } else {
            intrinsicValue = Math.max(0, strikePrice - underlyingPrice);
        }

        // 시간가치 (ATM 근처가 높음)
        double distanceFromATM = Math.abs(strikePrice - underlyingPrice);
        double timeValue = Math.max(0.5, 5.0 - (distanceFromATM * 0.3));

        double price = intrinsicValue + timeValue + (random.nextDouble() * 0.5);

        // 거래량 (ATM 근처가 많음)
        long baseVolume = (long) (10000 / (1 + distanceFromATM * 0.1));
        long volume = baseVolume + random.nextInt(5000);

        // 미결제약정
        long openInterest = (long) (baseVolume * (1.5 + random.nextDouble()));

        // IV (변동성)
        double iv = 15.0 + random.nextDouble() * 10;

        // Greeks 계산 (단순화된 버전)
        double delta = calculateDelta(optionType, strikePrice, underlyingPrice);
        double gamma = calculateGamma(strikePrice, underlyingPrice);
        double theta = -0.05 - random.nextDouble() * 0.05;
        double vega = 0.1 + random.nextDouble() * 0.1;

        String expiryDate = "2026-01-09"; // 2026년 1월물

        return OptionData.builder()
                .symbol(symbol)
                .optionType(optionType)
                .strikePrice(java.math.BigDecimal.valueOf(strikePrice))
                .currentPrice(java.math.BigDecimal.valueOf(price))
                .volume(volume)
                .tradingValue(java.math.BigDecimal.valueOf(price * volume * 100000))
                .openInterest(openInterest)
                .bidPrice(java.math.BigDecimal.valueOf(price - 0.05))
                .askPrice(java.math.BigDecimal.valueOf(price + 0.05))
                .bidVolume(random.nextInt(500) + 50)
                .askVolume(random.nextInt(500) + 50)
                .impliedVolatility(java.math.BigDecimal.valueOf(iv))
                .delta(java.math.BigDecimal.valueOf(delta))
                .gamma(java.math.BigDecimal.valueOf(gamma))
                .theta(java.math.BigDecimal.valueOf(theta))
                .vega(java.math.BigDecimal.valueOf(vega))
                .timestamp(timestamp)
                .expiryDate(expiryDate)
                .build();
    }

    private double calculateDelta(OptionType type, double strike, double underlying) {
        if (type == OptionType.CALL) {
            if (underlying > strike + 10)
                return 0.9 + random.nextDouble() * 0.1;
            if (underlying < strike - 10)
                return 0.0 + random.nextDouble() * 0.1;
            return 0.4 + ((underlying - strike + 10) / 20) * 0.5;
        } else {
            if (underlying < strike - 10)
                return -0.9 - random.nextDouble() * 0.1;
            if (underlying > strike + 10)
                return -0.0 - random.nextDouble() * 0.1;
            return -0.4 - ((strike - underlying + 10) / 20) * 0.5;
        }
    }

    private double calculateGamma(double strike, double underlying) {
        double distance = Math.abs(strike - underlying);
        if (distance < 5) {
            return 0.08 + random.nextDouble() * 0.04;
        } else if (distance < 10) {
            return 0.04 + random.nextDouble() * 0.02;
        } else {
            return 0.01 + random.nextDouble() * 0.01;
        }
    }

    /**
     * 야간 장 시작 시 거래량/거래대금 초기화
     * (종목 정보와 전일 종가는 유지)
     */
    /**
     * 야간장 시작 시 거래량/거래대금 초기화
     * 주의: KIS API가 이미 야간장 순수 거래량/거래대금을 전송하므로 DB만 0으로 초기화
     */
    /**
     * 현재 야간장 시간대인지 확인 (15:50 ~ 익일 09:00)
     */
    private boolean isNightSession() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();

        // 15:50 ~ 23:59 또는 00:00 ~ 08:59
        return (hour == 15 && minute >= 50) || (hour >= 16) || (hour < 9);
    }

    private void resetVolumeAndTradingValue() {
        log.info("========================================");
        log.info("Resetting volume and trading value for night session...");
        log.info("========================================");

        // 선물 거래량/거래대금 초기화 (야간장 시작 시 DB를 0으로 리셋)
        List<FuturesData> futuresList = futuresDataRepository.findAll();
        for (FuturesData futures : futuresList) {
            futures.setVolume(0L);
            futures.setTradingValue(java.math.BigDecimal.ZERO);
        }
        futuresDataRepository.saveAll(futuresList);
        log.info("✓ Reset {} futures volume/trading value", futuresList.size());

        // 옵션 거래량/거래대금 초기화
        List<OptionData> optionsList = optionDataRepository.findAll();
        for (OptionData option : optionsList) {
            option.setVolume(0L);
            option.setTradingValue(java.math.BigDecimal.ZERO);
        }
        optionDataRepository.saveAll(optionsList);
        log.info("✓ Reset {} options volume/trading value", optionsList.size());

        log.info("========================================");
        log.info("Night session data initialized!");
        log.info("KIS API will send night session pure volume/trading value");
        log.info("========================================");
    }
}
