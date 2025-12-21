package com.trading.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Put/Call Ratio 지표
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutCallRatioDTO {
    
    private Long callVolume;  // 콜 전체 거래량
    private Long putVolume;   // 풋 전체 거래량
    private BigDecimal volumeRatio;  // 거래량 기준 Put/Call Ratio
    
    private Long callOpenInterest;  // 콜 미결제약정
    private Long putOpenInterest;   // 풋 미결제약정
    private BigDecimal openInterestRatio;  // 미결제약정 기준 Put/Call Ratio
    
    private BigDecimal callTradingValue;  // 콜 거래대금
    private BigDecimal putTradingValue;   // 풋 거래대금
    private BigDecimal tradingValueRatio;  // 거래대금 기준 Put/Call Ratio
}
