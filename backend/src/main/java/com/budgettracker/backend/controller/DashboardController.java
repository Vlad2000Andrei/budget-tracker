package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.DashboardSummaryDto;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dashboard-summary")
public class DashboardController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary(User user) {
        DashboardSummaryDto summary = dashboardService.getDashboardSummary(user);
        return ResponseEntity.ok(summary);
    }
}
