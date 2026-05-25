package org.sejongisc.backend.assetmanagement.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("assetManagementAccountAuthorization")
@RequiredArgsConstructor
public class AssetManagementAccountAuthorization {
  @Value("${asset-management.account-viewer.team-name:자산운용}")
  private String assetManagementTeamName;

  @Value("${asset-management.account-viewer.allow-system-admin:false}")
  private boolean allowSystemAdmin;

  public boolean canView(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof CustomUserDetails user)) {
      return false;
    }

    if (user.getStatus() != UserStatus.ACTIVE || user.getRole() == Role.PENDING_MEMBER) {
      return false;
    }

    if (allowSystemAdmin && user.getRole() == Role.SYSTEM_ADMIN) {
      return true;
    }

    return normalize(user.getTeamName()).equals(normalize(assetManagementTeamName));
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }

    return value.replaceAll("\\s+", "").trim();
  }
}
