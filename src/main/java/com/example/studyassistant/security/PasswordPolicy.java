package com.example.studyassistant.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static final String MESSAGE = "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt; tối đa 72 byte UTF-8.";
    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.codePoints().anyMatch(Character::isUpperCase)
                || !password.codePoints().anyMatch(Character::isLowerCase)
                || !password.codePoints().anyMatch(Character::isDigit)
                || !password.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
