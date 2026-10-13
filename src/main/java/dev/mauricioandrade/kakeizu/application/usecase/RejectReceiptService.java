package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptNotFoundException;
import dev.mauricioandrade.kakeizu.domain.port.in.RejectReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RejectReceiptService implements RejectReceiptUseCase {

    private static final Logger LOG = Logger.getLogger(RejectReceiptService.class);

    private final ReceiptRepository repository;

    public RejectReceiptService(ReceiptRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void reject(UUID id) {
        var stored = repository.findById(id).orElseThrow(ReceiptNotFoundException::new);
        repository.save(stored.reject());
        LOG.infof("Receipt %s rejected", id);
    }
}
