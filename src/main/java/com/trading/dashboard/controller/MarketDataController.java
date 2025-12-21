package com.trading.dashboard.controller;

import com.trading.dashboard.dto.MarketOverviewDTO;
import com.trading.dashboard.dto.OptionChainAnalysisDTO;
import com.trading.dashboard.dto.PutCallRatioDTO;
import com.trading.dashboard.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {
    
    private final MarketDataService marketDataService;
    
    @Value("${trading.data-source}")
    private String dataSource;
    
    @Value("${trading.demo-mode}")
    private boolean demoMode;
    
    @Value("${trading.market-hours.enabled}")
    private boolean marketHoursEnabled;
    
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
        state.put("dataSource", dataSource);
        state.put("demoMode", demoMode);
        state.put("marketHoursEnabled", marketHoursEnabled);
        state.put("timestamp", System.currentTimeMillis());
        state.put("serverTime", new java.util.Date());
        return ResponseEntity.ok(state);
    }
}
