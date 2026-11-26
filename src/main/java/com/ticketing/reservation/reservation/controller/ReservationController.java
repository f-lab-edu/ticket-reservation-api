package com.ticketing.reservation.reservation.controller;

import com.ticketing.reservation.global.web.ApiPaths;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import com.ticketing.reservation.reservation.dto.ReservationDetailResponse;
import com.ticketing.reservation.reservation.dto.ReservationListResponse;
import com.ticketing.reservation.reservation.dto.ReservationRequest;
import com.ticketing.reservation.reservation.dto.ReservationResponse;
import com.ticketing.reservation.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    return ReservationResponse.from(
        reservationService.reserve(
            memberId(accessToken), request.performanceId(), request.quantity()));
  }

  @GetMapping
  public ReservationListResponse getMyReservations(
      @AuthenticationPrincipal Jwt accessToken,
      @RequestParam(name = "status", required = false) Set<ReservationStatus> statuses) {
    return ReservationListResponse.from(
        reservationService.getMyReservations(memberId(accessToken), normalizeStatuses(statuses)));
  }

  @GetMapping("/{reservationId}")
  public ReservationDetailResponse getMyReservation(
      @AuthenticationPrincipal Jwt accessToken, @PathVariable Long reservationId) {
    return ReservationDetailResponse.from(
        reservationService.getMyReservation(memberId(accessToken), reservationId));
  }

  private static Set<ReservationStatus> normalizeStatuses(Set<ReservationStatus> statuses) {
    return statuses == null
        ? Set.of()
        : statuses.stream().filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private static Long memberId(Jwt accessToken) {
    return Long.valueOf(accessToken.getSubject());
  }
}
