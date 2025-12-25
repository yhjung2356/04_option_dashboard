package com.trading.dashboard.controller;

import com.trading.dashboard.config.TradingProperties;
import com.trading.dashboard.dto.MarketOverviewDTO;
import com.trading.dashboard.dto.OptionChainAnalysisDTO;
import com.trading.dashboard.dto.PutCallRatioDTO;
import com.trading.dashboard.service.InitialDataLoader;
import com.trading.dashboard.service.MarketDataService;
import com.trading.dashboard.service.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final TradingProperties tradingProperties;
    private final InitialDataLoader initialDataLoader;
    private final TradingCalendarService tradingCalendarService;

    /**
     * 전체 시장 현황
     */
    @GetMapping("/overview")
    public ResponseEntity<MarketOverviewDTO> getMarketOverview() {
        return ResponseEntity.ok(marketDataService.getMarketOverview());
    }

    /**
     * Put/Call Ratio
     */
    @GetMapping("/put-call-ratio")
    public ResponseEntity<PutCallRatioDTO> getPutCallRatio() {
        return ResponseEntity.ok(marketDataService.calculatePutCallRatio());
    }

    /**
     * 옵션 체인 분석
     */
    @GetMapping("/option-chain")
    public ResponseEntity<OptionChainAnalysisDTO> getOptionChainAnalysis() {
        return ResponseEntity.ok(marketDataService.getOptionChainAnalysis());
    }

    /**
     * 시스템 상태 조회 (페이지 상태 전달용)
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getSystemState() {
        Map<String, Object> state = new HashMap<>();
        state.put("dataSource", tradingProperties.getDataSource());
        state.put("demoMode", tradingProperties.isDemoMode());
        state.put("marketHoursEnabled", tradingProperties.getMarketHours().isEnabled());
        state.put("isTradingDay", tradingCalendarService.isTradingDay());
        state.put("isHoliday", tradingCalendarService.isHoliday());
        state.put("timestamp", System.currentTimeMillis());
        state.put("serverTime", new java.util.Date());
        return ResponseEntity.ok(state);
    }

    /**
     * 거래일 여부 확인
     */
    @GetMapping("/is-trading-day")
    public ResponseEntity<Map<String, Boolean>> isTradingDay() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("isTradingDay", tradingCalendarService.isTradingDay());
        result.put("isHoliday", tradingCalendarService.isHoliday());
        return ResponseEntity.ok(result);
    }

    /**
     * 선물 데이터 조회
     */
    @GetMapping("/futures")
    public ResponseEntity<?> getFutures() {
        return ResponseEntity.ok(marketDataService.getAllFutures());
    }

    /**
     * 옵션 데이터 조회
     */
    @GetMapping("/options")
    public ResponseEntity<?> getOptions() {
        return ResponseEntity.ok(marketDataService.getAllOptions());
    }

    /**
     * 샘플 데이터 강제 로드 (개발/테스트용)
     */
    @PostMapping("/load-sample-data")
    public ResponseEntity<Map<String, Object>> loadSampleData() {
        try {
            initialDataLoader.forceLoadSampleData();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Sample data loaded successfully");
            response.put("futuresCount", marketDataService.getAllFutures().size());
            response.put("optionsCount", marketDataService.getAllOptions().size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to load sample data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}