package dev.mauricioandrade.kakeizu.application.usecase;

import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.CARD;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.NFCE;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_AS_READ;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_CORRECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
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
    final DailySummaryService summary = new DailySummaryService(repository);
    final ListReceiptsService list = new ListReceiptsService(repository, Fixtures.NOW);

    @Test
    void totalsOnlyConfirmedReceiptsByTypeAndCategory() {
        confirmed(PIX_CORRECTED);                                      // 80.00   PIX  SERVICES
        confirmed(NFCE);                                               // 146.46  NFCE GOODS
        confirmed(CARD);                                               // 500.00  CARD FUEL_SUPPLY
        confirmed(new ReceiptFields("CARD", "20,00", "2022-08-12", "Loja", "69.034.668/0001-56", null, "GOODS", null, "CREDIT", null, null, null));
        stored(PIX_AS_READ, Instant.now());                            // pending: ignored
        confirmed(new ReceiptFields("CARD", "999", "2022-08-13", "Loja", "69.034.668/0001-56", null, "GOODS", null, "CREDIT", null, null, null)); // other day

        var result = summary.summarize(DAY);

        assertEquals(4, result.count());
        assertEquals(new Money("746,46"), result.total());
        assertEquals(Map.of(ReceiptType.NFCE, new Money("146.46"), ReceiptType.PIX, new Money("80"),
                ReceiptType.CARD, new Money("520")), result.byType());
        assertEquals(Map.of(Category.FUEL_SUPPLY, new Money("500"), Category.GOODS, new Money("166.46"),
                Category.SERVICES, new Money("80")), result.byCategory());
        assertEquals(List.of(ReceiptType.NFCE, ReceiptType.PIX, ReceiptType.CARD), List.copyOf(result.byType().keySet()));
    }

    @Test
    void emptyDayIsZero() {
        var result = summary.summarize(DAY);

        assertEquals(0, result.count());
        assertEquals(new Money("0"), result.total());
        assertEquals(Map.of(), result.byType());
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
        confirm.confirm(stored(fields, Instant.now()).id(), fields);
    }

    private ReceiptRecord stored(ReceiptFields fields, Instant createdAt) {
        var pending = ReceiptRecord.pending(fields, Category.OTHER, UUID.randomUUID().toString(), "image/png", "{}", createdAt);
        repository.save(pending);
        return pending;
    }
}
