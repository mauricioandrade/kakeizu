package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.SalesReport;
import java.time.LocalDate;

public interface SalesReportUseCase {

    /**
     * Confirmed sales issued from {@code from} to {@code to}, both inclusive.
     */
    SalesReport report(LocalDate from, LocalDate to);
}
