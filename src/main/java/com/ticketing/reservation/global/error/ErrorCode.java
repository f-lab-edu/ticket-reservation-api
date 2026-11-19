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
  INVALID_SALE_END_AT(HttpStatus.BAD_REQUEST, "판매 종료 일시는 현재 시각보다 늦어야 합니다."),
  INVALID_PERFORMANCE_AT(HttpStatus.BAD_REQUEST, "공연 일시는 현재 시각보다 늦어야 합니다."),
  PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
  SCHEDULE_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "예약이 있는 공연은 공연 일시와 판매 시작 일시를 변경할 수 없습니다."),
  PERFORMANCE_ALREADY_STARTED(HttpStatus.CONFLICT, "공연 일시가 지난 공연은 수정할 수 없습니다."),
  TOTAL_QUANTITY_BELOW_RESERVED(HttpStatus.CONFLICT, "총 판매 수량은 예약된 수량보다 적을 수 없습니다."),
  PERFORMANCE_MODIFIED(HttpStatus.PRECONDITION_FAILED, "공연 정보가 변경되었습니다. 최신 정보를 다시 조회한 뒤 수정해 주세요."),
  IF_MATCH_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "If-Match 헤더에 조회 시 받은 ETag 하나를 포함해야 합니다.");

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
