package dev.mauricioandrade.kakeizu.domain.exception;

/**
 * The model could not be reached or returned something unusable.
 */
public class ReceiptReadingException extends DomainException {

    public ReceiptReadingException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
