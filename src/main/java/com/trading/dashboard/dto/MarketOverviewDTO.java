package com.trading.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 전체 시장 현황 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketOverviewDTO {
    
    // 선물 전체 통계
    private Long totalFuturesVolume;
    private BigDecimal totalFuturesTradingValue;
    private Long totalFuturesOpenInterest;
    
    // 옵션 전체 통계
    private Long totalOptionsVolume;
    private BigDecimal totalOptionsTradingValue;
    private Long totalOptionsOpenInterest;
    
    // 콜/풋 비율
    private PutCallRatioDTO putCallRatio;
    
    // 거래량 상위 종목
    private List<TopTradedInstrumentDTO> topByVolume;
    
    // 미결제약정 상위 종목
    private List<TopTradedInstrumentDTO> topByOpenInterest;
}
