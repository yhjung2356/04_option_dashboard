package com.trading.dashboard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "option_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;  // 옵션 종목 코드
    
    @Column
    private String name;  // 옵션 종목명 (예: "C 202601 567.5")
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OptionType optionType;  // CALL or PUT
    
    @Column(nullable = false)
    private BigDecimal strikePrice;  // 행사가
    
    @Column(nullable = false)
    private BigDecimal currentPrice;  // 현재가
    
    @Column(nullable = false)
    private Long volume;  // 거래량
    
    @Column(nullable = false)
    private Long openInterest;  // 미결제약정
    
    @Column(nullable = false)
    private BigDecimal tradingValue;  // 거래대금
    
    @Column
    private BigDecimal impliedVolatility;  // 내재변동성
    
    @Column
    private BigDecimal delta;  // 델타
    
    @Column
    private BigDecimal gamma;  // 감마
    
    @Column
    private BigDecimal theta;  // 세타
    
    @Column
    private BigDecimal vega;  // 베가
    
    @Column
    private BigDecimal bidPrice;  // 매수호가
    
    @Column
    private BigDecimal askPrice;  // 매도호가
    
    @Column
    private Integer bidVolume;  // 매수잔량
    
    @Column
    private Integer askVolume;  // 매도잔량
    
    @Column
    private BigDecimal underlyingPrice;  // 기초자산가격 (KOSPI200 지수)
    
    @Column(nullable = false)
    private LocalDateTime timestamp;  // 데이터 시간
    
    @Column
    private String expiryDate;  // 만기일
}
