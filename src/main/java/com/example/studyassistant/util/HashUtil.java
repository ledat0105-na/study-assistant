package com.example.studyassistant.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashUtil {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String hash(String input) {
        return encoder.encode(input);
    }

    public static boolean verify(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}
