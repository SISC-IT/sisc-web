package org.sejongisc.backend.user.util;


import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.util.regex.Pattern;

public class PasswordPolicyValidator {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{};:'\",.<>/?]).{8,20}$");

    public static void validate(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
