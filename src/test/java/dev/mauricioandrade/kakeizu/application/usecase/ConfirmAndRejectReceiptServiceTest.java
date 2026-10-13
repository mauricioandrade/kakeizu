package dev.mauricioandrade.kakeizu.application.usecase;

import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.CARD;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.NOW;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_AS_READ;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_CORRECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptAlreadyReviewedException;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptNotFoundException;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ConfirmResult;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmAndRejectReceiptServiceTest {

    final InMemoryReceiptRepository repository = new InMemoryReceiptRepository();
    final ConfirmReceiptService confirm = new ConfirmReceiptService(repository, Fixtures.MAPPER);
    final RejectReceiptService reject = new RejectReceiptService(repository);
    final ReviewReceiptService review = new ReviewReceiptService(repository, Fixtures.MAPPER);

    @Test
    void reviewShowsWhatIsWrongWithTheModelOutput() {
        var pending = stored(PIX_AS_READ);

        var result = review.review(pending.id()).result();

        assertEquals(List.of(ValidationIssue.error("uniqueId", "Pix transaction ID must have 32 characters, got 33")),
                result.issues());
    }

    @Test
    void correctedFieldsConfirmTheReceiptWithTheChosenCategory() {
        var pending = stored(PIX_AS_READ);

        var confirmed = assertInstanceOf(ConfirmResult.Confirmed.class, confirm.confirm(pending.id(), PIX_CORRECTED));

        var saved = repository.findById(pending.id()).orElseThrow();
        assertEquals(ReceiptStatus.CONFIRMED, saved.status());
        assertEquals(Category.SERVICES, saved.category());
        assertEquals(PIX_CORRECTED, saved.fields());
        assertInstanceOf(PixReceipt.class, saved.confirmedReceipt().orElseThrow());
        assertEquals(saved, confirmed.receipt());
    }

    @Test
    void fieldsThatAreStillWrongAreNotConfirmed() {
        var pending = stored(PIX_AS_READ);

        var invalid = assertInstanceOf(ConfirmResult.Invalid.class, confirm.confirm(pending.id(), PIX_AS_READ));

        assertEquals("uniqueId", invalid.result().issues().getFirst().field());
        assertEquals(ReceiptStatus.PENDING_REVIEW, repository.findById(pending.id()).orElseThrow().status());
    }

    @Test
    void samePixFromAnotherPhotoCannotBeConfirmedTwice() {
        confirm.confirm(stored(PIX_AS_READ).id(), PIX_CORRECTED);
        var secondPhoto = stored(PIX_AS_READ);

        var invalid = assertInstanceOf(ConfirmResult.Invalid.class, confirm.confirm(secondPhoto.id(), PIX_CORRECTED));

        assertEquals(ValidationIssue.error("uniqueId", "A receipt with this identifier was already confirmed"),
                invalid.result().issues().getLast());
    }

    @Test
    void cardSlipsWithTheSameAuthorizationCodeAreBothConfirmed() {
        assertInstanceOf(ConfirmResult.Confirmed.class, confirm.confirm(stored(CARD).id(), CARD));
        assertInstanceOf(ConfirmResult.Confirmed.class, confirm.confirm(stored(CARD).id(), CARD));
    }

    @Test
    void rejectedReceiptCannotBeConfirmedLater() {
        var pending = stored(PIX_AS_READ);
        reject.reject(pending.id());

        assertEquals(ReceiptStatus.REJECTED, repository.findById(pending.id()).orElseThrow().status());
        assertThrows(ReceiptAlreadyReviewedException.class, () -> confirm.confirm(pending.id(), PIX_CORRECTED));
    }

    @Test
    void unknownIdIsNotFound() {
        var unknown = UUID.randomUUID();

        assertThrows(ReceiptNotFoundException.class, () -> confirm.confirm(unknown, PIX_CORRECTED));
        assertThrows(ReceiptNotFoundException.class, () -> reject.reject(unknown));
        assertThrows(ReceiptNotFoundException.class, () -> review.review(unknown));
    }

    private ReceiptRecord stored(ReceiptFields fields) {
        var pending = ReceiptRecord.pending(fields, Category.OTHER, UUID.randomUUID().toString(), "image/png", "{}",
                NOW.instant());
        repository.save(pending);
        return pending;
    }
}
