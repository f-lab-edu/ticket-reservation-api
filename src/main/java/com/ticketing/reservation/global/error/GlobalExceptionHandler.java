package com.ticketing.reservation.global.error;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String CODE = "code";

  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getDetail());
    problemDetail.setProperty(CODE, errorCode.name());
    return problemDetail;
  }
}
