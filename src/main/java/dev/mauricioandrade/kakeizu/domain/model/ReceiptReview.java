package dev.mauricioandrade.kakeizu.domain.model;

/**
 * A stored receipt with its fields validated again, for the review screen.
 */
public record ReceiptReview(ReceiptRecord receipt, ExtractionResult result) {
}
