package com.ticketing.reservation.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class StrictLocalDateTimeModuleTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("숫자 배열 형식의 LocalDateTime은 거절한다")
  void rejectsNumericArray() {
    // when
    Throwable thrown =
        catchThrowable(
            () -> objectMapper.readValue("{\"at\":[2026,12,20,19,0,0,500000000]}", Plain.class));

    // then
    assertThat(thrown).isInstanceOf(MismatchedInputException.class);
  }

  @Test
  @DisplayName("필드의 @JsonFormat 설정을 기본 deserializer에 전달한다")
  void passesJsonFormatToDefaultDeserializer() throws Exception {
    // when
    Formatted result = objectMapper.readValue("{\"at\":\"2026-12-20T19:00:00\"}", Formatted.class);
    Throwable thrown =
        catchThrowable(
            () -> objectMapper.readValue("{\"at\":\"2026-12-20T19:00:00.5\"}", Formatted.class));

    // then
    assertThat(result.at()).isEqualTo(LocalDateTime.of(2026, 12, 20, 19, 0, 0));
    assertThat(thrown).isInstanceOf(InvalidFormatException.class);
  }

  @Test
  @DisplayName("존재하지 않는 날짜의 LocalDateTime은 거절한다")
  void rejectsNonExistentDate() {
    // when
    Throwable thrown =
        catchThrowable(
            () -> objectMapper.readValue("{\"at\":\"2026-02-30T10:00:00\"}", Formatted.class));

    // then
    assertThat(thrown).isInstanceOf(InvalidFormatException.class);
  }

  private record Plain(LocalDateTime at) {}

  private record Formatted(@JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss") LocalDateTime at) {}
}
