package com.ticketing.reservation.global.web;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
public class StrictLongBinding {

  @InitBinder
  public void registerStrictLongEditor(WebDataBinder binder) {
    binder.registerCustomEditor(Long.class, new StrictLongEditor());
  }
}
