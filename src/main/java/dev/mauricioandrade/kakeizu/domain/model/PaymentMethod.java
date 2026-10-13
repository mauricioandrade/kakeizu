package dev.mauricioandrade.kakeizu.domain.model;

public enum PaymentMethod {
    CASH, CREDIT, DEBIT, PIX, CHEQUE, OTHER;

    public boolean isCard() {
        return this == CREDIT || this == DEBIT;
    }
}
