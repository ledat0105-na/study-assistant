package com.example.studyassistant.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashUtil {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String hash(String input) {
        return encoder.encode(input);
    }

    public static boolean verify(String rawPassword, String hashedPassword) {
        // Try BCrypt match first
        try {
            if (encoder.matches(rawPassword, hashedPassword)) {
                return true;
            }
        } catch (Exception e) {
            // Not a valid BCrypt hash
        }
        // Fallback to raw string comparison for legacy/mock data
        return rawPassword.equals(hashedPassword);
    }
}
