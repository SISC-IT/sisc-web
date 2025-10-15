package org.sejongisc.backend.auth.service;

import jakarta.servlet.http.HttpSession;

public interface OauthStateService {
    String generateAndSaveState(HttpSession session);

    String getStateFromSession(HttpSession session);

    void clearState(HttpSession session);
}
