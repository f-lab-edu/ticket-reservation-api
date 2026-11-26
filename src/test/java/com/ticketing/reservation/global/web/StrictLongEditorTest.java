package com.ticketing.reservation.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrictLongEditorTest {

  private final StrictLongEditor editor = new StrictLongEditor();

  @ParameterizedTest(name = "\"{0}\"")
  @DisplayName("정수 형식이면 Long으로 바꾼다")
  @ValueSource(strings = {"10", "0", "-1", "9223372036854775807"})
  void convertsInteger(String text) {
    // when
    editor.setAsText(text);

    // then
    assertThat(editor.getValue()).isEqualTo(Long.parseLong(text));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @DisplayName("정수 형식이 아니면 거절한다")
  @ValueSource(
      strings = {
        "",
        " ",
        " 10",
        "10 ",
        "1 0",
        "\t10",
        "+10",
        "10.0",
        "1e3",
        "abc",
        "9223372036854775808"
      })
  void rejectsNonInteger(String text) {
    // when
    Throwable thrown = catchThrowable(() -> editor.setAsText(text));

    // then
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }
}
