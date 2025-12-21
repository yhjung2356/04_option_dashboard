package com.trading.dashboard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    
    @Value("${trading.data-source}")
    private String dataSource;
    
    @Value("${trading.demo-mode}")
    private boolean demoMode;
    
    @Value("${trading.market-hours.enabled}")
    private boolean marketHoursEnabled;
    
    @GetMapping("/")
    public String index(Model model) {
        // 페이지 초기 상태를 Model을 통해 전달
        model.addAttribute("dataSource", dataSource);
        model.addAttribute("demoMode", demoMode);
        model.addAttribute("marketHoursEnabled", marketHoursEnabled);
        model.addAttribute("currentTimestamp", System.currentTimeMillis());
        
        return "dashboard";
    }
}
