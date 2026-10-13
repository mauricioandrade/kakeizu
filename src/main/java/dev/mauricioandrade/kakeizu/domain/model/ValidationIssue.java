package dev.mauricioandrade.kakeizu.domain.model;

/**
 * A problem found in a receipt field. Errors block confirmation; warnings are shown on review only.
 */
public record ValidationIssue(String field, Severity severity, String message) {

    public enum Severity {
        ERROR, WARNING
    }

    public static ValidationIssue error(String field, String message) {
        return new ValidationIssue(field, Severity.ERROR, message);
    }

    public static ValidationIssue warning(String field, String message) {
        return new ValidationIssue(field, Severity.WARNING, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
