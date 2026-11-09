package com.ticketing.reservation.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 없거나 유효하지 않습니다."),
  AUTH_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 서비스를 일시적으로 사용할 수 없습니다.");

  private final HttpStatus status;
  private final String detail;

  ErrorCode(HttpStatus status, String detail) {
    this.status = status;
    this.detail = detail;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getDetail() {
    return detail;
  }
}
