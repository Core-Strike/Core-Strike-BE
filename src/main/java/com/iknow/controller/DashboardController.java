package com.iknow.controller;

import com.iknow.dto.response.DashboardClassResponse;
import com.iknow.dto.response.KeywordReportResponse;
import com.iknow.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // GET /api/dashboard/classes — 반별 통계 (대시보드용)
    @GetMapping("/classes")
    public ResponseEntity<List<DashboardClassResponse>> getDashboardClasses(@RequestParam LocalDate date) {
        return ResponseEntity.ok(dashboardService.getDashboardClasses(date));
    }

    @GetMapping("/keyword-report")
    public ResponseEntity<KeywordReportResponse> getKeywordReport(
            @RequestParam LocalDate date,
            @RequestParam String keyword,
            @RequestParam(required = false) String curriculum,
            @RequestParam(required = false) String classId
    ) {
        return ResponseEntity.ok(dashboardService.getKeywordReport(date, keyword, curriculum, classId));
    }
}
