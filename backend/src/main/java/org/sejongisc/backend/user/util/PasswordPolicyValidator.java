package org.sejongisc.backend.user.util;


import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.util.regex.Pattern;

public class PasswordPolicyValidator {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{};:'\",.<>/?]).{8,20}$");

    public static String getValidatedPassword(String password) {
        String trimmed = sanitize(password);

        if (!PASSWORD_PATTERN.matcher(trimmed).matches()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return trimmed;
    }

    private static String sanitize(String password) {
        if (password == null || password.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return password.trim();
    }
}
