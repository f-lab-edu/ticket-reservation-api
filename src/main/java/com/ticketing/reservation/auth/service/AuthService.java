package com.ticketing.reservation.auth.service;

import com.ticketing.reservation.auth.domain.RefreshToken;
import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.repository.RotationResult;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.auth.token.RefreshTokenProvider;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.email.EmailNormalizer;
import com.ticketing.reservation.member.repository.MemberRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenProvider accessTokenProvider;
  private final RefreshTokenProvider refreshTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;

  public AuthService(
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      AccessTokenProvider accessTokenProvider,
      RefreshTokenProvider refreshTokenProvider,
      RefreshTokenRepository refreshTokenRepository) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.accessTokenProvider = accessTokenProvider;
    this.refreshTokenProvider = refreshTokenProvider;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  public TokenResponse login(String email, String rawPassword) {
    Member member =
        memberRepository
            .findByEmail(EmailNormalizer.normalize(email))
            .filter(found -> passwordEncoder.matches(rawPassword, found.getPassword()))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    String refreshToken = refreshTokenProvider.generate();
    refreshTokenRepository.save(
        refreshTokenProvider.hash(refreshToken), member.getId(), UUID.randomUUID().toString());

    return tokenResponse(member, refreshToken);
  }

  public TokenResponse refresh(String refreshToken) {
    String currentHash = refreshTokenProvider.hash(refreshToken);
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHash(currentHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

    Member member =
        memberRepository
            .findById(current.memberId())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

    String newRefreshToken = refreshTokenProvider.generate();
    TokenResponse response = tokenResponse(member, newRefreshToken);

    RotationResult result =
        refreshTokenRepository.rotate(
            currentHash, refreshTokenProvider.hash(newRefreshToken), current.familyId());
    if (result == RotationResult.REUSED) {
      log.warn(
          "Refresh token reuse detected: memberId={}, familyId={}",
          current.memberId(),
          current.familyId());
    }
    if (result != RotationResult.ROTATED) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    return response;
  }

  private TokenResponse tokenResponse(Member member, String refreshToken) {
    return TokenResponse.bearer(
        accessTokenProvider.issue(member), accessTokenProvider.validitySeconds(), refreshToken);
  }
}
