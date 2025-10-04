package org.sejongisc.backend.user.oauth;

import org.sejongisc.backend.user.entity.AuthProvider;

public interface OauthUserInfo {
    String getProviderUid();
    // String getEamil();   // kakao email 승인 필요
    String getName();
    AuthProvider getProvider();
}
