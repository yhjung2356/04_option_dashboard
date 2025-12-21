package com.trading.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 시간대별 거래 현황
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSeriesDataDTO {
    
    private LocalDateTime timestamp;
    private Long volume;
    private BigDecimal tradingValue;
    private Long openInterest;
    private BigDecimal price;
}
