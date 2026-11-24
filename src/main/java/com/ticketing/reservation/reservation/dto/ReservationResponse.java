package com.ticketing.reservation.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationResponse(
    Long id,
    Long performanceId,
    int quantity,
    int unitPrice,
    long totalPrice,
    ReservationStatus status,
    @JsonFormat(pattern = MICROS_PATTERN) LocalDateTime reservedAt,
    @JsonFormat(pattern = MICROS_PATTERN) LocalDateTime paymentDeadlineAt) {

  static final String MICROS_PATTERN = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS";

  public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        reservation.getPerformanceId(),
        reservation.getQuantity(),
        reservation.getUnitPrice(),
        reservation.getTotalPrice(),
        reservation.getStatus(),
        reservation.getReservedAt(),
        reservation.getPaymentDeadlineAt());
  }
}
