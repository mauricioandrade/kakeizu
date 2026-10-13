package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public record Money(BigDecimal value) {

    private static final Pattern PLAIN = Pattern.compile("-?\\d+(\\.\\d+)?");                     // 1234.56
    private static final Pattern BRAZILIAN = Pattern.compile("-?(\\d{1,3}(\\.\\d{3})+|\\d+),\\d+"); // 1.234,56

    public Money {
        if (value == null) {
            throw new InvalidValueException("Amount is required");
        }
        if (value.signum() < 0) {
            throw new InvalidValueException("Amount cannot be negative");
        }
        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException _) {
            throw new InvalidValueException("Amount has more than 2 decimal places: " + value);
        }
    }

    /**
     * Accepts "1234.56", "1.234,56" and "R$ 1.234,56". The string is cleaned before delegating
     * to the canonical constructor (flexible constructor body, JEP 513).
     */
    public Money(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidValueException("Amount is required");
        }
        String cleaned = raw.replace("R$", "").replace(' ', ' ').strip();
        String normalized;
        if (BRAZILIAN.matcher(cleaned).matches()) {
            normalized = cleaned.replace(".", "").replace(',', '.');
        } else if (PLAIN.matcher(cleaned).matches()) {
            normalized = cleaned;
        } else {
            throw new InvalidValueException("Unreadable amount: " + raw);
        }
        this(new BigDecimal(normalized));
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value));
    }
}
