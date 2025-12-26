package com.trading.dashboard.service;

import com.trading.dashboard.repository.FuturesDataRepository;
import com.trading.dashboard.repository.OptionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 애플리케이션 시작 시 KIS API로 데이터 로드
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialDataLoader implements CommandLineRunner {

    private final FuturesDataRepository futuresDataRepository;
    private final OptionDataRepository optionDataRepository;
    private final KisApiService kisApiService;
    private final MarketDataService marketDataService;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("[STARTUP] Fast Startup Mode - Loading KIS API Data");
        log.info("========================================");

        // 시작 시 KIS API 데이터 로드 (비동기)
        CompletableFuture<Void> dataLoadingFuture = CompletableFuture.runAsync(this::loadInitialData);

        // ⚠️ 데이터 로딩 완료 후 WebSocket 연결 (순서 보장)
        dataLoadingFuture.thenRunAsync(() -> {
            log.info("[STARTUP] Data loading completed. Starting WebSocket...");
            marketDataService.startWebSocketIfNeeded();
        });

        log.info("[STARTUP] Background data loading started");
    }

    private void loadInitialData() {
        long startTime = System.currentTimeMillis();

        // 기존 데이터 확인
        long existingFutures = futuresDataRepository.count();
        long existingOptions = optionDataRepository.count();

        // 재시작할 때마다 최신 데이터로 갱신 (오래된 데이터 방지)
        try {
            if (existingFutures > 0 || existingOptions > 0) {
                log.info("[KIS API] Clearing old data ({} futures, {} options)...",
                        existingFutures, existingOptions);
                futuresDataRepository.deleteAll();
                optionDataRepository.deleteAll();
            }

            log.info("[KIS API] Loading fresh data from KIS API...");
            kisApiService.loadKospi200Futures();
            kisApiService.loadKospi200Options();

            long futuresCount = futuresDataRepository.count();
            long optionsCount = optionDataRepository.count();

            if (futuresCount > 0 || optionsCount > 0) {
                log.info("[KIS API] Data loaded successfully! ({} futures, {} options) [{}ms]",
                        futuresCount, optionsCount,
                        System.currentTimeMillis() - startTime);
            } else {
                log.warn("[KIS API] No data loaded. Please check your API credentials.");
            }
        } catch (Exception e) {
            log.error("[KIS API] Failed to load data: {}", e.getMessage());
            log.error("Please check your KIS API credentials and network connection.");
        }
    }
}
