package dev.mauricioandrade.kakeizu.domain.model;

public enum ReceiptStatus {
    /** Photo saved, the model has not finished reading it yet. */
    READING,
    PENDING_REVIEW, CONFIRMED, REJECTED
}
