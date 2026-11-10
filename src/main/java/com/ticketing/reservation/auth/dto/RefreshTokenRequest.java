package com.ticketing.reservation.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank String refreshToken) {

  @Override
  public String toString() {
    return "RefreshTokenRequest[refreshToken=***]";
  }
}
