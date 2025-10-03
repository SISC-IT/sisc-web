package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.GithubTokenResponse;
import org.sejongisc.backend.user.dto.GithubUserInfoResponse;

public interface GithubService {
    GithubTokenResponse getAccessTokenFromGithub(String code);
    GithubUserInfoResponse getUserInfo(String accessToken);
}
