package dev.mauricioandrade.kakeizu.domain.model;

/**
 * Receipt data as plain text, before validation. Comes from the model on extraction and from the
 * review form on confirmation, so both go through the same checks. The model never fills
 * {@code direction}: it is inferred from the shop CNPJ, or chosen by a person.
 */
public record ReceiptFields(
        String type,
        String amount,
        String date,
        String counterparty,
        String taxId,
        String uniqueId,
        String category,
        String time,
        String paymentMethod,
        String installments,
        String cardBrand,
        String direction) {
}
