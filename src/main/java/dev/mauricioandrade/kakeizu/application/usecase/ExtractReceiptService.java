package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.port.in.ExtractReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ImageStorage;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.jboss.logging.Logger;

/**
 * Saves the photo and queues it for the model; the reading itself happens in the background.
 * The request context is activated so the repository also works on a batch's own virtual threads.
 */
@ApplicationScoped
public class ExtractReceiptService implements ExtractReceiptUseCase {

    private static final Logger LOG = Logger.getLogger(ExtractReceiptService.class);

    private final BackgroundReceiptReader backgroundReader;
    private final ReceiptRepository repository;
    private final ImageStorage storage;
    private final Clock clock;

    public ExtractReceiptService(BackgroundReceiptReader backgroundReader, ReceiptRepository repository,
                                 ImageStorage storage, Clock clock) {
        this.backgroundReader = backgroundReader;
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    @ActivateRequestContext
    public UploadResult extract(byte[] image, String contentType) {
        String sha256 = sha256(image);
        var existing = repository.findByImageSha256(sha256);
        if (existing.isPresent()) {
            LOG.infof("[%s] Duplicate image, receipt %s", Correlation.id(), existing.get().id());
            return new UploadResult.AlreadyUploaded(existing.get());
        }

        storage.store(sha256, image);
        var receipt = ReceiptRecord.reading(sha256, contentType, clock.instant());
        try {
            repository.save(receipt);
        } catch (DuplicateReceiptException e) {
            // Same image uploaded twice at the same time: the other request won
            return repository.findByImageSha256(sha256)
                    .<UploadResult>map(UploadResult.AlreadyUploaded::new)
                    .orElseThrow(() -> e);
        }
        LOG.infof("[%s] Receipt %s uploaded, queued for reading", Correlation.id(), receipt.id());
        backgroundReader.submit(receipt.id());
        return new UploadResult.Queued(receipt);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
