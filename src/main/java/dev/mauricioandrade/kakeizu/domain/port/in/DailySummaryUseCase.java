package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import java.time.LocalDate;

public interface DailySummaryUseCase {

    /**
     * Totals of the receipts confirmed with the given issue date.
     */
    DailySummary summarize(LocalDate day);
}
