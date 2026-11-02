package com.ticketing.reservation.member.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

  private static final int MAX_PASSWORD_BYTES = 72;

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return value.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
  }
}
