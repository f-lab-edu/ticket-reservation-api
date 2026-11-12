package com.ticketing.reservation.performance.domain;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

@Embeddable
public record SalePeriod(LocalDateTime saleStartAt, LocalDateTime saleEndAt) {

  public SalePeriod {
    if (!saleEndAt.isAfter(saleStartAt)) {
      throw new BusinessException(ErrorCode.INVALID_SALE_PERIOD);
    }
  }
}
