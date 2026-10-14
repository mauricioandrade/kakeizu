package dev.mauricioandrade.kakeizu.domain.model;

/**
 * A field that failed its check digits on reading, and what was found for it. The fields themselves are never
 * changed: a suggestion only shows on review, for a person to take or leave.
 *
 * @param original     what the model read first
 * @param rereadOutput the model output when asked for this field alone; null if it was not asked or did not answer
 */
public record FieldRecovery(CheckDigitField field, String original, RecoveryResult result, String rereadOutput) {
}
