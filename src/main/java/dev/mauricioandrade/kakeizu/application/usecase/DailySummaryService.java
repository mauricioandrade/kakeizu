package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.port.in.DailySummaryUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import dev.mauricioandrade.kakeizu.domain.service.DailySummaryCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;

@ApplicationScoped
public class DailySummaryService implements DailySummaryUseCase {

    private final ReceiptRepository repository;
    private final DailySummaryCalculator calculator;
    private final Clock clock;

    public DailySummaryService(ReceiptRepository repository, DailySummaryCalculator calculator, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DailySummary summarize(LocalDate day) {
        return calculator.calculate(day, repository.findConfirmedIssuedOn(day), toReview(day));
    }

    // By upload day: a photo still to review may not have a readable issue date yet
    private int toReview(LocalDate day) {
        var zone = clock.getZone();
        return (int) repository.findCreatedBetween(day.atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .filter(r -> r.status() == ReceiptStatus.READING || r.status() == ReceiptStatus.PENDING_REVIEW)
                .count();
    }
}
