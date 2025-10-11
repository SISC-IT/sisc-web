package org.sejongisc.backend.auth.oauth;

import org.sejongisc.backend.auth.entity.AuthProvider;

public interface OauthUserInfo {
    String getProviderUid();
    // String getEamil();   // kakao email 승인 필요
    String getName();
    AuthProvider getProvider();
}
