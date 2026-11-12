package com.ticketing.reservation.auth.filter;

import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

public class RevokedSessionFilter extends OncePerRequestFilter {

  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final ProblemDetailWriter problemDetailWriter;

  public RevokedSessionFilter(
      RefreshTokenRepository refreshTokenRepository,
      AuthenticationEntryPoint authenticationEntryPoint,
      ProblemDetailWriter problemDetailWriter) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.problemDetailWriter = problemDetailWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
      filterChain.doFilter(request, response);
      return;
    }

    String familyId =
        jwtAuthentication.getToken().getClaimAsString(AccessTokenProvider.SESSION_CLAIM);
    try {
      if (familyId == null || refreshTokenRepository.isFamilyRevoked(familyId)) {
        authenticationEntryPoint.commence(
            request, response, new InvalidBearerTokenException("Login session is not valid"));
        return;
      }
    } catch (BusinessException e) {
      problemDetailWriter.write(request, response, e.getErrorCode());
      return;
    }
    filterChain.doFilter(request, response);
  }
}
