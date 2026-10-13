package dev.mauricioandrade.kakeizu.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What the model returns, as-is. Nothing here is trusted until the domain validates it.
 *
 * <p>Every field is required in the JSON schema LangChain4j sends to Ollama. Ollama enforces the schema as a
 * grammar, and with optional fields the model simply skipped keys: it reasoned "installments = 5" and then
 * left it out of the answer. Required keys force an answer for each field ("null" when not visible).
 */
public record ExtractedReceipt(
        @JsonProperty(required = true) String type,              // "NFE" | "NFCE" | "PIX" | "CARD" | "UNKNOWN"
        @JsonProperty(required = true) String amount,
        @JsonProperty(required = true) String date,              // ISO-8601
        @JsonProperty(required = true) String counterparty,
        @JsonProperty(required = true) String taxId,
        @JsonProperty(required = true) String uniqueId,
        @JsonProperty(required = true) String suggestedCategory,
        @JsonProperty(required = true) String time,              // HH:mm
        @JsonProperty(required = true) String paymentMethod,     // "CASH" | "CREDIT" | "DEBIT" | "PIX" | "OTHER"
        @JsonProperty(required = true) String installments,
        @JsonProperty(required = true) String cardBrand) {
}
