package dev.mauricioandrade.kakeizu.domain.exception;

public class DuplicateReceiptException extends DomainException {

    private DuplicateReceiptException(String message) {
        super(message);
    }

    public static DuplicateReceiptException sameImage() {
        return new DuplicateReceiptException("This image was already uploaded");
    }

    public static DuplicateReceiptException sameUniqueId() {
        return new DuplicateReceiptException("A receipt with this identifier was already confirmed");
    }
}
