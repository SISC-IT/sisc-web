package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.GoogleTokenResponse;
import org.sejongisc.backend.user.dto.GoogleUserInfoResponse;

public interface GoogleService {
    GoogleTokenResponse getAccessTokenFromGoogle(String code);
    GoogleUserInfoResponse getUserInfo(String accessToken);
}
