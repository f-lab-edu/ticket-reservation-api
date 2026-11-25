package com.ticketing.reservation.global.web;

import java.beans.PropertyEditorSupport;
import java.util.regex.Pattern;

public class StrictLongEditor extends PropertyEditorSupport {

  private static final Pattern INTEGER = Pattern.compile("-?\\d+");

  @Override
  public void setAsText(String text) {
    if (!INTEGER.matcher(text).matches()) {
      throw new IllegalArgumentException("정수 형식이 아닙니다. value=" + text);
    }
    setValue(Long.valueOf(text));
  }
}
