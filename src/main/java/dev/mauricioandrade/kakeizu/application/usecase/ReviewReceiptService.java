package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptNotFoundException;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptReview;
import dev.mauricioandrade.kakeizu.domain.port.in.ReviewReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class ReviewReceiptService implements ReviewReceiptUseCase {

    private final ReceiptRepository repository;
    private final ExtractionMapper mapper;

    public ReviewReceiptService(ReceiptRepository repository, ExtractionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ReceiptReview review(UUID id) {
        var receipt = repository.findById(id).orElseThrow(ReceiptNotFoundException::new);
        return new ReceiptReview(receipt, mapper.map(receipt.fields()));
    }
}
