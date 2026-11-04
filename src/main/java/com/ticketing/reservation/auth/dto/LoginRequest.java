package com.ticketing.reservation.auth.dto;

import com.ticketing.reservation.member.password.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank @ValidPassword String password) {

  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=***]";
  }
}
