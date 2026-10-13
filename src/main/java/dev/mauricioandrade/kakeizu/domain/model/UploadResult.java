package dev.mauricioandrade.kakeizu.domain.model;

public sealed interface UploadResult {

    ReceiptRecord receipt();

    record Extracted(ReceiptRecord receipt) implements UploadResult {
    }

    /**
     * The same image was uploaded before: no new extraction, the existing receipt is returned.
     */
    record AlreadyUploaded(ReceiptRecord receipt) implements UploadResult {
    }
}
