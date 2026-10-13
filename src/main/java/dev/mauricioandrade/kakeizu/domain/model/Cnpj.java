package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;

/**
 * Brazilian company tax ID: 12 base characters + 2 numeric check digits (mod 11).
 * Since July 2026 the base may be alphanumeric (IN RFB 2.229/2024); each character counts as its ASCII code - 48,
 * which keeps the old all-digit CNPJs valid.
 */
public record Cnpj(String value) {

    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("CNPJ is required");
        }
        value = value.replaceAll("[.\\-/\\s]", "").toUpperCase();
        if (!value.matches("[A-Z0-9]{12}\\d{2}")) {
            throw new InvalidValueException("CNPJ must have 14 characters (12 letters or digits + 2 check digits), got: " + value);
        }
        if (value.chars().distinct().count() == 1) {
            throw new InvalidValueException("CNPJ cannot have all characters equal");
        }
        if (checkDigit(value, FIRST_WEIGHTS) != valueAt(value, 12)
                || checkDigit(value, SECOND_WEIGHTS) != valueAt(value, 13)) {
            throw new InvalidValueException("CNPJ check digits do not match: " + value);
        }
    }

    private static int checkDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += valueAt(cnpj, i) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    // ASCII - 48: '0'..'9' -> 0..9, 'A'..'Z' -> 17..42
    private static int valueAt(String cnpj, int index) {
        return cnpj.charAt(index) - '0';
    }
}
