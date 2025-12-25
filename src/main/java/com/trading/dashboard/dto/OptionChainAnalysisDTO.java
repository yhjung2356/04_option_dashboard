package com.trading.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 옵션 체인 분석 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionChainAnalysisDTO {

    // 행사가별 데이터
    private List<StrikePriceDataDTO> strikeChain;

    // Max Pain 분석 (옵션 만기시 매도자가 최소 손실을 보는 가격)
    private BigDecimal maxPainPrice;
    private BigDecimal maxPainLoss;

    // 거래량이 가장 많은 행사가
    private BigDecimal highestVolumeStrike;
    private Long highestVolumeAmount;

    // 미결제약정이 가장 많은 행사가
    private BigDecimal highestOIStrike;
    private Long highestOIAmount;

    // ATM (At The Money) 정보
    private BigDecimal atmStrike;
    private BigDecimal underlyingPrice;

    // ATM Greeks 요약
    private AtmGreeksDTO atmGreeks;

    /**
     * ATM Greeks 요약 데이터
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AtmGreeksDTO {
        private BigDecimal callDelta;
        private BigDecimal putDelta;
        private BigDecimal gamma; // 콜 기준
        private BigDecimal theta; // 콜 기준
        private BigDecimal vega; // 콜 기준
        private BigDecimal impliedVolatility; // 콜 기준
    }
}
