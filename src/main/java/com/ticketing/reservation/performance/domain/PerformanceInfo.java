package com.ticketing.reservation.performance.domain;

import java.time.LocalDateTime;

public record PerformanceInfo(
    String title,
    String venue,
    LocalDateTime performanceAt,
    int price,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt) {}
