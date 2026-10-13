package dev.mauricioandrade.kakeizu.domain.exception;

public class ReceiptAlreadyReviewedException extends DomainException {

    public ReceiptAlreadyReviewedException() {
        super("Receipt was already confirmed or rejected");
    }
}
