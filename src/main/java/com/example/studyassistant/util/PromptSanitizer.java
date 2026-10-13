package com.example.studyassistant.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptSanitizer {
    
    private static final Pattern DELIMITER_PATTERN = Pattern.compile(
            "===\\s*(END\\s+)?(SOURCE DATA|KNOWLEDGE GRAPH CONTEXT)\\s*===", 
            Pattern.CASE_INSENSITIVE
    );

    public static String sanitizeUntrustedPromptText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        
        Matcher matcher = DELIMITER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String endPart = matcher.group(1) != null ? matcher.group(1) : "";
            String mainPart = matcher.group(2) != null ? matcher.group(2) : "";
            String replacement = "[untrusted marker: " + (endPart + mainPart).trim() + "]";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
