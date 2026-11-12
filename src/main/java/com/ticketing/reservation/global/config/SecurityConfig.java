package com.ticketing.reservation.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.reservation.auth.filter.RevokedSessionFilter;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.global.error.ProblemDetailWriter;
import com.ticketing.reservation.global.web.ApiPaths;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  private static final PathPatternRequestMatcher.Builder PATH =
      PathPatternRequestMatcher.withDefaults();

  private static final RequestMatcher PUBLIC_ENDPOINTS =
      new OrRequestMatcher(
          PATH.matcher(HttpMethod.GET, ApiPaths.V1 + "/members/email-availability"),
          PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/members"),
          PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/login"),
          PATH.matcher(HttpMethod.POST, ApiPaths.V1 + "/auth/refresh"));

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectMapper objectMapper, RefreshTokenRepository refreshTokenRepository)
      throws Exception {
    ProblemDetailWriter problemDetailWriter = new ProblemDetailWriter(objectMapper);
    AuthenticationEntryPoint entryPoint = problemDetailEntryPoint(problemDetailWriter);
    return http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .bearerTokenResolver(bearerTokenResolver())
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(entryPoint))
        .addFilterAfter(
            new RevokedSessionFilter(refreshTokenRepository, entryPoint, problemDetailWriter),
            BearerTokenAuthenticationFilter.class)
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
        .build();
  }

  private static AuthenticationEntryPoint problemDetailEntryPoint(
      ProblemDetailWriter problemDetailWriter) {
    AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    return (request, response, authException) -> {
      delegate.commence(request, response, authException);
      problemDetailWriter.write(request, response, ErrorCode.UNAUTHORIZED);
    };
  }

  private static BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request -> PUBLIC_ENDPOINTS.matches(request) ? null : delegate.resolve(request);
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthoritiesClaimName(AccessTokenProvider.ROLE_CLAIM);
    authoritiesConverter.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return converter;
  }
}
