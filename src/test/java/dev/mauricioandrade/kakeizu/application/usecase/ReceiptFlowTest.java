package dev.mauricioandrade.kakeizu.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.mauricioandrade.kakeizu.domain.model.ConfirmResult;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.port.in.ConfirmReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.DailySummaryUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.ExtractReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.ReviewReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The whole flow through the real wiring and database; only the model is replaced.
 */
@QuarkusTest
class ReceiptFlowTest {

    // A day no other test uses, since this test commits its data
    static final LocalDate DAY = LocalDate.of(2021, 5, 4);
    static final ReceiptFields AS_READ = new ReceiptFields("PIX", "80.00", "2021-05-04", "Pagsmile", null,
            "E00416968202105040422Mzmiz9rPYPLT", "OTHER", null, null, null, null, null);

    @InjectMock
    ReceiptReader reader;

    private final ExtractReceiptUseCase extract;
    private final ReviewReceiptUseCase review;
    private final ConfirmReceiptUseCase confirm;
    private final DailySummaryUseCase summary;

    ReceiptFlowTest(ExtractReceiptUseCase extract, ReviewReceiptUseCase review, ConfirmReceiptUseCase confirm,
                  DailySummaryUseCase summary) {
        this.extract = extract;
        this.review = review;
        this.confirm = confirm;
        this.summary = summary;
    }

    @Test
    void uploadReviewConfirmSummarize() throws InterruptedException {
        when(reader.read(any(), anyString())).thenReturn(Fixtures.reading(AS_READ));
        byte[] image = UUID.randomUUID().toString().getBytes();

        var uploaded = assertInstanceOf(UploadResult.Queued.class, extract.extract(image, "image/png"));
        var id = uploaded.receipt().id();
        assertInstanceOf(UploadResult.AlreadyUploaded.class, extract.extract(image, "image/png"));
        awaitRead(id);

        assertFalse(review.review(id).result().isValid());

        var corrected = new ReceiptFields("PIX", "80,00", "2021-05-04", "Pagsmile", null,
                "E00416968202105040422Mmzi9rPYPLT", "SERVICES", null, null, null, null, "SALE");
        var confirmed = assertInstanceOf(ConfirmResult.Confirmed.class, confirm.confirm(id, corrected));
        assertEquals(ReceiptStatus.CONFIRMED, confirmed.receipt().status());

        var day = summary.summarize(DAY);
        assertEquals(1, day.count());
        assertEquals(Map.of(ReceiptType.PIX, new Money("80")), day.byType());
    }

    // The model reads in the background, on the real worker thread
    private void awaitRead(UUID id) throws InterruptedException {
        for (int i = 0; i < 100 && review.review(id).receipt().status() == ReceiptStatus.READING; i++) {
            Thread.sleep(50);
        }
        assertEquals(ReceiptStatus.PENDING_REVIEW, review.review(id).receipt().status());
    }
}
