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
        if (status != ReceiptStatus.PENDING_REVIEW) {
            throw new ReceiptAlreadyReviewedException();
        }
    }
}
