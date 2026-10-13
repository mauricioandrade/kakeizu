package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.time.DateTimeException;
import java.time.YearMonth;

/**
 * 44-digit key of an NF-e/NFC-e. Layout: state (2), year/month AAMM (2), issuer CNPJ (14), model (2),
 * series (3), number (9), emission type (1), random code (8), check digit (1).
 */
public record NfeAccessKey(String value) {

    public static final String NFE_MODEL = "55";
    public static final String NFCE_MODEL = "65";

    public NfeAccessKey {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("Access key is required");
        }
        value = value.replaceAll("\\s", "");
        if (!value.matches("\\d{44}")) {
            throw new InvalidValueException("Access key must have 44 digits, got " + value.length());
        }
        if (checkDigit(value.substring(0, 43)) != value.charAt(43) - '0') {
            throw new InvalidValueException("Access key check digit does not match");
        }
        try {
            yearMonth(value);
        } catch (DateTimeException _) {
            throw new InvalidValueException("Access key has an invalid year/month: " + value.substring(2, 6));
        }
    }

    public YearMonth issuedIn() {
        return yearMonth(value);
    }

    public String issuerCnpj() {
        return value.substring(6, 20);
    }

    public String model() {
        return value.substring(20, 22);
    }

    private static YearMonth yearMonth(String key) {
        return YearMonth.of(2000 + Integer.parseInt(key.substring(2, 4)), Integer.parseInt(key.substring(4, 6)));
    }

    // Mod 11 with weights 2..9 applied from right to left
    private static int checkDigit(String digits) {
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
