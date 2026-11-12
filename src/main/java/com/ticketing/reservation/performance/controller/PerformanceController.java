package com.ticketing.reservation.performance.controller;

import com.ticketing.reservation.global.web.ApiPaths;
import com.ticketing.reservation.performance.dto.PerformanceRequest;
import com.ticketing.reservation.performance.dto.PerformanceResponse;
import com.ticketing.reservation.performance.service.PerformanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
  public PerformanceResponse register(@Valid @RequestBody PerformanceRequest request) {
    return PerformanceResponse.from(performanceService.register(request));
  }
}
