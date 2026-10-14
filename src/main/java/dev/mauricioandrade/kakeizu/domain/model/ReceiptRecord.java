package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptAlreadyReviewedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A stored receipt. While pending, only the text fields exist (they may be invalid); the validated
 * {@link Confirmation} is attached when a person confirms it. {@code recoveries} holds what was found for
 * fields that failed their check digits on reading, kept after review to measure how often it helped.
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
        List<FieldRecovery> recoveries,
        Instant createdAt) {

    public ReceiptRecord {
        recoveries = recoveries == null ? List.of() : List.copyOf(recoveries);
    }

    /**
     * What a person confirmed. Receipts confirmed before payments were tracked have an unknown payment and
     * no direction.
     */
    public record Confirmation(Receipt receipt, Payment payment, Direction direction) {
    }

    public static ReceiptRecord pending(ReceiptFields fields, Category category, String imageSha256,
                                        String imageContentType, String rawModelOutput, Instant createdAt) {
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.PENDING_REVIEW, fields, category, Optional.empty(),
                imageSha256, imageContentType, rawModelOutput, List.of(), createdAt);
    }

    private static final ReceiptFields NOTHING_READ = new ReceiptFields(null, null, null, null, null, null, null,
            null, null, null, null, null);

    /**
     * The photo is saved and waits for the model: nothing is read yet.
     */
    public static ReceiptRecord reading(String imageSha256, String imageContentType, Instant createdAt) {
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.READING, NOTHING_READ, Category.OTHER,
                Optional.empty(), imageSha256, imageContentType, null, List.of(), createdAt);
    }

    /**
     * What the model read goes to review, valid or not, with what was found for fields that failed their
     * check digits.
     */
    public ReceiptRecord read(ModelReading reading, Category category, List<FieldRecovery> recoveries) {
        requireStatus(ReceiptStatus.READING);
        return new ReceiptRecord(id, ReceiptStatus.PENDING_REVIEW, reading.fields(), category, Optional.empty(),
                imageSha256, imageContentType, reading.rawOutput(), recoveries, createdAt);
    }

    /**
     * The model could not read the photo: it goes to review empty, and the person types it from the photo.
     */
    public ReceiptRecord readingFailed() {
        requireStatus(ReceiptStatus.READING);
        return new ReceiptRecord(id, ReceiptStatus.PENDING_REVIEW, NOTHING_READ, Category.OTHER, Optional.empty(),
                imageSha256, imageContentType, null, List.of(), createdAt);
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
                Optional.of(confirmation), imageSha256, imageContentType, rawModelOutput, recoveries, createdAt);
    }

    public ReceiptRecord reject() {
        requirePending();
        return new ReceiptRecord(id, ReceiptStatus.REJECTED, fields, category, Optional.empty(),
                imageSha256, imageContentType, rawModelOutput, recoveries, createdAt);
    }

    /**
     * What was found for the field the review form shows under that name, if it failed its check digits.
     */
    public Optional<FieldRecovery> recoveryOf(String formField) {
        return recoveries.stream().filter(r -> r.field().formField().equals(formField)).findFirst();
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
