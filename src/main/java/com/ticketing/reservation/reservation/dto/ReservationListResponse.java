package com.ticketing.reservation.reservation.dto;

import static com.ticketing.reservation.reservation.dto.ReservationResponse.MICROS_PATTERN;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationDetail;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationListResponse(List<Item> reservations) {

  public static ReservationListResponse from(List<ReservationDetail> details) {
    return new ReservationListResponse(details.stream().map(Item::from).toList());
  }

  public record Item(
      Long id,
      PerformanceItem performance,
      int quantity,
      long totalPrice,
      ReservationStatus status,
      @JsonFormat(pattern = MICROS_PATTERN) LocalDateTime paymentDeadlineAt) {

    private static Item from(ReservationDetail detail) {
      Reservation reservation = detail.reservation();
      return new Item(
          reservation.getId(),
          PerformanceItem.from(detail.performance()),
          reservation.getQuantity(),
          reservation.getTotalPrice(),
          detail.status(),
          detail.paymentDeadlineAt());
    }
  }

  public record PerformanceItem(Long id, String title, LocalDateTime performanceAt) {

    private static PerformanceItem from(Performance performance) {
      return new PerformanceItem(
          performance.getId(), performance.getTitle(), performance.getPerformanceAt());
    }
  }
}
