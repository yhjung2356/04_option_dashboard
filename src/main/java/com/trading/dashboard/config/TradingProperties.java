package com.trading.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 거래 관련 설정 속성
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    /**
     * 데이터 소스 설정
     */
    private String dataSource = "KIS";

    /**
     * 데모 모드 설정
     */
    private boolean demoMode = false;

    /**
     * 장 시간 체크 설정
     */
    private MarketHours marketHours = new MarketHours();

    /**
     * KRX API 설정
     */
    private Krx krx = new Krx();

    @Data
    public static class MarketHours {
        private boolean enabled = true;
    }

    @Data
    public static class Krx {
        private String apiUrl = "http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd";
    }
}
