package com.ticketing.reservation.member.password;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidPassword {

  String message() default "비밀번호는 72바이트를 초과할 수 없습니다.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
