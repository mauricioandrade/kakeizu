package dev.mauricioandrade.kakeizu.domain.exception;

/**
 * A value read from a receipt (by the model or typed by a person) is not valid.
 */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String message) {
        super(message);
    }
}
