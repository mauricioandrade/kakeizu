package dev.mauricioandrade.kakeizu.infrastructure.web;

/**
 * A request the user can fix: a missing or unsupported image, a date range backwards. The message is ours and
 * safe to show.
 */
class InvalidInputException extends RuntimeException {

    InvalidInputException(String message) {
        super(message);
    }
}
