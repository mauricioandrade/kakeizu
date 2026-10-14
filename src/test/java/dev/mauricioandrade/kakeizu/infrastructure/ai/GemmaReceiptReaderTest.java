package dev.mauricioandrade.kakeizu.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.FieldReading;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import org.junit.jupiter.api.Test;

class GemmaReceiptReaderTest {

    final ReceiptExtractionService service = mock(ReceiptExtractionService.class);
    final FieldRereadService rereadService = mock(FieldRereadService.class);
    final GemmaReceiptReader reader = new GemmaReceiptReader(service, rereadService, new ObjectMapper(), 1);

    @Test
    void passesTheModelOutputThroughUntouched() {
        when(service.extract(any())).thenReturn(new ExtractedReceipt("NFCE", "146.46", "2020-01-01", "LOJA RUA",
                "0000000000000000000000000000000", null, "GOODS", "12:30", "CASH", "1", null));

        var reading = reader.read(new byte[]{1}, "image/png");

        // The model never decides sale or expense: direction stays empty
        assertEquals(new ReceiptFields("NFCE", "146.46", "2020-01-01", "LOJA RUA", "0000000000000000000000000000000",
                null, "GOODS", "12:30", "CASH", "1", null, null), reading.fields());
        assertEquals("{\"type\":\"NFCE\",\"amount\":\"146.46\",\"date\":\"2020-01-01\",\"counterparty\":\"LOJA RUA\","
                + "\"taxId\":\"0000000000000000000000000000000\",\"uniqueId\":null,\"suggestedCategory\":\"GOODS\","
                + "\"time\":\"12:30\",\"paymentMethod\":\"CASH\",\"installments\":\"1\",\"cardBrand\":null}",
                reading.rawOutput());
    }

    @Test
    void nullOrPunctuationMeansTheFieldWasNotFound() {
        // Real gemma4:e4b output for a card purchase in a bank app, which shows no merchant CNPJ
        when(service.extract(any())).thenReturn(new ExtractedReceipt("CARD", "89.82", "2026-09-28",
                "TOESCA SUPERMERCADO EI", "null", " NULL ", "GOODS", "14:14", "CREDIT", ", ", "-"));

        var reading = reader.read(new byte[]{1}, "image/png");

        assertNull(reading.fields().taxId());
        assertNull(reading.fields().uniqueId());
        // Real gemma4:e4b answer for the installments of a Pix, once every field became required
        assertNull(reading.fields().installments());
        assertNull(reading.fields().cardBrand());
        assertTrue(reading.rawOutput().contains("\"taxId\":\"null\""), "raw output is kept as the model wrote it");
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
                .thenReturn(new ExtractedReceipt("PIX", "1.00", null, null, null, null, null, null, null, null, null));

        assertThrows(ReceiptReadingException.class, () -> reader.read(new byte[]{1}, "image/png"));
        assertEquals("PIX", reader.read(new byte[]{1}, "image/png").fields().type());
    }

    @Test
    void rereadsAFieldByItsPrintedNameAndLength() {
        when(rereadService.reread(any(), anyString(), anyInt())).thenReturn(new RereadField("23010551000131"));

        var reading = reader.reread(new byte[]{1}, "image/png", CheckDigitField.CNPJ);

        assertEquals(new FieldReading("23010551000131", "{\"value\":\"23010551000131\"}"), reading);
        verify(rereadService).reread(any(), contains("CNPJ"), eq(14));
    }

    @Test
    void anUnreadableFieldOnRereadIsNull() {
        when(rereadService.reread(any(), anyString(), anyInt())).thenReturn(new RereadField("null"));

        var reading = reader.reread(new byte[]{1}, "image/png", CheckDigitField.NFE_ACCESS_KEY);

        assertNull(reading.value());
        assertEquals("{\"value\":\"null\"}", reading.rawOutput());
        verify(rereadService).reread(any(), contains("Chave de Acesso"), eq(44));
    }

    @Test
    void rereadErrorsBecomeADomainExceptionAndFreeTheModel() {
        when(rereadService.reread(any(), anyString(), anyInt())).thenThrow(new RuntimeException("Read timed out"));
        when(service.extract(any()))
                .thenReturn(new ExtractedReceipt("PIX", "1.00", null, null, null, null, null, null, null, null, null));

        assertThrows(ReceiptReadingException.class,
                () -> reader.reread(new byte[]{1}, "image/png", CheckDigitField.CNPJ));
        assertEquals("PIX", reader.read(new byte[]{1}, "image/png").fields().type());
    }
}
