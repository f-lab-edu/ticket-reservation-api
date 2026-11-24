package com.ticketing.reservation.reservation.dto;

import com.ticketing.reservation.performance.domain.Performance;
import java.time.LocalDateTime;

public record PerformanceSummary(Long id, String title, String venue, LocalDateTime performanceAt) {

  static PerformanceSummary from(Performance performance) {
    return new PerformanceSummary(
        performance.getId(),
        performance.getTitle(),
        performance.getVenue(),
        performance.getPerformanceAt());
  }
}
