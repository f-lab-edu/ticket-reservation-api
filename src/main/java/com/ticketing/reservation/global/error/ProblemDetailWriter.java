package com.ticketing.reservation.global.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

public class ProblemDetailWriter {

  private static final String CODE = "code";

  private final ObjectMapper objectMapper;

  public ProblemDetailWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
      throws IOException {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getDetail());
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty(CODE, errorCode.name());

    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
