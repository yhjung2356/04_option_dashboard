package com.trading.dashboard.exception;

import com.trading.dashboard.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 단위 테스트
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleDataFetchException() {
        // Given
        DataFetchException exception = new DataFetchException("API error", "KIS", "KR4101P12345");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleDataFetchException(exception);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("API error"));
    }

    @Test
    void testHandleTokenExpiredException() {
        // Given
        TokenExpiredException exception = new TokenExpiredException("Token expired");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleTokenExpiredException(exception);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Token expired", response.getBody().getMessage());
    }

    @Test
    void testHandleDataParseException() {
        // Given
        DataParseException exception = new DataParseException("Invalid JSON", "{invalid}");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleDataParseException(exception);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Failed to parse data"));
    }

    @Test
    void testHandleGenericException() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(exception);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("An unexpected error occurred"));
    }
}
