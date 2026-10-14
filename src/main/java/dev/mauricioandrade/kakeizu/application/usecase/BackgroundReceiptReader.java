package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.port.out.ImageStorage;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Reads uploaded photos in the background, so the person can take the next photo instead of waiting
 * ~15 s for the model. As many workers as the GPU takes readings at once; the rest wait in line, in order.
 * Readings left half way by a restart start again when the app comes back.
 */
@ApplicationScoped
public class BackgroundReceiptReader {

    private static final Logger LOG = Logger.getLogger(BackgroundReceiptReader.class);

    private final ReceiptReader reader;
    private final ReceiptRepository repository;
    private final ImageStorage storage;
    private final ExtractionMapper mapper;
    private final Executor executor;

    @Inject
    public BackgroundReceiptReader(ReceiptReader reader, ReceiptRepository repository, ImageStorage storage,
                                   ExtractionMapper mapper,
                                   @ConfigProperty(name = "kakeizu.ai.max-concurrent-readings") int workers) {
        this(reader, repository, storage, mapper,
                Executors.newFixedThreadPool(workers, Thread.ofVirtual().name("reader-", 1).factory()));
    }

    BackgroundReceiptReader(ReceiptReader reader, ReceiptRepository repository, ImageStorage storage,
                            ExtractionMapper mapper, Executor executor) {
        this.reader = reader;
        this.repository = repository;
        this.storage = storage;
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Returns at once. The caller's correlation id goes along by hand: a scoped value reaches the threads
     * of a structured task scope, but not an executor's.
     */
    public void submit(UUID receiptId) {
        String correlationId = Correlation.id();
        executor.execute(() -> Correlation.with(correlationId, () -> {
            read(receiptId);
            return null;
        }));
    }

    /**
     * Queues again what a restart left half read. Called once the app is up.
     */
    @ActivateRequestContext
    public void resumeAfterRestart() {
        var unfinished = repository.findReading();
        if (!unfinished.isEmpty()) {
            LOG.infof("Resuming %d readings left by a restart", unfinished.size());
            unfinished.forEach(receipt -> submit(receipt.id()));
        }
    }

    // The request context lets the repository work on this worker thread, without holding a transaction
    // (and a database connection) while the model reads
    @ActivateRequestContext
    void read(UUID receiptId) {
        var receipt = repository.findById(receiptId).filter(r -> r.status() == ReceiptStatus.READING);
        if (receipt.isEmpty()) {
            return;
        }
        repository.save(readOrGiveUp(receipt.get()));
    }

    private ReceiptRecord readOrGiveUp(ReceiptRecord receipt) {
        try {
            var reading = reader.read(storage.load(receipt.imageSha256()), receipt.imageContentType());
            LOG.infof("[%s] Receipt %s read, pending review", Correlation.id(), receipt.id());
            return receipt.read(reading, mapper.map(reading.fields()).category());
        } catch (RuntimeException e) {
            // A person can still type the receipt from the photo
            LOG.warnf("[%s] Receipt %s not read, left for typing: %s", Correlation.id(), receipt.id(), e.toString());
            return receipt.readingFailed();
        }
    }

    // No interrupt: an interrupted reading would be saved as failed. Whatever is still reading stays
    // READING and starts again on the next start
    @PreDestroy
    void stop() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }
}
