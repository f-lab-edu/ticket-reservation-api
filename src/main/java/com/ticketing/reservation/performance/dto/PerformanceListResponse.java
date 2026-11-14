package com.ticketing.reservation.performance.dto;

import com.ticketing.reservation.performance.domain.Performance;
import java.time.LocalDateTime;
import java.util.List;

public record PerformanceListResponse(List<Item> performances) {

  public static PerformanceListResponse from(List<Performance> performances) {
    return new PerformanceListResponse(performances.stream().map(Item::from).toList());
  }

  public record Item(
      Long id,
      String title,
      String venue,
      LocalDateTime performanceAt,
      int price,
      int remainingQuantity) {

    private static Item from(Performance performance) {
      return new Item(
          performance.getId(),
          performance.getTitle(),
          performance.getVenue(),
          performance.getPerformanceAt(),
          performance.getPrice(),
          performance.getRemainingQuantity());
    }
  }
}
