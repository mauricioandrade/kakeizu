package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;

/**
 * The magnetic line printed at the bottom of a cheque (CMC-7): 30 digits in three groups, each protected by a
 * module 10 check digit. Separators such as {@code <}, {@code >} and spaces are dropped.
 * <ul>
 *     <li>group 1 (8): bank, agency, check digit of group 2</li>
 *     <li>group 2 (10): clearing house, cheque number, cheque kind</li>
 *     <li>group 3 (12): check digit of bank and agency, account, check digit of the account</li>
 * </ul>
 */
public record Cmc7(String value) {

    private static final int LENGTH = 30;

    public Cmc7 {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("CMC-7 line is required");
        }
        value = value.replaceAll("[\\s<>:;]", "");
        if (!value.matches("\\d+")) {
            throw new InvalidValueException("CMC-7 line must have only digits");
        }
        if (value.length() != LENGTH) {
            throw new InvalidValueException("CMC-7 line must have " + LENGTH + " digits, got " + value.length());
        }
        boolean bankAndAgencyMatch = checkDigit(value.substring(0, 7)) == digit(value, 18);
        boolean chequeMatches = checkDigit(value.substring(8, 18)) == digit(value, 7);
        boolean accountMatches = checkDigit(value.substring(19, 29)) == digit(value, 29);
        if (!bankAndAgencyMatch || !chequeMatches || !accountMatches) {
            throw new InvalidValueException("CMC-7 check digit does not match");
        }
    }

    // Module 10: weights 2 and 1 from the right, adding the digits of each product
    private static int checkDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int product = digit(digits, digits.length() - 1 - i) * (i % 2 == 0 ? 2 : 1);
            sum += product / 10 + product % 10;
        }
        return (10 - sum % 10) % 10;
    }

    private static int digit(String digits, int index) {
        return digits.charAt(index) - '0';
    }
}
