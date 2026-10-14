package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.BatchItem;
import dev.mauricioandrade.kakeizu.domain.model.UploadedImage;
import dev.mauricioandrade.kakeizu.domain.port.in.BatchExtractReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.ExtractReceiptUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.StructuredTaskScope;
import org.jboss.logging.Logger;

/**
 * Reads several photos at once, one virtual thread each, inside a structured task scope.
 * The only class that uses a preview API (structured concurrency, JEP 505): the build and the JVM need
 * {@code --enable-preview}.
 * <p>
 * Each fork hashes, deduplicates, stores and queues one photo; the model reads them afterwards, in the
 * background. The scope is about shape: every photo gets an answer, one bad photo does not stop the
 * others, and no thread outlives the request.
 */
@ApplicationScoped
public class BatchExtractReceiptService implements BatchExtractReceiptUseCase {

    private static final Logger LOG = Logger.getLogger(BatchExtractReceiptService.class);

    private final ExtractReceiptUseCase extractReceipt;

    public BatchExtractReceiptService(ExtractReceiptUseCase extractReceipt) {
        this.extractReceipt = extractReceipt;
    }

    @Override
    public List<BatchItem> extractAll(List<UploadedImage> images) {
        if (images.isEmpty() || images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("A batch has 1 to " + MAX_IMAGES + " images, got " + images.size());
        }
        String batchId = Correlation.newId();
        return Correlation.with(batchId, () -> {
            LOG.infof("[%s] Batch of %d photos", Correlation.id(), images.size());
            // awaitAll: wait for every photo, and never cancel the others when one fails.
            // Named virtual threads, so a thread dump or a log line says which batch they belong to
            try (var scope = StructuredTaskScope.open(Joiner.<BatchItem>awaitAll(), config -> config
                    .withName("batch-" + batchId)
                    .withThreadFactory(Thread.ofVirtual().name("batch-" + batchId + "-", 1).factory()))) {
                var tasks = new ArrayList<Subtask<BatchItem>>();
                for (int i = 0; i < images.size(); i++) {
                    var image = images.get(i);
                    // The fork inherits the batch id; each photo binds its own id on top of it
                    String photoId = batchId + "/" + (i + 1);
                    tasks.add(scope.fork(() -> Correlation.with(photoId, () -> extract(image))));
                }
                scope.join();
                return tasks.stream().map(Subtask::get).toList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ReceiptReadingException("Interrupted while reading the batch", e);
            }
        });
    }

    // Never throws: a failure becomes a result, so Subtask.get() always has one
    private BatchItem extract(UploadedImage image) {
        try {
            return new BatchItem.Done(image.fileName(), extractReceipt.extract(image.content(), image.contentType()));
        } catch (RuntimeException e) {
            LOG.warnf("[%s] Photo not read: %s", Correlation.id(), e.toString());
            return new BatchItem.Failed(image.fileName(), "The model could not read this photo. Try it again on its own.");
        }
    }
}
