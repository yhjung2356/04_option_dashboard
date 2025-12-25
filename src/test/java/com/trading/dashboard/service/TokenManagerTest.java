package com.trading.dashboard.service;

import com.trading.dashboard.repository.OptionDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenManager 단위 테스트
 */
class TokenManagerTest {

    private TokenManager tokenManager;

    @BeforeEach
    void setUp() {
        tokenManager = new TokenManager();
    }

    @Test
    void testSetAndGetToken() {
        // Given
        String token = "test-token-123";
        java.time.LocalDateTime expiry = java.time.LocalDateTime.now().plusHours(1);

        // When
        tokenManager.setToken(token, expiry);
        String retrievedToken = tokenManager.getToken();

        // Then
        assertEquals(token, retrievedToken);
    }

    @Test
    void testIsTokenValid_WhenValid() {
        // Given
        String token = "test-token-123";
        java.time.LocalDateTime expiry = java.time.LocalDateTime.now().plusHours(1);
        tokenManager.setToken(token, expiry);

        // When & Then
        assertTrue(tokenManager.isTokenValid());
    }

    @Test
    void testIsTokenValid_WhenExpired() {
        // Given
        String token = "expired-token";
        java.time.LocalDateTime expiry = java.time.LocalDateTime.now().minusHours(1);
        tokenManager.setToken(token, expiry);

        // When & Then
        assertFalse(tokenManager.isTokenValid());
    }

    @Test
    void testClear() {
        // Given
        String token = "test-token-123";
        java.time.LocalDateTime expiry = java.time.LocalDateTime.now().plusHours(1);
        tokenManager.setToken(token, expiry);

        // When
        tokenManager.clear();

        // Then
        assertFalse(tokenManager.isTokenValid());
    }
}
