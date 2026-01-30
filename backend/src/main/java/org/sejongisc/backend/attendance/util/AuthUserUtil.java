package org.sejongisc.backend.attendance.util;

import java.util.UUID;

import org.sejongisc.backend.common.auth.dto.CustomUserDetails;

public class AuthUserUtil {
  private AuthUserUtil() {}

  public static UUID requireUserId(CustomUserDetails userDetails) {
    if (userDetails == null) throw new IllegalStateException("UNAUTHENTICATED");
    return userDetails.getUserId();
  }

}
