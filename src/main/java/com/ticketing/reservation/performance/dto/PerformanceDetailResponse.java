package com.ticketing.reservation.performance.dto;

import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceDetail;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import java.time.LocalDateTime;

public record PerformanceDetailResponse(
    Long id,
    String title,
    String venue,
    LocalDateTime performanceAt,
    int price,
    int totalQuantity,
    int remainingQuantity,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt) {

  public static PerformanceDetailResponse from(PerformanceDetail detail) {
    Performance performance = detail.performance();
    PerformanceStock stock = detail.stock();
    return new PerformanceDetailResponse(
        performance.getId(),
        performance.getTitle(),
        performance.getVenue(),
        performance.getPerformanceAt(),
        performance.getPrice(),
        stock.getTotalQuantity(),
        stock.getRemainingQuantity(),
        performance.getSalePeriod().saleStartAt(),
        performance.getSalePeriod().saleEndAt());
  }
}
