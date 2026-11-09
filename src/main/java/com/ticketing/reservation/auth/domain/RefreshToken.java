package com.ticketing.reservation.auth.domain;

import java.time.Instant;

public record RefreshToken(
    String tokenHash,
    Long memberId,
    String familyId,
    RefreshTokenStatus status,
    Instant expiresAt,
    Instant createdAt) {}
