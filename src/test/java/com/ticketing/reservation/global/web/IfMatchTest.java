package com.ticketing.reservation.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IfMatchTest {

  @Test
  @DisplayName("eTagOf로 만든 ETag를 If-Match로 보내면 같은 version과만 일치한다")
  void matchesETagCreatedFromVersion() {
    // when
    IfMatch ifMatch = IfMatch.parse(IfMatch.eTagOf(3L));

    // then
    assertThat(ifMatch.matches(3L)).isTrue();
    assertThat(ifMatch.matches(4L)).isFalse();
  }

  @Test
  @DisplayName("약한 ETag는 일치하지 않는다")
  void doesNotMatchWeakETag() {
    // when
    IfMatch ifMatch = IfMatch.parse("W/\"3\"");

    // then
    assertThat(ifMatch.matches(3L)).isFalse();
  }

  @ParameterizedTest(name = "If-Match: [{0}]")
  @DisplayName("형식에 맞는 ETag가 정확히 하나가 아니면 거절한다")
  @NullSource
  @ValueSource(strings = {"", " ", "*", "\"2\", \"3\"", "\"3\", *", "3"})
  void rejectsUnlessSingleETag(String header) {
    // when
    Throwable thrown = catchThrowable(() -> IfMatch.parse(header));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IF_MATCH_REQUIRED));
  }
}
