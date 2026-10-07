package com.streamx.billing.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kenyan mobile number helpers for M-Pesa (format 2547XXXXXXXX / 2541XXXXXXXX).
 */
public final class PhoneNumbers {

    private static final Pattern LOCAL = Pattern.compile("^0([17]\\d{8})$");
    private static final Pattern INTERNATIONAL = Pattern.compile("^\\+?254([17]\\d{8})$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

    private PhoneNumbers() {
    }

    /**
     * Accepts 07…, 01…, +2547…, +2541…, 2547…, 2541… (spaces, dashes, dots and parentheses ignored).
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = SEPARATORS.matcher(raw.trim()).replaceAll("");
        Matcher local = LOCAL.matcher(compact);
        if (local.matches()) {
            return Optional.of("254" + local.group(1));
        }
        Matcher international = INTERNATIONAL.matcher(compact);
        if (international.matches()) {
            return Optional.of("254" + international.group(1));
        }
        return Optional.empty();
    }

    /**
     * 254712345678 → 2547****5678.
     */
    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        if (phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 4);
    }
}
