package org.sejongisc.backend.attendance.util;

import java.util.UUID;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

public class AuthUserUtil {
  private AuthUserUtil() {}

  public static UUID requireUserId(CustomUserDetails userDetails) {
    if (userDetails == null)
      throw new CustomException(ErrorCode.UNAUTHENTICATED);
    return userDetails.getUserId();
  }

}
