package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;

/**
 * Brazilian company tax ID: 12 base digits + 2 check digits (mod 11).
 */
public record Cnpj(String value) {

    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("CNPJ is required");
        }
        value = value.replaceAll("[.\\-/\\s]", "");
        if (!value.matches("\\d{14}")) {
            throw new InvalidValueException("CNPJ must have 14 digits, got: " + value);
        }
        if (value.chars().distinct().count() == 1) {
            throw new InvalidValueException("CNPJ cannot have all digits equal");
        }
        if (checkDigit(value, FIRST_WEIGHTS) != digitAt(value, 12)
                || checkDigit(value, SECOND_WEIGHTS) != digitAt(value, 13)) {
            throw new InvalidValueException("CNPJ check digits do not match: " + value);
        }
    }

    private static int checkDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digitAt(digits, i) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static int digitAt(String digits, int index) {
        return digits.charAt(index) - '0';
    }
}
