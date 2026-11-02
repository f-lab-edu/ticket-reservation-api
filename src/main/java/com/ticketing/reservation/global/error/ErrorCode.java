package com.ticketing.reservation.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");

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
