package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.SalesReport;
import dev.mauricioandrade.kakeizu.domain.port.in.SalesReportUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import dev.mauricioandrade.kakeizu.domain.service.SalesReportCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class SalesReportService implements SalesReportUseCase {

    private final ReceiptRepository repository;
    private final SalesReportCalculator calculator;

    public SalesReportService(ReceiptRepository repository, SalesReportCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    @Transactional
    public SalesReport report(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The range ends before it starts");
        }
        var sales = repository.findConfirmedSalesIssuedBetween(from, to).stream()
                .map(ReceiptRecord::confirmation)
                .flatMap(Optional::stream)
                .toList();
        return calculator.calculate(from, to, sales);
    }
}
