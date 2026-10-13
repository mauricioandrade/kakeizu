package dev.mauricioandrade.kakeizu.infrastructure.web;

/**
 * The uploaded file is missing or not an image we accept. The message is ours and safe to show.
 */
class InvalidUploadException extends RuntimeException {

    InvalidUploadException(String message) {
        super(message);
    }
}
