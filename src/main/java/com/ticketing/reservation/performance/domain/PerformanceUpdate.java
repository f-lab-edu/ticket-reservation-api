package com.ticketing.reservation.performance.domain;

import java.time.LocalDateTime;

public record PerformanceUpdate(
    String title,
    String venue,
    LocalDateTime performanceAt,
    int price,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt) {}
