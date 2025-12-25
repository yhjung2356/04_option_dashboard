package com.trading.dashboard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "futures_data", indexes = {
        @Index(name = "idx_futures_volume", columnList = "volume"),
        @Index(name = "idx_futures_timestamp", columnList = "timestamp"),
        @Index(name = "idx_futures_symbol", columnList = "symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuturesData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol; // 선물 종목 코드

    @Column(nullable = false)
    private String name; // 종목명

    @Column(nullable = false)
    private BigDecimal currentPrice; // 현재가

    @Column
    private BigDecimal changeAmount; // 전일대비

    @Column
    private BigDecimal changePercent; // 등락률

    @Column(nullable = false)
    private Long volume; // 거래량

    @Column(nullable = false)
    private Long openInterest; // 미결제약정

    @Column(nullable = false)
    private BigDecimal tradingValue; // 거래대금

    @Column
    private BigDecimal bidPrice; // 매수호가

    @Column
    private BigDecimal askPrice; // 매도호가

    @Column
    private Integer bidVolume; // 매수잔량

    @Column
    private Integer askVolume; // 매도잔량

    @Column
    private BigDecimal openPrice; // 시가

    @Column
    private BigDecimal highPrice; // 고가

    @Column
    private BigDecimal lowPrice; // 저가

    @Column(nullable = false)
    private LocalDateTime timestamp; // 데이터 시간
}
