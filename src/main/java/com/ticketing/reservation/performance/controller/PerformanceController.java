package com.ticketing.reservation.performance.controller;

import com.ticketing.reservation.global.web.ApiPaths;
import com.ticketing.reservation.global.web.IfMatch;
import com.ticketing.reservation.performance.domain.PerformanceDetail;
import com.ticketing.reservation.performance.dto.PerformanceDetailResponse;
import com.ticketing.reservation.performance.dto.PerformanceListResponse;
import com.ticketing.reservation.performance.dto.PerformanceRequest;
import com.ticketing.reservation.performance.service.PerformanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/performances")
public class PerformanceController {

  private final PerformanceService performanceService;

  public PerformanceController(PerformanceService performanceService) {
    this.performanceService = performanceService;
  }

  @PostMapping
  public ResponseEntity<PerformanceDetailResponse> register(
      @Valid @RequestBody PerformanceRequest request) {
    return withETag(
        HttpStatus.CREATED,
        performanceService.register(request.toPerformanceInfo(), request.totalQuantity()));
  }

  @GetMapping("/{performanceId}")
  public ResponseEntity<PerformanceDetailResponse> getPerformance(
      @PathVariable Long performanceId) {
    return withETag(HttpStatus.OK, performanceService.getPerformance(performanceId));
  }

  @PutMapping("/{performanceId}")
  public ResponseEntity<PerformanceDetailResponse> update(
      @PathVariable Long performanceId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody PerformanceRequest request) {
    return ResponseEntity.ok(
        PerformanceDetailResponse.from(
            performanceService.update(
                performanceId,
                request.toPerformanceInfo(),
                request.totalQuantity(),
                IfMatch.parse(ifMatch))));
  }

  @GetMapping
  public PerformanceListResponse getPerformances(
      @RequestParam(defaultValue = "false") boolean available) {
    return PerformanceListResponse.from(performanceService.getPerformances(available));
  }

  private static ResponseEntity<PerformanceDetailResponse> withETag(
      HttpStatus status, PerformanceDetail detail) {
    return ResponseEntity.status(status)
        .eTag(IfMatch.eTagOf(detail.performance().getVersion()))
        .body(PerformanceDetailResponse.from(detail));
  }
}
