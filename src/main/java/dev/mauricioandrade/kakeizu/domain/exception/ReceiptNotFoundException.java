package dev.mauricioandrade.kakeizu.domain.exception;

public class ReceiptNotFoundException extends DomainException {

    public ReceiptNotFoundException() {
        super("Receipt not found");
    }
}
