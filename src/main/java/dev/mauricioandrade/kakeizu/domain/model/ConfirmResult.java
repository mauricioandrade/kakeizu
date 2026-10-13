package dev.mauricioandrade.kakeizu.domain.model;

public sealed interface ConfirmResult {

    record Confirmed(ReceiptRecord receipt) implements ConfirmResult {
    }

    /**
     * The corrected fields still have errors; nothing was saved.
     */
    record Invalid(ExtractionResult result) implements ConfirmResult {
    }
}
