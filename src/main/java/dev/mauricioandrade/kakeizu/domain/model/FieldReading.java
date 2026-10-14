package dev.mauricioandrade.kakeizu.domain.model;

/**
 * What the model read when asked for a single field (null if it could not read it), plus its output as JSON.
 */
public record FieldReading(String value, String rawOutput) {
}
