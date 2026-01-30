package org.sejongisc.backend.auth.service.oauth2.exception;

public class OauthUnlinkException extends RuntimeException {
    public OauthUnlinkException(String message) {
        super(message);
    }

    public OauthUnlinkException(String message, Throwable cause) {
        super(message, cause);
    }
}
