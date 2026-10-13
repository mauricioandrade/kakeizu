package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.port.in.ExtractReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.out.ImageStorage;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.jboss.logging.Logger;

/**
 * Not transactional on purpose: the model call can take a minute and must not hold a database connection.
 */
@ApplicationScoped
public class ExtractReceiptService implements ExtractReceiptUseCase {

    private static final Logger LOG = Logger.getLogger(ExtractReceiptService.class);

    private final ReceiptReader reader;
    private final ReceiptRepository repository;
    private final ImageStorage storage;
    private final ExtractionMapper mapper;
    private final Clock clock;

    public ExtractReceiptService(ReceiptReader reader, ReceiptRepository repository, ImageStorage storage,
                                 ExtractionMapper mapper, Clock clock) {
        this.reader = reader;
        this.repository = repository;
        this.storage = storage;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public UploadResult extract(byte[] image, String contentType) {
        String sha256 = sha256(image);
        var existing = repository.findByImageSha256(sha256);
        if (existing.isPresent()) {
            LOG.infof("Duplicate image, receipt %s", existing.get().id());
            return new UploadResult.AlreadyUploaded(existing.get());
        }

        var reading = reader.read(image, contentType);
        var category = mapper.map(reading.fields()).category();
        storage.store(sha256, image);
        var receipt = ReceiptRecord.pending(reading.fields(), category, sha256, contentType, reading.rawOutput(),
                clock.instant());
        try {
            repository.save(receipt);
        } catch (DuplicateReceiptException e) {
            // Same image uploaded twice at the same time: the other request won
            return repository.findByImageSha256(sha256)
                    .<UploadResult>map(UploadResult.AlreadyUploaded::new)
                    .orElseThrow(() -> e);
        }
        LOG.infof("Receipt %s extracted, pending review", receipt.id());
        return new UploadResult.Extracted(receipt);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
