package com.ticketing.reservation.reservation.domain;

import com.ticketing.reservation.performance.domain.Performance;
import java.time.LocalDateTime;

public record ReservationDetail(
    Reservation reservation, Performance performance, ReservationStatus status) {

  public LocalDateTime paymentDeadlineAt() {
    if (status != ReservationStatus.PENDING_PAYMENT) {
      return null;
    }
    return reservation.getPaymentDeadlineAt();
  }
}
