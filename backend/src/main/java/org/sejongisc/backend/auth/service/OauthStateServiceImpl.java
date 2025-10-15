package org.sejongisc.backend.auth.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OauthStateServiceImpl implements OauthStateService {

    private final String STATE_KEY = "oauth_state";

    @Override
    public String generateAndSaveState(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE_KEY, state);
        return state;
    }

    @Override
    public String getStateFromSession(HttpSession session) {
        Object state = session.getAttribute(STATE_KEY);
        return (state != null) ? state.toString() : null;
    }

    @Override
    public void clearState(HttpSession session) {
        session.removeAttribute(STATE_KEY);
    }
}
