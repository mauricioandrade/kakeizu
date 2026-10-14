package dev.mauricioandrade.kakeizu.domain.exception;

public class ReceiptAlreadyReviewedException extends DomainException {

    public ReceiptAlreadyReviewedException() {
        super("Receipt is still being read, or was already confirmed or rejected");
    }
}
