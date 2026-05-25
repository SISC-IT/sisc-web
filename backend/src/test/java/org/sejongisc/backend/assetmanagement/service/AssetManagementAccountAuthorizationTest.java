package org.sejongisc.backend.assetmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class AssetManagementAccountAuthorizationTest {
  private AssetManagementAccountAuthorization authorization;

  @BeforeEach
  void setUp() {
    authorization = new AssetManagementAccountAuthorization();
    ReflectionTestUtils.setField(authorization, "assetManagementTeamName", "자산운용팀");
    ReflectionTestUtils.setField(authorization, "allowSystemAdmin", false);
  }

  @Test
  void grantsActiveAssetManagementTeamMember() {
    Authentication authentication = authentication(Role.TEAM_MEMBER, "자산 운용팀", UserStatus.ACTIVE);

    assertThat(authorization.canView(authentication)).isTrue();
  }

  @Test
  void deniesPresidentOutsideAssetManagementTeam() {
    Authentication authentication = authentication(Role.PRESIDENT, "리서치팀", UserStatus.ACTIVE);

    assertThat(authorization.canView(authentication)).isFalse();
  }

  @Test
  void deniesPendingMemberEvenIfAssetManagementTeam() {
    Authentication authentication = authentication(Role.PENDING_MEMBER, "자산운용팀", UserStatus.ACTIVE);

    assertThat(authorization.canView(authentication)).isFalse();
  }

  @Test
  void systemAdminRequiresExplicitBreakGlassOption() {
    Authentication authentication = authentication(Role.SYSTEM_ADMIN, "개발팀", UserStatus.ACTIVE);

    assertThat(authorization.canView(authentication)).isFalse();

    ReflectionTestUtils.setField(authorization, "allowSystemAdmin", true);

    assertThat(authorization.canView(authentication)).isTrue();
  }

  private Authentication authentication(Role role, String teamName, UserStatus status) {
    User user = User.builder()
        .userId(UUID.randomUUID())
        .studentId(UUID.randomUUID().toString())
        .name("테스트")
        .email("test@example.com")
        .role(role)
        .teamName(teamName)
        .status(status)
        .point(0)
        .build();
    CustomUserDetails principal = new CustomUserDetails(user);

    return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
  }
}
