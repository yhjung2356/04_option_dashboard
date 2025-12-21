package com.trading.dashboard.service;

import com.trading.dashboard.model.*;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 애플리케이션 시작 시 전거래일 데이터를 자동으로 로드
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialDataLoader implements CommandLineRunner {

    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final KisApiService kisApiService;
    private final KrxDataService krxDataService;
    private final Random random = new Random();

    @Override
    public void run(String... args) throws Exception {
        // 시작 시 전거래일 데이터를 로드
        loadInitialData();
    }

    private void loadInitialData() {
        log.info("========================================");
        log.info("Loading initial market data (Previous Trading Day)...");
        log.info("========================================");

        // 기존 데이터가 있으면 skip (재시작 시에만 로드)
        if (futuresDataRepository.count() > 0 || optionDataRepository.count() > 0) {
            log.info("Data already exists. Skipping initial load.");
            return;
        }

        // 1단계: 한국투자증권 KIS API 시도 (실시간 데이터)
        try {
            log.info("Attempting to load KIS API data (Korea Investment & Securities)...");
            kisApiService.loadKospi200Futures();
            kisApiService.loadKospi200Options();
            
            // 데이터가 성공적으로 로드되었는지 확인
            if (futuresDataRepository.count() > 0 || optionDataRepository.count() > 0) {
                log.info("✓ KIS API data loaded successfully!");
                log.info("Total: {} futures, {} options", 
                    futuresDataRepository.count(), 
                    optionDataRepository.count());
                return;
            }
        } catch (Exception e) {
            log.warn("Could not load KIS API data: {}", e.getMessage());
        }
        
        // 2단계: 실패 시 KRX 데이터 시도
        try {
            log.info("Attempting to load real KRX data...");
            krxDataService.loadPreviousTradingDayData();
            
            // 데이터가 성공적으로 로드되었는지 확인
            if (futuresDataRepository.count() > 0 || optionDataRepository.count() > 0) {
                log.info("✓ Real KRX data loaded successfully!");
                log.info("Total: {} futures, {} options", 
                    futuresDataRepository.count(), 
                    optionDataRepository.count());
                return;
            }
        } catch (Exception e) {
            log.warn("Could not load real KRX data: {}", e.getMessage());
        }
        
        // 3단계: 모두 실패 시 샘플 데이터 생성
        log.info("Loading sample data as fallback...");
        loadSampleData();
    }
    
    /**
     * 샘플 데이터 생성 (KRX 데이터를 가져올 수 없을 때)
     */
    private void loadSampleData() {
        LocalDateTime timestamp = LocalDateTime.now();
        List<FuturesData> futuresList = new ArrayList<>();
        String[] months = {"01", "02", "03", "06", "09", "12"};
        double basePrice = 400.0;
        
        for (int i = 0; i < 6; i++) {
            double price = basePrice + (i * 0.5) + (random.nextDouble() - 0.5);
            double change = (random.nextDouble() * 2 - 1);
            double changePercent = (random.nextDouble() * 2 - 1);
            
            FuturesData futures = FuturesData.builder()
                    .symbol("F2025" + months[i])
                    .name("KOSPI200 선물 25" + months[i])
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

        // 옵션 데이터 생성 (KOSPI200 옵션)
        List<OptionData> optionsList = new ArrayList<>();
        double underlyingPrice = 400.0;
        
        // 행사가: 380 ~ 420 (5pt 간격)
        for (double strike = 380.0; strike <= 420.0; strike += 5.0) {
            // CALL 옵션
            OptionData call = createOption(
                    strike, 
                    OptionType.CALL, 
                    underlyingPrice, 
                    timestamp,
                    "12"  // 당월물
            );
            optionsList.add(call);
            
            // PUT 옵션
            OptionData put = createOption(
                    strike, 
                    OptionType.PUT, 
                    underlyingPrice, 
                    timestamp,
                    "12"
            );
            optionsList.add(put);
        }
        
        optionDataRepository.saveAll(optionsList);
        log.info("✓ Loaded {} option contracts", optionsList.size());
        
        log.info("========================================");
        log.info("Initial data load completed!");
        log.info("Total: {} futures, {} options", futuresList.size(), optionsList.size());
        log.info("You can now view the dashboard at http://localhost:8080");
        log.info("========================================");
    }

    private OptionData createOption(double strikePrice, OptionType optionType, 
                                    double underlyingPrice, LocalDateTime timestamp,
                                    String month) {
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
        
        String symbol = String.format("O2025%s%s%03d", 
                month,
                optionType == OptionType.CALL ? "C" : "P", 
                (int) strikePrice);
        
        String expiryDate = "2025-12-" + month;
        
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
            if (underlying > strike + 10) return 0.9 + random.nextDouble() * 0.1;
            if (underlying < strike - 10) return 0.0 + random.nextDouble() * 0.1;
            return 0.4 + ((underlying - strike + 10) / 20) * 0.5;
        } else {
            if (underlying < strike - 10) return -0.9 - random.nextDouble() * 0.1;
            if (underlying > strike + 10) return -0.0 - random.nextDouble() * 0.1;
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
}
