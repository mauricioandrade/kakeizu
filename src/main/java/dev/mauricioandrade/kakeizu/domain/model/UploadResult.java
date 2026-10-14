package dev.mauricioandrade.kakeizu.domain.model;

public sealed interface UploadResult {

    ReceiptRecord receipt();

    /**
     * The photo is saved and waits for the model (status READING).
     */
    record Queued(ReceiptRecord receipt) implements UploadResult {
    }

    /**
     * The same image was uploaded before: no new extraction, the existing receipt is returned.
     */
    record AlreadyUploaded(ReceiptRecord receipt) implements UploadResult {
    }
}
