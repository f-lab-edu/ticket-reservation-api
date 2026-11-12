package com.ticketing.reservation.performance.dto;

import com.ticketing.reservation.performance.domain.Performance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PerformanceRequest(
    @NotBlank @Size(max = Performance.MAX_TITLE_LENGTH) String title,
    @NotBlank @Size(max = Performance.MAX_VENUE_LENGTH) String venue,
    @NotNull LocalDateTime performanceAt,
    @NotNull @PositiveOrZero Integer price,
    @NotNull @Positive Integer totalQuantity,
    @NotNull LocalDateTime saleStartAt,
    @NotNull LocalDateTime saleEndAt) {}
