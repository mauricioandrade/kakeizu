package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptNotFoundException;
import dev.mauricioandrade.kakeizu.domain.model.ConfirmResult;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import dev.mauricioandrade.kakeizu.domain.port.in.ConfirmReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConfirmReceiptService implements ConfirmReceiptUseCase {

    private static final Logger LOG = Logger.getLogger(ConfirmReceiptService.class);

    private final ReceiptRepository repository;
    private final ExtractionMapper mapper;

    public ConfirmReceiptService(ReceiptRepository repository, ExtractionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ConfirmResult confirm(UUID id, ReceiptFields correctedFields) {
        var stored = repository.findById(id).orElseThrow(ReceiptNotFoundException::new);
        var result = mapper.map(correctedFields);
        if (!result.isValid()) {
            return new ConfirmResult.Invalid(result);
        }
        Receipt receipt = result.receipt().orElseThrow();
        if (isAlreadyConfirmed(receipt)) {
            LOG.infof("Receipt %s not confirmed: identifier already confirmed", id);
            return new ConfirmResult.Invalid(withIssue(result, ValidationIssue.error("uniqueId",
                    "A receipt with this identifier was already confirmed")));
        }
        var confirmed = stored.confirm(correctedFields, receipt, result.category());
        repository.save(confirmed);
        LOG.infof("Receipt %s confirmed", id);
        return new ConfirmResult.Confirmed(confirmed);
    }

    // Friendly check for the review screen; the unique index still guards against concurrent confirmations
    private boolean isAlreadyConfirmed(Receipt receipt) {
        return receipt.deduplicationKey()
                .flatMap(key -> repository.findConfirmedByUniqueId(receipt.type(), key))
                .isPresent();
    }

    private static ExtractionResult withIssue(ExtractionResult result, ValidationIssue issue) {
        var issues = new ArrayList<>(result.issues());
        issues.add(issue);
        return new ExtractionResult(result.fields(), result.receipt(), result.category(), issues);
    }
}
