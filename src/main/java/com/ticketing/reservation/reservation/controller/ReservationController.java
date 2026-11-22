package com.ticketing.reservation.reservation.controller;

import com.ticketing.reservation.global.web.ApiPaths;
import com.ticketing.reservation.reservation.dto.ReservationRequest;
import com.ticketing.reservation.reservation.dto.ReservationResponse;
import com.ticketing.reservation.reservation.service.ReservationService;
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
@RequestMapping(ApiPaths.V1 + "/reservations")
public class ReservationController {

  private final ReservationService reservationService;

  public ReservationController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReservationResponse reserve(
      @AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody ReservationRequest request) {
    Long memberId = Long.valueOf(accessToken.getSubject());
    return ReservationResponse.from(
        reservationService.reserve(memberId, request.performanceId(), request.quantity()));
  }
}
