package org.sejongisc.backend.auth.service;

import org.sejongisc.backend.auth.dto.GithubTokenResponse;
import org.sejongisc.backend.auth.dto.GithubUserInfoResponse;

public interface GithubService {
    GithubTokenResponse getAccessTokenFromGithub(String code);
    GithubUserInfoResponse getUserInfo(String accessToken);
}
