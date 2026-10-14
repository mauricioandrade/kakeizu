package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptAlreadyReviewedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A stored receipt. While pending, only the text fields exist (they may be invalid); the validated
 * {@link Confirmation} is attached when a person confirms it.
 */
public record ReceiptRecord(
        UUID id,
        ReceiptStatus status,
        ReceiptFields fields,
        Category category,
        Optional<Confirmation> confirmation,
        String imageSha256,
        String imageContentType,
        String rawModelOutput,
        Instant createdAt) {

    /**
     * What a person confirmed. Receipts confirmed before payments were tracked have an unknown payment and
     * no direction.
     */
    public record Confirmation(Receipt receipt, Payment payment, Direction direction) {
    }

    public static ReceiptRecord pending(ReceiptFields fields, Category category, String imageSha256,
                                        String imageContentType, String rawModelOutput, Instant createdAt) {
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.PENDING_REVIEW, fields, category, Optional.empty(),
                imageSha256, imageContentType, rawModelOutput, createdAt);
    }

    private static final ReceiptFields NOTHING_READ = new ReceiptFields(null, null, null, null, null, null, null,
            null, null, null, null, null);

    /**
     * The photo is saved and waits for the model: nothing is read yet.
     */
    public static ReceiptRecord reading(String imageSha256, String imageContentType, Instant createdAt) {
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.READING, NOTHING_READ, Category.OTHER,
                Optional.empty(), imageSha256, imageContentType, null, createdAt);
    }

    /**
     * What the model read goes to review, valid or not.
     */
    public ReceiptRecord read(ModelReading reading, Category category) {
        requireStatus(ReceiptStatus.READING);
        return new ReceiptRecord(id, ReceiptStatus.PENDING_REVIEW, reading.fields(), category, Optional.empty(),
                imageSha256, imageContentType, reading.rawOutput(), createdAt);
    }

    /**
     * The model could not read the photo: it goes to review empty, and the person types it from the photo.
     */
    public ReceiptRecord readingFailed() {
        requireStatus(ReceiptStatus.READING);
        return new ReceiptRecord(id, ReceiptStatus.PENDING_REVIEW, NOTHING_READ, Category.OTHER, Optional.empty(),
                imageSha256, imageContentType, null, createdAt);
    }

    /**
     * Pending, but the model gave nothing to review.
     */
    public boolean modelFailed() {
        return status == ReceiptStatus.PENDING_REVIEW && rawModelOutput == null;
    }

    /**
     * @param result a valid extraction of the corrected fields
     */
    public ReceiptRecord confirm(ExtractionResult result) {
        requirePending();
        if (!result.isValid()) {
            throw new IllegalArgumentException("Only valid fields can be confirmed");
        }
        var confirmation = new Confirmation(result.receipt().orElseThrow(), result.payment().orElseThrow(),
                result.direction());
        return new ReceiptRecord(id, ReceiptStatus.CONFIRMED, result.fields(), result.category(),
                Optional.of(confirmation), imageSha256, imageContentType, rawModelOutput, createdAt);
    }

    public ReceiptRecord reject() {
        requirePending();
        return new ReceiptRecord(id, ReceiptStatus.REJECTED, fields, category, Optional.empty(),
                imageSha256, imageContentType, rawModelOutput, createdAt);
    }

    public Optional<Receipt> confirmedReceipt() {
        return confirmation.map(Confirmation::receipt);
    }

    private void requirePending() {
        requireStatus(ReceiptStatus.PENDING_REVIEW);
    }

    private void requireStatus(ReceiptStatus expected) {
        if (status != expected) {
            throw new ReceiptAlreadyReviewedException();
        }
    }
}
