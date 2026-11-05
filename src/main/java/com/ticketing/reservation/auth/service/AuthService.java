package com.ticketing.reservation.auth.service;

import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.email.EmailNormalizer;
import com.ticketing.reservation.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenProvider accessTokenProvider;

  public AuthService(
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      AccessTokenProvider accessTokenProvider) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.accessTokenProvider = accessTokenProvider;
  }

  public TokenResponse login(String email, String rawPassword) {
    Member member =
        memberRepository
            .findByEmail(EmailNormalizer.normalize(email))
            .filter(found -> passwordEncoder.matches(rawPassword, found.getPassword()))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    return TokenResponse.bearer(
        accessTokenProvider.issue(member), accessTokenProvider.validitySeconds());
  }
}
