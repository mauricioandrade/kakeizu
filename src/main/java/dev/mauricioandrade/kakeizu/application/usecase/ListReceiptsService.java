package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.port.in.ListReceiptsUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class ListReceiptsService implements ListReceiptsUseCase {

    private final ReceiptRepository repository;
    private final Clock clock;

    public ListReceiptsService(ReceiptRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<ReceiptRecord> uploadedOn(LocalDate day) {
        var zone = clock.getZone();
        return repository.findCreatedBetween(day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
