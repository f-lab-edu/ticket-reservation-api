package com.ticketing.reservation.performance.service;

import com.ticketing.reservation.performance.domain.PerformanceStock;
import java.time.LocalDateTime;

public interface OverdueReservationExpirer {

  void expireOverdue(PerformanceStock stock, LocalDateTime now);
}
