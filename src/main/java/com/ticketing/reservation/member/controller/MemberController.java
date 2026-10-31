package com.ticketing.reservation.member.controller;

import com.ticketing.reservation.member.dto.EmailAvailabilityResponse;
import com.ticketing.reservation.member.dto.SignUpRequest;
import com.ticketing.reservation.member.dto.SignUpResponse;
import com.ticketing.reservation.member.email.ValidEmail;
import com.ticketing.reservation.member.service.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
  }

  @GetMapping("/email-availability")
  public EmailAvailabilityResponse checkEmailAvailability(
      @RequestParam @NotBlank @ValidEmail String email) {
    return new EmailAvailabilityResponse(memberService.isEmailAvailable(email));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SignUpResponse signUp(@Valid @RequestBody SignUpRequest request) {
    return SignUpResponse.from(memberService.signUp(request.email(), request.password()));
  }
}
