package dev.mauricioandrade.kakeizu.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import org.junit.jupiter.api.Test;

class GemmaReceiptReaderTest {

    final ReceiptExtractionService service = mock(ReceiptExtractionService.class);
    final GemmaReceiptReader reader = new GemmaReceiptReader(service, new ObjectMapper(), 1);

    @Test
    void passesTheModelOutputThroughUntouched() {
        when(service.extract(any())).thenReturn(new ExtractedReceipt("NFCE", "146.46", "2020-01-01", "LOJA RUA",
                "0000000000000000000000000000000", null, "GOODS"));

        var reading = reader.read(new byte[]{1}, "image/png");

        assertEquals(new ReceiptFields("NFCE", "146.46", "2020-01-01", "LOJA RUA", "0000000000000000000000000000000",
                null, "GOODS"), reading.fields());
        assertEquals("{\"type\":\"NFCE\",\"amount\":\"146.46\",\"date\":\"2020-01-01\",\"counterparty\":\"LOJA RUA\","
                + "\"taxId\":\"0000000000000000000000000000000\",\"uniqueId\":null,\"suggestedCategory\":\"GOODS\"}",
                reading.rawOutput());
    }

    @Test
    void modelErrorsBecomeADomainException() {
        when(service.extract(any())).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(ReceiptReadingException.class, () -> reader.read(new byte[]{1}, "image/png"));
    }

    @Test
    void aFailedReadingFreesTheModelForTheNextOne() {
        when(service.extract(any()))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(new ExtractedReceipt("PIX", "1.00", null, null, null, null, null));

        assertThrows(ReceiptReadingException.class, () -> reader.read(new byte[]{1}, "image/png"));
        assertEquals("PIX", reader.read(new byte[]{1}, "image/png").fields().type());
    }
}
