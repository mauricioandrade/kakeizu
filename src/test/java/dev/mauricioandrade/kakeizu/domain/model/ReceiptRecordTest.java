package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptAlreadyReviewedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReceiptRecordTest {

    static final ReceiptFields FIELDS = new ReceiptFields("PIX", "80,00", "2022-08-12", "Pagsmile", null,
            "E00416968202208120422Mmzi9rPYPLT", "OTHER", null, null, null, null, null);
    static final Receipt PIX = new PixReceipt(new Money("80"), LocalDate.of(2022, 8, 12), "Pagsmile",
            new PixEndToEndId("E00416968202208120422Mmzi9rPYPLT"));

    static final Payment PAID_BY_PIX = new Payment(LocalTime.of(4, 22), PaymentMethod.PIX, 1, null);

    final ReceiptRecord pending = ReceiptRecord.pending(FIELDS, Category.OTHER, "a".repeat(64), "image/png", "{}",
            Instant.parse("2026-10-13T12:00:00Z"));

    @Test
    void startsPendingWithoutAReceipt() {
        assertEquals(ReceiptStatus.PENDING_REVIEW, pending.status());
        assertTrue(pending.confirmedReceipt().isEmpty());
    }

    @Test
    void confirmAttachesTheReceiptPaymentDirectionAndTheChosenCategory() {
        var confirmed = pending.confirm(valid(Category.SERVICES));

        assertEquals(ReceiptStatus.CONFIRMED, confirmed.status());
        assertEquals(new ReceiptRecord.Confirmation(PIX, PAID_BY_PIX, Direction.SALE),
                confirmed.confirmation().orElseThrow());
        assertEquals(PIX, confirmed.confirmedReceipt().orElseThrow());
        assertEquals(Category.SERVICES, confirmed.category());
        assertEquals(pending.id(), confirmed.id());
    }

    @Test
    void invalidFieldsCannotBeConfirmed() {
        var invalid = new ExtractionResult(FIELDS, Optional.empty(), Category.OTHER, Optional.empty(), null,
                List.of(ValidationIssue.error("direction", "Choose")));

        assertThrows(IllegalArgumentException.class, () -> pending.confirm(invalid));
    }

    @Test
    void cannotReviewTwice() {
        var confirmed = pending.confirm(valid(Category.OTHER));
        var rejected = pending.reject();

        assertThrows(ReceiptAlreadyReviewedException.class, confirmed::reject);
        assertThrows(ReceiptAlreadyReviewedException.class, () -> rejected.confirm(valid(Category.OTHER)));
    }

    private static ExtractionResult valid(Category category) {
        return new ExtractionResult(FIELDS, Optional.of(PIX), category, Optional.of(PAID_BY_PIX), Direction.SALE,
                List.of());
    }
}
