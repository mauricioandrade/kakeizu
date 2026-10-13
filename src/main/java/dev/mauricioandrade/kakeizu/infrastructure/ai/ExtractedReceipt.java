package dev.mauricioandrade.kakeizu.infrastructure.ai;

/**
 * What the model returns, as-is. Nothing here is trusted until the domain validates it.
 */
public record ExtractedReceipt(
        String type,            // "NFE" | "NFCE" | "PIX" | "CARD" | "UNKNOWN"
        String amount,
        String date,            // ISO-8601
        String counterparty,
        String taxId,
        String uniqueId,
        String suggestedCategory) {
}
