package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptNotFoundException;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptImage;
import dev.mauricioandrade.kakeizu.domain.port.in.ReceiptImageUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ImageStorage;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class ReceiptImageService implements ReceiptImageUseCase {

    private final ReceiptRepository repository;
    private final ImageStorage storage;

    public ReceiptImageService(ReceiptRepository repository, ImageStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    @Transactional
    public ReceiptImage image(UUID id) {
        var receipt = repository.findById(id).orElseThrow(ReceiptNotFoundException::new);
        return new ReceiptImage(storage.load(receipt.imageSha256()), receipt.imageContentType());
    }
}
