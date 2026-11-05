package com.ticketing.reservation.auth.dto;

public record TokenResponse(String tokenType, String accessToken, long expiresIn) {

  private static final String BEARER = "Bearer";

  public static TokenResponse bearer(String accessToken, long expiresIn) {
    return new TokenResponse(BEARER, accessToken, expiresIn);
  }

  @Override
  public String toString() {
    return "TokenResponse[tokenType="
        + tokenType
        + ", accessToken=***, expiresIn="
        + expiresIn
        + "]";
  }
}
