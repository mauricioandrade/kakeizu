package dev.mauricioandrade.kakeizu.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What the model returns when asked for one field again. Required for the same reason as in
 * {@link ExtractedReceipt}: an optional key gets skipped.
 */
public record RereadField(@JsonProperty(required = true) String value) {
}
