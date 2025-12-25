package com.trading.dashboard.service;

import com.trading.dashboard.dto.*;
import com.trading.dashboard.model.InstrumentType;
import com.trading.dashboard.model.OptionData;
import com.trading.dashboard.model.OptionType;
import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MarketDataService {

    private final OptionDataRepository optionDataRepository;
    private final FuturesDataRepository futuresDataRepository;

    /**
     * 전체 시장 현황 조회
     */
    public MarketOverviewDTO getMarketOverview() {
        // 선물 통계
        Long totalFuturesVolume = futuresDataRepository.sumAllVolume();
        BigDecimal totalFuturesTradingValue = futuresDataRepository.sumAllTradingValue();
        Long totalFuturesOpenInterest = futuresDataRepository.sumAllOpenInterest();

        // 옵션 통계 (콜 + 풋)
        Long callVolume = optionDataRepository.sumVolumeByOptionType(OptionType.CALL);
        Long putVolume = optionDataRepository.sumVolumeByOptionType(OptionType.PUT);
        Long totalOptionsVolume = (callVolume != null ? callVolume : 0L) + (putVolume != null ? putVolume : 0L);

        BigDecimal callTradingValue = optionDataRepository.sumTradingValueByOptionType(OptionType.CALL);
        BigDecimal putTradingValue = optionDataRepository.sumTradingValueByOptionType(OptionType.PUT);
        BigDecimal totalOptionsTradingValue = (callTradingValue != null ? callTradingValue : BigDecimal.ZERO)
                .add(putTradingValue != null ? putTradingValue : BigDecimal.ZERO);

        Long callOpenInterest = optionDataRepository.sumOpenInterestByOptionType(OptionType.CALL);
        Long putOpenInterest = optionDataRepository.sumOpenInterestByOptionType(OptionType.PUT);
        Long totalOptionsOpenInterest = (callOpenInterest != null ? callOpenInterest : 0L) +
                (putOpenInterest != null ? putOpenInterest : 0L);

        // Put/Call Ratio 계산
        PutCallRatioDTO putCallRatio = calculatePutCallRatio();

        // 거래량 상위 종목 TOP 5
        List<TopTradedInstrumentDTO> topByVolume = getTopByVolume(5);

        // 미결제약정 상위 종목 TOP 5
        List<TopTradedInstrumentDTO> topByOpenInterest = getTopByOpenInterest(5);

        return MarketOverviewDTO.builder()
                .totalFuturesVolume(totalFuturesVolume != null ? totalFuturesVolume : 0L)
                .totalFuturesTradingValue(totalFuturesTradingValue != null ? totalFuturesTradingValue : BigDecimal.ZERO)
                .totalFuturesOpenInterest(totalFuturesOpenInterest != null ? totalFuturesOpenInterest : 0L)
                .totalOptionsVolume(totalOptionsVolume)
                .totalOptionsTradingValue(totalOptionsTradingValue)
                .totalOptionsOpenInterest(totalOptionsOpenInterest)
                .putCallRatio(putCallRatio)
                .topByVolume(topByVolume)
                .topByOpenInterest(topByOpenInterest)
                .build();
    }

    /**
     * Put/Call Ratio 계산
     */
    public PutCallRatioDTO calculatePutCallRatio() {
        Long callVolume = optionDataRepository.sumVolumeByOptionType(OptionType.CALL);
        Long putVolume = optionDataRepository.sumVolumeByOptionType(OptionType.PUT);

        Long callOpenInterest = optionDataRepository.sumOpenInterestByOptionType(OptionType.CALL);
        Long putOpenInterest = optionDataRepository.sumOpenInterestByOptionType(OptionType.PUT);

        BigDecimal callTradingValue = optionDataRepository.sumTradingValueByOptionType(OptionType.CALL);
        BigDecimal putTradingValue = optionDataRepository.sumTradingValueByOptionType(OptionType.PUT);

        // Null 체크 및 기본값 설정
        long safeCallVolume = (callVolume != null && callVolume > 0) ? callVolume : 1L;
        long safePutVolume = (putVolume != null) ? putVolume : 0L;
        long safeCallOI = (callOpenInterest != null && callOpenInterest > 0) ? callOpenInterest : 1L;
        long safePutOI = (putOpenInterest != null) ? putOpenInterest : 0L;
        BigDecimal safeCallValue = (callTradingValue != null && callTradingValue.compareTo(BigDecimal.ZERO) > 0)
                ? callTradingValue
                : BigDecimal.ONE;
        BigDecimal safePutValue = (putTradingValue != null) ? putTradingValue : BigDecimal.ZERO;

        // Ratio 계산 (0으로 나누기 방지)
        BigDecimal volumeRatio = BigDecimal.valueOf(safePutVolume)
                .divide(BigDecimal.valueOf(safeCallVolume), 4, RoundingMode.HALF_UP);

        BigDecimal openInterestRatio = BigDecimal.valueOf(safePutOI)
                .divide(BigDecimal.valueOf(safeCallOI), 4, RoundingMode.HALF_UP);

        BigDecimal tradingValueRatio = safePutValue
                .divide(safeCallValue, 4, RoundingMode.HALF_UP);

        return PutCallRatioDTO.builder()
                .callVolume(callVolume != null ? callVolume : 0L)
                .putVolume(putVolume != null ? putVolume : 0L)
                .volumeRatio(volumeRatio)
                .callOpenInterest(callOpenInterest != null ? callOpenInterest : 0L)
                .putOpenInterest(putOpenInterest != null ? putOpenInterest : 0L)
                .openInterestRatio(openInterestRatio)
                .callTradingValue(callTradingValue != null ? callTradingValue : BigDecimal.ZERO)
                .putTradingValue(putTradingValue != null ? putTradingValue : BigDecimal.ZERO)
                .tradingValueRatio(tradingValueRatio)
                .build();
    }

    /**
     * 거래대금 상위 종목 (옵션만)
     */
    public List<TopTradedInstrumentDTO> getTopByTradingValue(int limit) {
        List<TopTradedInstrumentDTO> result = new ArrayList<>();

        // 옵션 데이터만 조회 (선물 제외)
        List<OptionData> topOptions = optionDataRepository.findTopByTradingValueDesc();
        topOptions.stream()
                .limit(limit)
                .forEach(o -> result.add(TopTradedInstrumentDTO.builder()
                        .symbol(o.getSymbol())
                        .name(o.getName() != null && !o.getName().isEmpty() ? o.getName()
                                : o.getSymbol() + " " + o.getStrikePrice() + " " + o.getOptionType())
                        .type(InstrumentType.OPTIONS)
                        .currentPrice(o.getCurrentPrice())
                        .volume(o.getVolume())
                        .tradingValue(o.getTradingValue())
                        .openInterest(o.getOpenInterest())
                        .build()));

        return result;
    }

    /**
     * 거래량 상위 종목 (옵션만)
     */
    public List<TopTradedInstrumentDTO> getTopByVolume(int limit) {
        List<TopTradedInstrumentDTO> result = new ArrayList<>();

        // 옵션 데이터만 조회 (선물 제외)
        List<OptionData> topOptions = optionDataRepository.findTopByVolumeDesc();
        topOptions.stream()
                .limit(limit)
                .forEach(o -> result.add(TopTradedInstrumentDTO.builder()
                        .symbol(o.getSymbol())
                        .name(o.getName() != null && !o.getName().isEmpty() ? o.getName()
                                : o.getSymbol() + " " + o.getStrikePrice() + " " + o.getOptionType())
                        .type(InstrumentType.OPTIONS)
                        .currentPrice(o.getCurrentPrice())
                        .volume(o.getVolume())
                        .tradingValue(o.getTradingValue())
                        .openInterest(o.getOpenInterest())
                        .build()));

        return result;
    }

    /**
     * 미결제약정 상위 종목 (옵션만)
     */
    public List<TopTradedInstrumentDTO> getTopByOpenInterest(int limit) {
        List<TopTradedInstrumentDTO> result = new ArrayList<>();

        // 옵션 데이터만 조회 (선물 제외)
        List<OptionData> topOptions = optionDataRepository.findTopByOpenInterestDesc();
        topOptions.stream()
                .limit(limit)
                .forEach(o -> result.add(TopTradedInstrumentDTO.builder()
                        .symbol(o.getSymbol())
                        .name(o.getName() != null && !o.getName().isEmpty() ? o.getName()
                                : o.getSymbol() + " " + o.getStrikePrice() + " " + o.getOptionType())
                        .type(InstrumentType.OPTIONS)
                        .currentPrice(o.getCurrentPrice())
                        .volume(o.getVolume())
                        .tradingValue(o.getTradingValue())
                        .openInterest(o.getOpenInterest())
                        .build()));

        return result;
    }

    /**
     * 옵션 체인 분석 (행사가별 콜/풋 데이터)
     */
    public OptionChainAnalysisDTO getOptionChainAnalysis() {
        List<OptionData> allOptions = optionDataRepository.findAllOrderByStrikePrice();

        // 행사가별로 그룹핑
        Map<BigDecimal, Map<OptionType, OptionData>> strikeMap = allOptions.stream()
                .collect(Collectors.groupingBy(
                        OptionData::getStrikePrice,
                        Collectors.toMap(
                                OptionData::getOptionType,
                                o -> o,
                                (o1, o2) -> o1.getVolume() > o2.getVolume() ? o1 : o2)));

        // StrikePriceDataDTO 리스트 생성
        List<StrikePriceDataDTO> strikeChain = strikeMap.entrySet().stream()
                .map(entry -> {
                    BigDecimal strike = entry.getKey();
                    Map<OptionType, OptionData> optionMap = entry.getValue();
                    OptionData call = optionMap.get(OptionType.CALL);
                    OptionData put = optionMap.get(OptionType.PUT);

                    Long callVolume = call != null ? call.getVolume() : 0L;
                    Long putVolume = put != null ? put.getVolume() : 0L;
                    Long callOI = call != null ? call.getOpenInterest() : 0L;
                    Long putOI = put != null ? put.getOpenInterest() : 0L;

                    return StrikePriceDataDTO.builder()
                            .strikePrice(strike)
                            .callPrice(call != null ? call.getCurrentPrice() : null)
                            .callVolume(callVolume)
                            .callOpenInterest(callOI)
                            .callImpliedVolatility(call != null ? call.getImpliedVolatility() : null)
                            .callDelta(call != null ? call.getDelta() : null)
                            .callGamma(call != null ? call.getGamma() : null)
                            .callTheta(call != null ? call.getTheta() : null)
                            .callVega(call != null ? call.getVega() : null)
                            .callBidPrice(call != null ? call.getBidPrice() : null)
                            .callAskPrice(call != null ? call.getAskPrice() : null)
                            .putPrice(put != null ? put.getCurrentPrice() : null)
                            .putVolume(putVolume)
                            .putOpenInterest(putOI)
                            .putImpliedVolatility(put != null ? put.getImpliedVolatility() : null)
                            .putDelta(put != null ? put.getDelta() : null)
                            .putGamma(put != null ? put.getGamma() : null)
                            .putTheta(put != null ? put.getTheta() : null)
                            .putVega(put != null ? put.getVega() : null)
                            .putBidPrice(put != null ? put.getBidPrice() : null)
                            .putAskPrice(put != null ? put.getAskPrice() : null)
                            .totalVolume(callVolume + putVolume)
                            .totalOpenInterest(callOI + putOI)
                            .build();
                })
                .sorted(Comparator.comparing(StrikePriceDataDTO::getStrikePrice))
                .collect(Collectors.toList());

        // 거래량 최대 행사가
        StrikePriceDataDTO maxVolumeStrike = strikeChain.stream()
                .max(Comparator.comparing(StrikePriceDataDTO::getTotalVolume))
                .orElse(null);

        // 미결제약정 최대 행사가
        StrikePriceDataDTO maxOIStrike = strikeChain.stream()
                .max(Comparator.comparing(StrikePriceDataDTO::getTotalOpenInterest))
                .orElse(null);

        // Max Pain 계산 (간단한 버전)
        BigDecimal maxPainPrice = calculateMaxPain(strikeChain);

        // 기초자산 가격 추정 (ATM 찾기)
        BigDecimal underlyingPrice = estimateUnderlyingPrice(allOptions);
        BigDecimal atmStrike = findNearestStrike(strikeChain, underlyingPrice);

        return OptionChainAnalysisDTO.builder()
                .strikeChain(strikeChain)
                .maxPainPrice(maxPainPrice)
                .highestVolumeStrike(maxVolumeStrike != null ? maxVolumeStrike.getStrikePrice() : null)
                .highestVolumeAmount(maxVolumeStrike != null ? maxVolumeStrike.getTotalVolume() : null)
                .highestOIStrike(maxOIStrike != null ? maxOIStrike.getStrikePrice() : null)
                .highestOIAmount(maxOIStrike != null ? maxOIStrike.getTotalOpenInterest() : null)
                .atmStrike(atmStrike)
                .underlyingPrice(underlyingPrice)
                .build();
    }

    /**
     * Max Pain 계산 - 옵션 매도자가 최소 손실을 보는 가격
     */
    private BigDecimal calculateMaxPain(List<StrikePriceDataDTO> strikeChain) {
        if (strikeChain.isEmpty())
            return BigDecimal.ZERO;

        BigDecimal minPain = null;
        BigDecimal maxPainPrice = null;

        for (StrikePriceDataDTO currentPrice : strikeChain) {
            BigDecimal totalPain = BigDecimal.ZERO;

            for (StrikePriceDataDTO strike : strikeChain) {
                // 콜 옵션의 intrinsic value
                BigDecimal callValue = currentPrice.getStrikePrice()
                        .subtract(strike.getStrikePrice())
                        .max(BigDecimal.ZERO);
                totalPain = totalPain.add(callValue.multiply(
                        BigDecimal.valueOf(strike.getCallOpenInterest() != null ? strike.getCallOpenInterest() : 0L)));

                // 풋 옵션의 intrinsic value
                BigDecimal putValue = strike.getStrikePrice()
                        .subtract(currentPrice.getStrikePrice())
                        .max(BigDecimal.ZERO);
                totalPain = totalPain.add(putValue.multiply(
                        BigDecimal.valueOf(strike.getPutOpenInterest() != null ? strike.getPutOpenInterest() : 0L)));
            }

            if (minPain == null || totalPain.compareTo(minPain) < 0) {
                minPain = totalPain;
                maxPainPrice = currentPrice.getStrikePrice();
            }
        }

        return maxPainPrice;
    }

    /**
     * 기초자산 가격 추정
     */
    private BigDecimal estimateUnderlyingPrice(List<OptionData> options) {
        // 옵션 데이터에서 저장된 KOSPI200 지수 사용
        return options.stream()
                .filter(o -> o.getUnderlyingPrice() != null)
                .map(OptionData::getUnderlyingPrice)
                .findFirst()
                .orElseGet(() -> {
                    // 폴백: ATM 옵션을 찾아 기초자산 가격 추정
                    // 콜 델타가 0.5에 가장 가까운 옵션의 행사가를 사용
                    return options.stream()
                            .filter(o -> o.getOptionType() == OptionType.CALL && o.getDelta() != null)
                            .min(Comparator.comparing(o -> o.getDelta().subtract(new BigDecimal("0.5")).abs()))
                            .map(OptionData::getStrikePrice)
                            .orElse(BigDecimal.valueOf(300)); // 최종 기본값
                });
    }

    /**
     * 가장 가까운 행사가 찾기
     */
    private BigDecimal findNearestStrike(List<StrikePriceDataDTO> strikeChain, BigDecimal price) {
        return strikeChain.stream()
                .min(Comparator.comparing(s -> s.getStrikePrice().subtract(price).abs()))
                .map(StrikePriceDataDTO::getStrikePrice)
                .orElse(price);
    }
}
