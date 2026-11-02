package com.ticketing.reservation.member.dto;

import com.ticketing.reservation.member.email.ValidEmail;
import com.ticketing.reservation.member.password.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record SignUpRequest(
    @NotBlank @ValidEmail String email, @NotBlank @ValidPassword String password) {}
