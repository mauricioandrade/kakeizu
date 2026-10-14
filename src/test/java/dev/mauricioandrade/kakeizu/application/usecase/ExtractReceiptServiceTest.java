package dev.mauricioandrade.kakeizu.application.usecase;

import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_AS_READ;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.reading;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtractReceiptServiceTest {

    static final byte[] IMAGE = {1, 2, 3};

    final ReceiptReader reader = mock(ReceiptReader.class);
    final InMemoryReceiptRepository repository = new InMemoryReceiptRepository();
    final InMemoryImageStorage storage = new InMemoryImageStorage();
    // Holds the background work until the test runs it, so both sides of the queue can be seen
    final List<Runnable> queue = new ArrayList<>();
    final BackgroundReceiptReader backgroundReader = new BackgroundReceiptReader(reader, repository, storage,
            Fixtures.MAPPER, queue::add);
    final ExtractReceiptService service = new ExtractReceiptService(backgroundReader, repository, storage, Fixtures.NOW);

    @Test
    void uploadSavesThePhotoAndReturnsBeforeTheModelReads() {
        var result = assertInstanceOf(UploadResult.Queued.class, service.extract(IMAGE, "image/png"));

        var saved = repository.findById(result.receipt().id()).orElseThrow();
        assertEquals(ReceiptStatus.READING, saved.status());
        assertEquals(Fixtures.NOW.instant(), saved.createdAt());
        assertArrayEquals(IMAGE, storage.load(saved.imageSha256()));
        assertEquals(1, queue.size());
        verify(reader, never()).read(any(), anyString());
    }

    @Test
    void backgroundReadingSavesWhatTheModelReadAsPendingEvenWithErrors() {
        when(reader.read(any(), anyString())).thenReturn(reading(PIX_AS_READ));
        var id = service.extract(IMAGE, "image/png").receipt().id();

        runQueue();

        var saved = repository.findById(id).orElseThrow();
        assertEquals(ReceiptStatus.PENDING_REVIEW, saved.status());
        assertEquals(PIX_AS_READ, saved.fields());
        assertEquals(Category.OTHER, saved.category());
        assertEquals("{\"model\":\"output\"}", saved.rawModelOutput());
        assertFalse(saved.modelFailed());
    }

    @Test
    void sameImageAgainReturnsTheExistingReceiptWithoutQueueingIt() {
        var first = service.extract(IMAGE, "image/png");

        var second = assertInstanceOf(UploadResult.AlreadyUploaded.class, service.extract(IMAGE.clone(), "image/png"));

        assertEquals(first.receipt().id(), second.receipt().id());
        assertEquals(1, repository.receipts.size());
        assertEquals(1, queue.size());
    }

    @Test
    void imageHashIsSha256Hex() {
        var receipt = service.extract("abc".getBytes(), "image/png").receipt();

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", receipt.imageSha256());
    }

    @Test
    void modelFailureLeavesAnEmptyReceiptToTypeFromThePhoto() {
        when(reader.read(any(), anyString())).thenThrow(new ReceiptReadingException("down", new RuntimeException()));
        var id = service.extract(IMAGE, "image/png").receipt().id();

        runQueue();

        var saved = repository.findById(id).orElseThrow();
        assertEquals(ReceiptStatus.PENDING_REVIEW, saved.status());
        assertTrue(saved.modelFailed());
        assertNull(saved.fields().amount());
        assertArrayEquals(IMAGE, storage.load(saved.imageSha256()));
    }

    @Test
    void readingsLeftByARestartStartAgain() {
        when(reader.read(any(), anyString())).thenReturn(reading(PIX_AS_READ));
        storage.store("a".repeat(64), IMAGE);
        var unfinished = ReceiptRecord.reading("a".repeat(64), "image/png", Fixtures.NOW.instant());
        repository.save(unfinished);

        backgroundReader.resumeAfterRestart();
        runQueue();

        assertEquals(ReceiptStatus.PENDING_REVIEW, repository.findById(unfinished.id()).orElseThrow().status());
    }

    @Test
    void aReceiptNoLongerReadingIsLeftAlone() {
        backgroundReader.submit(UUID.randomUUID());

        runQueue();

        verify(reader, never()).read(any(), anyString());
    }

    private void runQueue() {
        var pending = List.copyOf(queue);
        queue.clear();
        pending.forEach(Runnable::run);
    }
}
