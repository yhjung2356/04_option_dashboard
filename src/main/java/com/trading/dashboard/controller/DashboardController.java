package com.trading.dashboard.controller;

import com.trading.dashboard.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final TradingProperties tradingProperties;

    @GetMapping("/")
    public String index(Model model) {
        // 페이지 초기 상태를 Model을 통해 전달
        model.addAttribute("dataSource", tradingProperties.getDataSource());
        model.addAttribute("demoMode", tradingProperties.isDemoMode());
        model.addAttribute("marketHoursEnabled", tradingProperties.getMarketHours().isEnabled());
        model.addAttribute("currentTimestamp", System.currentTimeMillis());

        return "dashboard";
    }
}
