package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import java.time.LocalDate;

public interface DailySummaryUseCase {

    /**
     * The day close: sales, expenses, balance and cash drawer from the receipts confirmed with the given issue
     * date, plus how many photos uploaded that day are still to review.
     */
    DailySummary summarize(LocalDate day);
}
