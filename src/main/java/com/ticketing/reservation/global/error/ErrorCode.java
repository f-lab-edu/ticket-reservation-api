package com.ticketing.reservation.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  // 회원
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

  // 인증
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 없거나 유효하지 않습니다."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "요청한 작업을 수행할 권한이 없습니다."),
  AUTH_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 서비스를 일시적으로 사용할 수 없습니다."),

  // 공연
  INVALID_SALE_START_AT(HttpStatus.BAD_REQUEST, "판매 시작 일시는 현재 시각보다 늦어야 합니다."),
  INVALID_SALE_PERIOD(HttpStatus.BAD_REQUEST, "판매 종료 일시는 판매 시작 일시보다 늦어야 합니다."),
  SALE_END_AFTER_PERFORMANCE(HttpStatus.BAD_REQUEST, "판매 종료 일시는 공연 일시보다 빨라야 합니다."),
  PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다.");

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
