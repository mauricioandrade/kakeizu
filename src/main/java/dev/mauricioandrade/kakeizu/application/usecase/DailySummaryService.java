package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.port.in.DailySummaryUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class DailySummaryService implements DailySummaryUseCase {

    private static final Money ZERO = new Money(BigDecimal.ZERO);

    private final ReceiptRepository repository;

    public DailySummaryService(ReceiptRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DailySummary summarize(LocalDate day) {
        var confirmed = repository.findConfirmedIssuedOn(day);
        var byType = new EnumMap<ReceiptType, Money>(ReceiptType.class);
        var byCategory = new EnumMap<Category, Money>(Category.class);
        var total = ZERO;
        for (ReceiptRecord record : confirmed) {
            var receipt = record.confirmedReceipt().orElseThrow();
            add(byType, receipt.type(), receipt.amount());
            add(byCategory, record.category(), receipt.amount());
            total = total.plus(receipt.amount());
        }
        return new DailySummary(day, confirmed.size(), total, byType, byCategory);
    }

    private static <K> void add(Map<K, Money> totals, K key, Money amount) {
        totals.merge(key, amount, Money::plus);
    }
}
