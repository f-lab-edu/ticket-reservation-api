package com.ticketing.reservation.member.dto;

import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.domain.MemberRole;

public record SignUpResponse(Long id, String email, MemberRole role) {

  public static SignUpResponse from(Member member) {
    return new SignUpResponse(member.getId(), member.getEmail(), member.getRole());
  }
}
