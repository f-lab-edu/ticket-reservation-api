package com.ticketing.reservation.global.web;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import java.util.List;
import org.springframework.http.ETag;

public final class IfMatch {

  private final ETag eTag;

  private IfMatch(ETag eTag) {
    this.eTag = eTag;
  }

  public static IfMatch parse(String header) {
    if (header == null) {
      throw new BusinessException(ErrorCode.IF_MATCH_REQUIRED);
    }
    List<ETag> eTags = ETag.parse(header);
    if (eTags.size() != 1 || eTags.get(0).isWildcard()) {
      throw new BusinessException(ErrorCode.IF_MATCH_REQUIRED);
    }
    return new IfMatch(eTags.get(0));
  }

  public static String eTagOf(long version) {
    return eTag(version).formattedTag();
  }

  public boolean matches(long version) {
    return eTag(version).compare(eTag, true);
  }

  private static ETag eTag(long version) {
    return new ETag(String.valueOf(version), false);
  }
}
