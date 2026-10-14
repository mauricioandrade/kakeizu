package dev.mauricioandrade.kakeizu.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.BatchItem;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.model.UploadedImage;
import dev.mauricioandrade.kakeizu.domain.port.in.ExtractReceiptUseCase;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BatchExtractReceiptServiceTest {

    // Records the correlation id each photo was read under, keyed by the photo's content
    private final Map<String, String> idsSeen = new ConcurrentHashMap<>();

    @Test
    void readsEveryPhotoAndKeepsTheOrder() {
        var batch = new BatchExtractReceiptService(fakeReader(null));

        var items = batch.extractAll(images("a", "b", "c"));

        assertEquals(List.of("a.jpg", "b.jpg", "c.jpg"), items.stream().map(BatchItem::fileName).toList());
        items.forEach(item -> assertInstanceOf(BatchItem.Done.class, item));
    }

    @Test
    void oneBadPhotoDoesNotStopTheOthers() {
        var batch = new BatchExtractReceiptService(fakeReader("b"));

        var items = batch.extractAll(images("a", "b", "c"));

        assertInstanceOf(BatchItem.Done.class, items.get(0));
        assertEquals(new BatchItem.Failed("b.jpg", "The model could not read this photo. Try it again on its own."),
                items.get(1));
        assertInstanceOf(BatchItem.Done.class, items.get(2));
    }

    @Test
    void photosAreReadAtTheSameTime() throws InterruptedException {
        // Each fake reading waits until all three have started: only works if they run in parallel
        var allStarted = new CountDownLatch(3);
        ExtractReceiptUseCase waitsForTheOthers = (image, type) -> {
            allStarted.countDown();
            try {
                assertTrue(allStarted.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return extracted();
        };

        var items = new BatchExtractReceiptService(waitsForTheOthers).extractAll(images("a", "b", "c"));

        items.forEach(item -> assertInstanceOf(BatchItem.Done.class, item));
    }

    @Test
    void eachPhotoLogsUnderTheBatchIdPlusItsPosition() {
        new BatchExtractReceiptService(fakeReader(null)).extractAll(images("a", "b"));

        String batchId = idsSeen.get("a").substring(0, idsSeen.get("a").indexOf('/'));
        assertEquals(batchId + "/1", idsSeen.get("a"));
        assertEquals(batchId + "/2", idsSeen.get("b"));
        assertEquals("-", Correlation.id(), "the id is gone once the batch is over");
    }

    @Test
    void twoBatchesGetDifferentIds() {
        var batch = new BatchExtractReceiptService(fakeReader(null));
        batch.extractAll(images("a"));
        String first = idsSeen.get("a");

        batch.extractAll(images("a"));

        assertNotEquals(first, idsSeen.get("a"));
    }

    @Test
    void refusesMoreThanFivePhotos() {
        var batch = new BatchExtractReceiptService(fakeReader(null));
        var six = images(IntStream.range(0, 6).mapToObj(String::valueOf).toArray(String[]::new));

        assertThrows(IllegalArgumentException.class, () -> batch.extractAll(six));
        assertThrows(IllegalArgumentException.class, () -> batch.extractAll(List.of()));
    }

    private ExtractReceiptUseCase fakeReader(String failingContent) {
        return (image, type) -> {
            String content = new String(image, StandardCharsets.UTF_8);
            idsSeen.put(content, Correlation.id());
            if (content.equals(failingContent)) {
                throw new ReceiptReadingException("The model could not read the receipt", null);
            }
            return extracted();
        };
    }

    private static UploadResult extracted() {
        return new UploadResult.Queued(ReceiptRecord.pending(Fixtures.CARD, Category.OTHER, "a".repeat(64),
                "image/jpeg", "{}", Fixtures.NOW.instant()));
    }

    private static List<UploadedImage> images(String... contents) {
        return Arrays.stream(contents)
                .map(c -> new UploadedImage(c + ".jpg", c.getBytes(StandardCharsets.UTF_8), "image/jpeg"))
                .toList();
    }
}
