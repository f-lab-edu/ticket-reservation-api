package com.ticketing.reservation.auth.controller;

import com.ticketing.reservation.auth.dto.LoginRequest;
import com.ticketing.reservation.auth.dto.RefreshTokenRequest;
import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.service.AuthService;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request.email(), request.password());
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return authService.refresh(request.refreshToken());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal Jwt accessToken) {
    authService.logout(accessToken.getClaimAsString(AccessTokenProvider.SESSION_CLAIM));
  }
}
