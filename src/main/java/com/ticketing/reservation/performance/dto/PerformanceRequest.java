package com.ticketing.reservation.performance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @NotNull @JsonFormat(pattern = DATE_TIME_PATTERN) LocalDateTime performanceAt,
    @NotNull @PositiveOrZero Integer price,
    @NotNull @Positive Integer totalQuantity,
    @NotNull @JsonFormat(pattern = DATE_TIME_PATTERN) LocalDateTime saleStartAt,
    @NotNull @JsonFormat(pattern = DATE_TIME_PATTERN) LocalDateTime saleEndAt) {

  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
}
