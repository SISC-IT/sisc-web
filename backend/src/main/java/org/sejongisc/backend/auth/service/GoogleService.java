package org.sejongisc.backend.auth.service;

import org.sejongisc.backend.auth.dto.GoogleTokenResponse;
import org.sejongisc.backend.auth.dto.GoogleUserInfoResponse;

public interface GoogleService {
    GoogleTokenResponse getAccessTokenFromGoogle(String code);
    GoogleUserInfoResponse getUserInfo(String accessToken);
}
