package dev.mauricioandrade.kakeizu.domain.model;

/**
 * Receipt data as plain text, before validation. Comes from the model on extraction and from the
 * review form on confirmation, so both go through the same checks.
 */
public record ReceiptFields(
        String type,
        String amount,
        String date,
        String counterparty,
        String taxId,
        String uniqueId,
        String category) {
}
