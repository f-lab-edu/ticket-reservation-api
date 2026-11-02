package com.ticketing.reservation.member.email;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

// 허용 문자가 모두 ASCII이므로 길이 제한(바이트)은 문자 수로 비교한다.
public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

  private static final int MAX_EMAIL_LENGTH = 254;
  private static final int MAX_LOCAL_PART_LENGTH = 64;
  private static final int MAX_DOMAIN_LENGTH = 255;
  private static final int MAX_LABEL_LENGTH = 63;

  // 4.1.1.1, 4.1.1.2: 허용 문자 묶음(atom)을 마침표 하나로만 이어 붙여 시작·끝·연속 마침표를 막는다.
  private static final String ATOM = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";
  private static final Pattern LOCAL_PART = Pattern.compile(ATOM + "(\\." + ATOM + ")*+");

  // 4.1.1.3, 4.1.1.4: 영문자·숫자로 시작하고 끝나며, 가운데에만 하이픈을 허용한다.
  private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?");

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    String email = value.strip();
    if (email.length() > MAX_EMAIL_LENGTH) {
      return false;
    }

    int at = email.indexOf('@');
    if (at <= 0 || at != email.lastIndexOf('@')) {
      return false;
    }
    return isValidLocalPart(email.substring(0, at)) && isValidDomain(email.substring(at + 1));
  }

  private boolean isValidLocalPart(String localPart) {
    return localPart.length() <= MAX_LOCAL_PART_LENGTH && LOCAL_PART.matcher(localPart).matches();
  }

  private boolean isValidDomain(String domain) {
    if (domain.length() > MAX_DOMAIN_LENGTH) {
      return false;
    }
    // "a..com", ".com"처럼 비어 있는 레이블도 배열에 남겨 검증한다.
    String[] labels = domain.split("\\.", -1);
    if (labels.length < 2) {
      return false;
    }
    for (String label : labels) {
      if (label.length() > MAX_LABEL_LENGTH || !LABEL.matcher(label).matches()) {
        return false;
      }
    }
    return true;
  }
}
