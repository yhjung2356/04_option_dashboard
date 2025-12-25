package com.trading.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 한국투자증권 Open API 설정
 */
@Configuration
@ConfigurationProperties(prefix = "trading.kis")
@Data
public class KisApiConfig {

    private String appKey;
    private String appSecret;
    private String accountNo;
    private String baseUrl;

    // WebSocket
    private String websocketUrl;
}
