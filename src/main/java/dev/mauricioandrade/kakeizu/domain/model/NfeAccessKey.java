package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.time.DateTimeException;
import java.time.YearMonth;

/**
 * 44-character key of an NF-e/NFC-e. Layout: state (2), year/month AAMM (4), issuer CNPJ (14), model (2),
 * series (3), number (9), emission type (1), random code (8), check digit (1).
 * Only the CNPJ part may be alphanumeric (NT 2025.001); characters count as ASCII - 48, like in {@link Cnpj}.
 */
public record NfeAccessKey(String value) {

    public static final String NFE_MODEL = "55";
    public static final String NFCE_MODEL = "65";

    public NfeAccessKey {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("Access key is required");
        }
        value = value.replaceAll("[.\\-/\\s]", "").toUpperCase();
        if (value.length() != 44) {
            throw new InvalidValueException("Access key must have 44 characters, got " + value.length());
        }
        if (!value.matches("\\d{6}[A-Z0-9]{14}\\d{24}")) {
            throw new InvalidValueException("Access key must be digits, except for the CNPJ part");
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
    private static int checkDigit(String key) {
        int sum = 0;
        int weight = 2;
        for (int i = key.length() - 1; i >= 0; i--) {
            sum += (key.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
