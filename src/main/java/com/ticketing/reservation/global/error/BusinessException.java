package com.ticketing.reservation.global.error;

public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getDetail());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getDetail(), cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
