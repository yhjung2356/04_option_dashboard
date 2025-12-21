package com.trading.dashboard.dto;

import com.trading.dashboard.model.InstrumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 거래 상위 종목 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopTradedInstrumentDTO {
    
    private String symbol;
    private String name;
    private InstrumentType type;
    private BigDecimal currentPrice;
    private Long volume;
    private BigDecimal tradingValue;
    private Long openInterest;
    private BigDecimal changePercent;
}
