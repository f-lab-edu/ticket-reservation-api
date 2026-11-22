package com.ticketing.reservation.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservationRequest(
    @NotNull Long performanceId, @NotNull @Positive Integer quantity) {}
