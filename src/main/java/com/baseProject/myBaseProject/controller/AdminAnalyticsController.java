package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminAnalyticsResponse;
import com.baseProject.myBaseProject.dto.admin.AdminAnalyticsTimeSeriesResponse;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/analytics")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị - Phân tích")
public class AdminAnalyticsController {
    private final AdminAnalyticsService service;

    @GetMapping("/summary")
    @Operation(summary = "Lấy funnel và số liệu tổng hợp trong kỳ")
    public AdminAnalyticsResponse summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.summary(from, to);
    }

    @GetMapping("/time-series")
    @Operation(summary = "Lấy chuỗi số liệu theo ngày UTC")
    public AdminAnalyticsTimeSeriesResponse timeSeries(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.timeSeries(from, to);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Xuất chuỗi số liệu theo ngày dạng CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=admin-analytics.csv")
                .body(service.exportCsv(from, to));
    }
}
