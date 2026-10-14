package dev.mauricioandrade.kakeizu.application.usecase;

import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.CARD;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.NFCE;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_AS_READ;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_CORRECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.service.DailySummaryCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailySummaryAndListServiceTest {

    static final LocalDate DAY = LocalDate.of(2022, 8, 12);

    final InMemoryReceiptRepository repository = new InMemoryReceiptRepository();
    final ConfirmReceiptService confirm = new ConfirmReceiptService(repository, Fixtures.MAPPER);
    final DailySummaryService summary = new DailySummaryService(repository, new DailySummaryCalculator(),
            Fixtures.NOW);
    final ListReceiptsService list = new ListReceiptsService(repository, Fixtures.NOW);

    @Test
    void closesTheDayWithOnlyConfirmedReceipts() {
        // The shop is Melhor Grão Stone (Fixtures.MAPPER): its card slips are sales, the rest expenses
        confirmed(PIX_CORRECTED);                                      // 80.00   expense SERVICES
        confirmed(NFCE);                                               // 146.46  expense GOODS
        confirmed(CARD);                                               // 500.00  sale    CREDIT
        confirmed(new ReceiptFields("CARD", "20,00", "2022-08-12", "Loja", "69.034.668/0001-56", null, "GOODS", null, "CREDIT", null, null, null));
        stored(PIX_AS_READ, Instant.now());                            // pending: ignored
        confirmed(new ReceiptFields("CARD", "999", "2022-08-13", "Loja", "69.034.668/0001-56", null, "GOODS", null, "CREDIT", null, null, null)); // other day

        var result = summary.summarize(DAY);

        assertEquals(4, result.count());
        assertEquals(new Money("520"), result.sales().total());
        assertEquals(Map.of(PaymentMethod.CREDIT, new Money("520")), result.sales().byMethod());
        assertEquals(new Money("226.46"), result.expenses().total());
        assertEquals(Map.of(Category.GOODS, new Money("146.46"), Category.SERVICES, new Money("80")),
                result.expenses().byCategory());
        assertEquals(new BigDecimal("293.54"), result.balance());
    }

    @Test
    void countsWhatWasUploadedThatDayAndIsStillToReview() {
        var reading = ReceiptRecord.reading(UUID.randomUUID().toString(), "image/png",
                Instant.parse("2026-10-13T12:00:00Z"));
        repository.save(reading);
        stored(PIX_AS_READ, Instant.parse("2026-10-13T13:00:00Z"));    // pending review
        confirmed(CARD, Instant.parse("2026-10-13T14:00:00Z"));       // reviewed: not left to review
        stored(PIX_AS_READ, Instant.parse("2026-10-14T13:00:00Z"));    // another day

        assertEquals(2, summary.summarize(LocalDate.of(2026, 10, 13)).toReview());
    }

    @Test
    void emptyDayIsZero() {
        var result = summary.summarize(DAY);

        assertEquals(0, result.count());
        assertEquals(new BigDecimal("0.00"), result.balance());
        assertEquals(Map.of(), result.sales().byMethod());
    }

    @Test
    void uploadsAreGroupedByDayInTheBusinessTimeZone() {
        var lateOn12th = stored(PIX_AS_READ, Instant.parse("2026-10-13T02:30:00Z"));   // 23:30 on the 12th in São Paulo
        var earlyOn13th = stored(PIX_AS_READ, Instant.parse("2026-10-13T03:30:00Z"));  // 00:30 on the 13th
        var lateOn13th = stored(PIX_AS_READ, Instant.parse("2026-10-14T02:00:00Z"));   // 23:00 on the 13th

        assertEquals(List.of(lateOn13th.id(), earlyOn13th.id()),
                list.uploadedOn(LocalDate.of(2026, 10, 13)).stream().map(ReceiptRecord::id).toList());
        assertEquals(List.of(lateOn12th.id()),
                list.uploadedOn(LocalDate.of(2026, 10, 12)).stream().map(ReceiptRecord::id).toList());
    }

    private void confirmed(ReceiptFields fields) {
        confirmed(fields, Instant.now());
    }

    private void confirmed(ReceiptFields fields, Instant createdAt) {
        confirm.confirm(stored(fields, createdAt).id(), fields);
    }

    private ReceiptRecord stored(ReceiptFields fields, Instant createdAt) {
        var pending = ReceiptRecord.pending(fields, Category.OTHER, UUID.randomUUID().toString(), "image/png", "{}", createdAt);
        repository.save(pending);
        return pending;
    }
}
