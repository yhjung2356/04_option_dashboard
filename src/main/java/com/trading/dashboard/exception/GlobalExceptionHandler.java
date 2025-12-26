package com.trading.dashboard.exception;

import com.trading.dashboard.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * 전역 예외 처리기
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataFetchException.class)
    public ResponseEntity<ErrorResponse> handleDataFetchException(DataFetchException e) {
        log.error("Data fetch error: source={}, symbol={}", e.getSource(), e.getSymbol(), e);
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpiredException(TokenExpiredException e) {
        log.error("Token expired", e);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(DataParseException.class)
    public ResponseEntity<ErrorResponse> handleDataParseException(DataParseException e) {
        log.error("Data parse error: {}", e.getRawData(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse data");
    }

    /**
     * HttpMessageNotWritableException - SockJS 요청 시 발생하는 컨버터 에러 무시
     * (application/javascript로 JSON 응답 시도 시 발생)
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Void> handleHttpMessageNotWritableException(HttpMessageNotWritableException e) {
        // SockJS 관련 에러는 DEBUG 레벨로만 로깅 (정상 동작)
        log.debug("HttpMessageNotWritable (SockJS fallback): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    /**
     * NoResourceFoundException - 브라우저 자동 요청 파일 무시
     * (favicon.ico, .well-known/*, robots.txt 등)
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        String path = e.getMessage();

        // 브라우저 자동 요청 파일들은 DEBUG 레벨로만 로깅
        if (path != null && (path.contains("favicon.ico")
                || path.contains(".well-known/")
                || path.contains("pwa-")
                || path.contains("apple-touch-icon"))) {
            log.debug("Browser auto-request (ignored): {}", path);
        } else {
            // 기타 정적 리소스 누락은 WARN 레벨로 로깅
            log.warn("Static resource not found: {}", path);
        }

        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .build();
        return new ResponseEntity<>(response, status);
    }
}
