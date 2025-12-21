package com.trading.dashboard.dto;

import com.trading.dashboard.model.OptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 행사가별 옵션 데이터 (콜/풋 함께)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrikePriceDataDTO {
    
    private BigDecimal strikePrice;
    
    // 콜 옵션 데이터
    private BigDecimal callPrice;
    private Long callVolume;
    private Long callOpenInterest;
    private BigDecimal callImpliedVolatility;
    private BigDecimal callDelta;
    private BigDecimal callGamma;
    private BigDecimal callTheta;
    private BigDecimal callVega;
    private BigDecimal callBidPrice;
    private BigDecimal callAskPrice;
    
    // 풋 옵션 데이터
    private BigDecimal putPrice;
    private Long putVolume;
    private Long putOpenInterest;
    private BigDecimal putImpliedVolatility;
    private BigDecimal putDelta;
    private BigDecimal putGamma;
    private BigDecimal putTheta;
    private BigDecimal putVega;
    private BigDecimal putBidPrice;
    private BigDecimal putAskPrice;
    
    // 총합
    private Long totalVolume;
    private Long totalOpenInterest;
}
