package dev.mauricioandrade.kakeizu.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * The fields as read, the receipt built from them (absent if any field has an error) and every issue found.
 */
public record ExtractionResult(ReceiptFields fields, Optional<Receipt> receipt, Category category,
                               List<ValidationIssue> issues) {

    public ExtractionResult {
        issues = List.copyOf(issues);
    }

    public boolean isValid() {
        return issues.stream().noneMatch(ValidationIssue::isError);
    }
}
