package com.ticketing.reservation.auth.dto;

public record TokenResponse(
    String tokenType, String accessToken, long expiresIn, String refreshToken) {

  private static final String BEARER = "Bearer";

  public static TokenResponse bearer(String accessToken, long expiresIn, String refreshToken) {
    return new TokenResponse(BEARER, accessToken, expiresIn, refreshToken);
  }

  @Override
  public String toString() {
    return "TokenResponse[tokenType="
        + tokenType
        + ", accessToken=***, expiresIn="
        + expiresIn
        + ", refreshToken=***]";
  }
}
