package com.ticketing.reservation.performance.dto;

import com.ticketing.reservation.performance.domain.Performance;
import java.time.LocalDateTime;

public record PerformanceResponse(
    Long id,
    String title,
    String venue,
    LocalDateTime performanceAt,
    int price,
    int totalQuantity,
    int remainingQuantity,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt) {

  public static PerformanceResponse from(Performance performance) {
    return new PerformanceResponse(
        performance.getId(),
        performance.getTitle(),
        performance.getVenue(),
        performance.getPerformanceAt(),
        performance.getPrice(),
        performance.getTotalQuantity(),
        performance.getRemainingQuantity(),
        performance.getSaleStartAt(),
        performance.getSaleEndAt());
  }
}
