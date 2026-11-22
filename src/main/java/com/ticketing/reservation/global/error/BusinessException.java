package com.ticketing.reservation.global.error;

import java.util.Map;

public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final transient Map<String, Object> properties;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, Map.of());
  }

  public BusinessException(ErrorCode errorCode, Map<String, Object> properties) {
    super(errorCode.getDetail());
    this.errorCode = errorCode;
    this.properties = Map.copyOf(properties);
  }

  public BusinessException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getDetail(), cause);
    this.errorCode = errorCode;
    this.properties = Map.of();
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }
}
