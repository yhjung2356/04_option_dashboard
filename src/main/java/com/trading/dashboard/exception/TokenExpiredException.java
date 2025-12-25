package com.trading.dashboard.exception;

/**
 * API 토큰 만료 시 발생하는 예외
 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
