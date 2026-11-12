package com.ticketing.reservation.performance.controller;

import com.ticketing.reservation.global.web.ApiPaths;
import com.ticketing.reservation.performance.dto.PerformanceDetailResponse;
import com.ticketing.reservation.performance.dto.PerformanceRequest;
import com.ticketing.reservation.performance.service.PerformanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/performances")
public class PerformanceController {

  private final PerformanceService performanceService;

  public PerformanceController(PerformanceService performanceService) {
    this.performanceService = performanceService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PerformanceDetailResponse register(@Valid @RequestBody PerformanceRequest request) {
    return PerformanceDetailResponse.from(performanceService.register(request));
  }

  @GetMapping("/{performanceId}")
  public PerformanceDetailResponse getPerformance(@PathVariable Long performanceId) {
    return PerformanceDetailResponse.from(performanceService.getPerformance(performanceId));
  }
}
