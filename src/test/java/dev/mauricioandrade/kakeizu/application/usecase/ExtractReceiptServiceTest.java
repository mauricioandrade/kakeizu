package dev.mauricioandrade.kakeizu.application.usecase;

import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.PIX_AS_READ;
import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.reading;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import org.junit.jupiter.api.Test;

class ExtractReceiptServiceTest {

    static final byte[] IMAGE = {1, 2, 3};

    final ReceiptReader reader = mock(ReceiptReader.class);
    final InMemoryReceiptRepository repository = new InMemoryReceiptRepository();
    final InMemoryImageStorage storage = new InMemoryImageStorage();
    final ExtractReceiptService service = new ExtractReceiptService(reader, repository, storage, Fixtures.MAPPER, Fixtures.NOW);

    @Test
    void savesWhatTheModelReadAsPendingEvenWithErrors() {
        when(reader.read(any(), anyString())).thenReturn(reading(PIX_AS_READ));

        var result = assertInstanceOf(UploadResult.Extracted.class, service.extract(IMAGE, "image/png"));

        var saved = repository.findById(result.receipt().id()).orElseThrow();
        assertEquals(ReceiptStatus.PENDING_REVIEW, saved.status());
        assertEquals(PIX_AS_READ, saved.fields());
        assertEquals(Category.OTHER, saved.category());
        assertEquals("{\"model\":\"output\"}", saved.rawModelOutput());
        assertEquals(Fixtures.NOW.instant(), saved.createdAt());
        assertArrayEquals(IMAGE, storage.load(saved.imageSha256()));
    }

    @Test
    void sameImageAgainReturnsTheExistingReceiptWithoutCallingTheModel() {
        when(reader.read(any(), anyString())).thenReturn(reading(PIX_AS_READ));
        var first = service.extract(IMAGE, "image/png");

        var second = assertInstanceOf(UploadResult.AlreadyUploaded.class, service.extract(IMAGE.clone(), "image/png"));

        assertEquals(first.receipt().id(), second.receipt().id());
        assertEquals(1, repository.receipts.size());
        verify(reader, times(1)).read(any(), anyString());
    }

    @Test
    void imageHashIsSha256Hex() {
        when(reader.read(any(), anyString())).thenReturn(reading(PIX_AS_READ));

        var receipt = service.extract("abc".getBytes(), "image/png").receipt();

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", receipt.imageSha256());
    }

    @Test
    void modelFailureSavesNothing() {
        when(reader.read(any(), anyString())).thenThrow(new ReceiptReadingException("down", new RuntimeException()));

        assertThrows(ReceiptReadingException.class, () -> service.extract(IMAGE, "image/png"));
        assertTrue(repository.receipts.isEmpty());
        assertTrue(storage.images.isEmpty());
    }
}
