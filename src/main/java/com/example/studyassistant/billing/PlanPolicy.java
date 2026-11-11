package com.example.studyassistant.billing;

import java.util.*;
public final class PlanPolicy {
    private PlanPolicy() {}
    public static String normalize(String plan) {
        if (plan == null) return "FREE";
        return switch (plan.trim().toUpperCase(Locale.ROOT)) {
            case "BASIC", "STUDENT", "PRO" -> "PRO";
            case "PREMIUM", "VIP" -> "VIP";
            default -> "FREE";
        };
    }
    public static int limit(String plan, String feature) {
        plan=normalize(plan);
        if (plan.equals("VIP")) return -1;
        boolean pro=plan.equals("PRO");
        return switch(feature) {
            case "UPLOAD" -> pro ? 50 : 3;
            case "CHAT" -> pro ? 500 : 20;
            case "QUIZ", "FLASHCARD", "SUMMARY", "MIND_MAP" -> pro ? 100 : 3;
            case "OUTLINE" -> pro ? 20 : 1;
            case "SLIDES" -> pro ? 200 : 10;
            default -> throw new IllegalArgumentException("Unknown quota feature");
        };
    }
    public static long price(String plan, int months) {
        if (!Set.of(1,3,12).contains(months)) throw new IllegalArgumentException("Chỉ hỗ trợ 1, 3 hoặc 12 tháng.");
        plan=normalize(plan);
        if (plan.equals("FREE")) throw new IllegalArgumentException("Gói FREE không cần thanh toán.");
        long monthly=plan.equals("VIP") ? 100000 : 50000;
        return (monthly-(months==3 ? 10000 : months==12 ? 20000 : 0))*months;
    }
    public static String upgrade(String plan) { return normalize(plan).equals("FREE") ? "PRO" : "VIP"; }
}
