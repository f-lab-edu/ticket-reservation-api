package com.ticketing.reservation.reservation.dto;

import static com.ticketing.reservation.reservation.dto.ReservationResponse.MICROS_PATTERN;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationDetail;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationDetailResponse(
    Long id,
    PerformanceSummary performance,
    int quantity,
    int unitPrice,
    long totalPrice,
    ReservationStatus status,
    @JsonFormat(pattern = MICROS_PATTERN) LocalDateTime reservedAt,
    @JsonFormat(pattern = MICROS_PATTERN) LocalDateTime paymentDeadlineAt) {

  public static ReservationDetailResponse from(ReservationDetail detail) {
    Reservation reservation = detail.reservation();
    return new ReservationDetailResponse(
        reservation.getId(),
        PerformanceSummary.from(detail.performance()),
        reservation.getQuantity(),
        reservation.getUnitPrice(),
        reservation.getTotalPrice(),
        detail.status(),
        reservation.getReservedAt(),
        detail.paymentDeadlineAt());
  }
}
