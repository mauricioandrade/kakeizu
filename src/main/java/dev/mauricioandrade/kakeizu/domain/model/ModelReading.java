package dev.mauricioandrade.kakeizu.domain.model;

/**
 * What the model read, plus its output as JSON for debugging.
 */
public record ModelReading(ReceiptFields fields, String rawOutput) {
}
