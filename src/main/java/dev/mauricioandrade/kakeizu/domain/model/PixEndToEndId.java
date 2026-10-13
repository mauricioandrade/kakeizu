package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;

/**
 * Pix transaction ID: "E" + 31 alphanumeric characters.
 */
public record PixEndToEndId(String value) {

    private static final int LENGTH = 32;

    public PixEndToEndId {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("Pix transaction ID is required");
        }
        value = value.strip();
        if (value.length() != LENGTH) {
            throw new InvalidValueException("Pix transaction ID must have " + LENGTH + " characters, got " + value.length());
        }
        if (!value.matches("E[A-Za-z0-9]{31}")) {
            throw new InvalidValueException("Pix transaction ID must start with E and be alphanumeric");
        }
    }
}
