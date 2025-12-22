package com.trading.dashboard.service;

import com.trading.dashboard.config.TradingProperties;
import com.trading.dashboard.model.FuturesData;
import com.trading.dashboard.model.OptionData;
import com.trading.dashboard.model.OptionType;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;

/**
 * 실시간 데이터 시뮬레이션 서비스
 * 실제 환경에서는 외부 API나 웹소켓으로 실시간 데이터를 받아옵니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSimulationService {

    private final OptionDataRepository optionDataRepository;
    private final FuturesDataRepository futuresDataRepository;
    private final TradingProperties tradingProperties;
    private final Random random = new Random();

    /**
     * 장 시간인지 체크
     * KOSPI200 선물옵션 거래시간: 평일 09:00 ~ 15:45 (주간), 18:00 ~ 05:00 (야간)
     */
    private boolean isMarketOpen() {
        // 데모 모드면 항상 열림
        if (tradingProperties.isDemoMode()) {
            return true;
        }

        // 장 시간 체크가 비활성화되어 있으면 항상 열림
        if (!tradingProperties.getMarketHours().isEnabled()) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // 주말은 장 마감
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        // 주간 거래: 09:00 ~ 15:45
        boolean isDaySession = time.isAfter(LocalTime.of(9, 0)) &&
                time.isBefore(LocalTime.of(15, 45));

        // 야간 거래: 18:00 ~ 익일 05:00
        boolean isNightSession = time.isAfter(LocalTime.of(18, 0)) ||
                time.isBefore(LocalTime.of(5, 0));

        return isDaySession || isNightSession;
    }

    /**
     * 1초마다 더미 데이터 생성 (장 시간에만)
     * KIS API 사용 시 비활성화됨
     */
    @Scheduled(fixedRate = 1000)
    @Transactional
    public void generateSimulatedData() {
        // KIS API 사용 시 시뮬레이션 비활성화
        if (!tradingProperties.isDemoMode()) {
            return;
        }

        // 장 시간 체크
        if (!isMarketOpen()) {
            if (tradingProperties.isDemoMode()) {
                log.debug("Demo mode: Market check bypassed");
            } else {
                log.debug("Market is closed. Displaying last trading day data.");
                // 장 마감 시에는 데이터를 삭제하지 않고 그대로 유지
                // 마지막 거래일의 데이터가 계속 표시됨
                return;
            }
        }

        log.info("Generating simulated market data... (Demo Mode: {})", tradingProperties.isDemoMode());
        futuresDataRepository.deleteAll();

        // 선물 데이터 생성
        generateFuturesData();

        // 옵션 데이터 생성
        generateOptionsData();
    }

    private void generateFuturesData() {
        // KOSPI200 선물
        FuturesData kospi200 = FuturesData.builder()
                .symbol("F101")
                .name("KOSPI200 선물")
                .currentPrice(new BigDecimal(300 + random.nextInt(20) - 10))
                .changeAmount(new BigDecimal(random.nextInt(10) - 5))
                .changePercent(new BigDecimal(random.nextDouble() * 4 - 2))
                .volume((long) (100000 + random.nextInt(50000)))
                .openInterest((long) (500000 + random.nextInt(100000)))
                .tradingValue(new BigDecimal(1000000000 + random.nextInt(500000000)))
                .bidPrice(new BigDecimal(299.5 + random.nextDouble() * 2))
                .askPrice(new BigDecimal(300.5 + random.nextDouble() * 2))
                .bidVolume(random.nextInt(1000) + 100)
                .askVolume(random.nextInt(1000) + 100)
                .openPrice(new BigDecimal(298 + random.nextInt(5)))
                .highPrice(new BigDecimal(305 + random.nextInt(5)))
                .lowPrice(new BigDecimal(295 + random.nextInt(5)))
                .timestamp(LocalDateTime.now())
                .build();

        futuresDataRepository.save(kospi200);

        // 미니 KOSPI200 선물
        FuturesData miniKospi = FuturesData.builder()
                .symbol("F201")
                .name("미니 KOSPI200 선물")
                .currentPrice(new BigDecimal(300 + random.nextInt(20) - 10))
                .changeAmount(new BigDecimal(random.nextInt(10) - 5))
                .changePercent(new BigDecimal(random.nextDouble() * 4 - 2))
                .volume((long) (50000 + random.nextInt(25000)))
                .openInterest((long) (250000 + random.nextInt(50000)))
                .tradingValue(new BigDecimal(500000000 + random.nextInt(250000000)))
                .bidPrice(new BigDecimal(299.5 + random.nextDouble() * 2))
                .askPrice(new BigDecimal(300.5 + random.nextDouble() * 2))
                .bidVolume(random.nextInt(500) + 50)
                .askVolume(random.nextInt(500) + 50)
                .openPrice(new BigDecimal(298 + random.nextInt(5)))
                .highPrice(new BigDecimal(305 + random.nextInt(5)))
                .lowPrice(new BigDecimal(295 + random.nextInt(5)))
                .timestamp(LocalDateTime.now())
                .build();

        futuresDataRepository.save(miniKospi);
    }

    private void generateOptionsData() {
        LocalDateTime now = LocalDateTime.now();
        String expiryDate = "2024-12-14";

        // 행사가 범위: 280 ~ 320, 2.5 단위
        for (int strike = 280; strike <= 320; strike += 5) {
            BigDecimal strikePrice = new BigDecimal(strike);

            // 콜 옵션
            OptionData call = createOptionData(
                    "C" + strike,
                    OptionType.CALL,
                    strikePrice,
                    expiryDate,
                    now);
            optionDataRepository.save(call);

            // 풋 옵션
            OptionData put = createOptionData(
                    "P" + strike,
                    OptionType.PUT,
                    strikePrice,
                    expiryDate,
                    now);
            optionDataRepository.save(put);
        }
    }

    private OptionData createOptionData(String symbol, OptionType optionType,
            BigDecimal strikePrice, String expiryDate,
            LocalDateTime timestamp) {
        // 기초자산 가격 (300 근처)
        double underlyingPrice = 300.0;
        double strike = strikePrice.doubleValue();

        // ATM에 가까울수록 거래량이 많도록 설정
        double atmDistance = Math.abs(underlyingPrice - strike);
        long baseVolume = (long) (10000 * Math.exp(-atmDistance / 20));
        long volume = baseVolume + random.nextInt(5000);

        long openInterest = (long) (volume * (1.5 + random.nextDouble()));

        // 옵션 가격 계산 (간단한 내재가치 기반)
        double intrinsicValue;
        if (optionType == OptionType.CALL) {
            intrinsicValue = Math.max(0, underlyingPrice - strike);
        } else {
            intrinsicValue = Math.max(0, strike - underlyingPrice);
        }

        double timeValue = Math.max(1, 10 - atmDistance / 2);
        double optionPrice = intrinsicValue + timeValue + random.nextDouble() * 2;

        // 델타 계산 (간단한 근사)
        double delta;
        if (optionType == OptionType.CALL) {
            delta = underlyingPrice > strike ? 0.5 + (underlyingPrice - strike) / 40
                    : 0.5 - (strike - underlyingPrice) / 40;
            delta = Math.max(0.01, Math.min(0.99, delta));
        } else {
            delta = underlyingPrice < strike ? -0.5 - (strike - underlyingPrice) / 40
                    : -0.5 + (underlyingPrice - strike) / 40;
            delta = Math.max(-0.99, Math.min(-0.01, delta));
        }

        // 내재변동성 (ATM에서 높고 OTM으로 갈수록 변동)
        double iv = 15 + Math.abs(atmDistance) * 0.1 + random.nextDouble() * 3;

        return OptionData.builder()
                .symbol(symbol)
                .optionType(optionType)
                .strikePrice(strikePrice)
                .currentPrice(BigDecimal.valueOf(optionPrice).setScale(2, RoundingMode.HALF_UP))
                .volume(volume)
                .openInterest(openInterest)
                .tradingValue(BigDecimal.valueOf(optionPrice * volume).setScale(0, RoundingMode.HALF_UP))
                .impliedVolatility(BigDecimal.valueOf(iv).setScale(2, RoundingMode.HALF_UP))
                .delta(BigDecimal.valueOf(delta).setScale(4, RoundingMode.HALF_UP))
                .gamma(BigDecimal.valueOf(0.01 + random.nextDouble() * 0.02).setScale(4, RoundingMode.HALF_UP))
                .theta(BigDecimal.valueOf(-0.5 - random.nextDouble()).setScale(4, RoundingMode.HALF_UP))
                .vega(BigDecimal.valueOf(0.1 + random.nextDouble() * 0.2).setScale(4, RoundingMode.HALF_UP))
                .bidPrice(BigDecimal.valueOf(optionPrice - 0.5).setScale(2, RoundingMode.HALF_UP))
                .askPrice(BigDecimal.valueOf(optionPrice + 0.5).setScale(2, RoundingMode.HALF_UP))
                .bidVolume(random.nextInt(100) + 10)
                .askVolume(random.nextInt(100) + 10)
                .timestamp(timestamp)
                .expiryDate(expiryDate)
                .build();
    }
}
