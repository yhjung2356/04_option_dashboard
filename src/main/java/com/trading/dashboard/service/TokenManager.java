package com.trading.dashboard.service;

import com.trading.dashboard.exception.TokenExpiredException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe 토큰 관리자
 */
@Slf4j
@Component
public class TokenManager {

    private final AtomicReference<TokenInfo> tokenRef = new AtomicReference<>();

    @Data
    @AllArgsConstructor
    public static class TokenInfo {
        private final String token;
        private final LocalDateTime expiry;

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }

        public boolean isValid() {
            return token != null && !token.isEmpty() && !isExpired();
        }
    }

    /**
     * 토큰 설정
     */
    public void setToken(String token, LocalDateTime expiry) {
        TokenInfo newToken = new TokenInfo(token, expiry);
        tokenRef.set(newToken);
        log.debug("Token set. Expires at: {}", expiry);
    }

    /**
     * 토큰 가져오기
     */
    public String getToken() {
        TokenInfo current = tokenRef.get();

        if (current == null || !current.isValid()) {
            throw new TokenExpiredException("Token is null or expired");
        }

        return current.getToken();
    }

    /**
     * 토큰 유효성 확인
     */
    public boolean isTokenValid() {
        TokenInfo current = tokenRef.get();
        return current != null && current.isValid();
    }

    /**
     * 토큰 만료 시간 가져오기
     */
    public LocalDateTime getExpiry() {
        TokenInfo current = tokenRef.get();
        return current != null ? current.getExpiry() : null;
    }

    /**
     * 토큰 초기화
     */
    public void clear() {
        tokenRef.set(null);
        log.debug("Token cleared");
    }
}
