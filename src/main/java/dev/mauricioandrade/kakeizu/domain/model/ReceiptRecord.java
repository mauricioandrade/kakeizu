package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptAlreadyReviewedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A stored receipt. While pending, only the text fields exist (they may be invalid); the validated
 * {@link Receipt} is attached when a person confirms it.
 */
public record ReceiptRecord(
        UUID id,
        ReceiptStatus status,
        ReceiptFields fields,
        Category category,
        Optional<Receipt> confirmedReceipt,
        String imageSha256,
        String imageContentType,
        String rawModelOutput,
        Instant createdAt) {

    public static ReceiptRecord pending(ReceiptFields fields, Category category, String imageSha256,
                                        String imageContentType, String rawModelOutput, Instant createdAt) {
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.PENDING_REVIEW, fields, category, Optional.empty(),
                imageSha256, imageContentType, rawModelOutput, createdAt);
    }

    public ReceiptRecord confirm(ReceiptFields correctedFields, Receipt receipt, Category chosenCategory) {
        requirePending();
        return new ReceiptRecord(id, ReceiptStatus.CONFIRMED, correctedFields, chosenCategory, Optional.of(receipt),
                imageSha256, imageContentType, rawModelOutput, createdAt);
    }

    public ReceiptRecord reject() {
        requirePending();
        return new ReceiptRecord(id, ReceiptStatus.REJECTED, fields, category, Optional.empty(),
                imageSha256, imageContentType, rawModelOutput, createdAt);
    }

    private void requirePending() {
        if (status != ReceiptStatus.PENDING_REVIEW) {
            throw new ReceiptAlreadyReviewedException();
        }
    }
}
