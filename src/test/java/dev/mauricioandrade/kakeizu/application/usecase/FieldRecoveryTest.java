package dev.mauricioandrade.kakeizu.application.usecase;

import static dev.mauricioandrade.kakeizu.application.usecase.Fixtures.reading;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.FieldReading;
import dev.mauricioandrade.kakeizu.domain.model.FieldRecovery;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Ambiguous;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Recovered;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Unrecoverable;
import dev.mauricioandrade.kakeizu.domain.model.RecoverySource;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import dev.mauricioandrade.kakeizu.domain.service.CheckDigitRecovery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The background reading with the model mocked: what happens to a CNPJ or access key that fails its check digits.
 */
class FieldRecoveryTest {

    static final byte[] IMAGE = {1, 2, 3};
    static final String CNPJ = "23010551000131";
    static final String KEY = "35261023010551000131650010000123451123456787";

    final ReceiptReader reader = mock(ReceiptReader.class);
    final InMemoryReceiptRepository repository = new InMemoryReceiptRepository();
    final InMemoryImageStorage storage = new InMemoryImageStorage();

    @Test
    void validFieldsAreNotReadAgain() {
        var saved = readInBackground(nfce(CNPJ, KEY), true);

        assertTrue(saved.recoveries().isEmpty());
        verify(reader, never()).reread(any(), anyString(), any());
    }

    @Test
    void aRereadThatPassesIsSuggested() {
        when(reader.reread(any(), anyString(), eq(CheckDigitField.CNPJ)))
                .thenReturn(new FieldReading(CNPJ, "{\"value\":\"" + CNPJ + "\"}"));

        var saved = readInBackground(nfce("23010551000181", KEY), true);

        assertEquals(List.of(new FieldRecovery(CheckDigitField.CNPJ, "23010551000181",
                new Recovered(CNPJ, RecoverySource.REREAD), "{\"value\":\"" + CNPJ + "\"}")), saved.recoveries());
        verify(reader, times(1)).reread(any(), anyString(), eq(CheckDigitField.CNPJ));
        verify(reader, never()).reread(any(), anyString(), eq(CheckDigitField.NFE_ACCESS_KEY));
    }

    @Test
    void whenTheRereadAlsoFailsTheMisreadDigitSearchSuggests() {
        String misread = swap(KEY, 30, '7');
        when(reader.reread(any(), anyString(), eq(CheckDigitField.NFE_ACCESS_KEY)))
                .thenReturn(new FieldReading(swap(KEY, 37, '8'), "{}"));

        var saved = readInBackground(nfce(CNPJ, misread), true);

        var recovery = saved.recoveryOf("uniqueId").orElseThrow();
        assertEquals(new Recovered(KEY, RecoverySource.CONFUSION_SEARCH), recovery.result());
        assertEquals(misread, recovery.original());
        verify(reader, times(1)).reread(any(), anyString(), eq(CheckDigitField.NFE_ACCESS_KEY));
    }

    @Test
    void whenNothingWorksTheFieldStaysFlagged() {
        when(reader.reread(any(), anyString(), eq(CheckDigitField.CNPJ))).thenReturn(new FieldReading(null, "{}"));

        var saved = readInBackground(nfce("23010551000431", KEY), true); // 1 read as 4: not in the map

        assertInstanceOf(Unrecoverable.class, saved.recoveryOf("taxId").orElseThrow().result());
    }

    @Test
    void theFieldsGoToReviewAsTheModelReadThem() {
        when(reader.reread(any(), anyString(), any())).thenReturn(new FieldReading(CNPJ, "{}"));
        var asRead = nfce("23010551000181", KEY);

        var saved = readInBackground(asRead, true);

        assertEquals(ReceiptStatus.PENDING_REVIEW, saved.status());
        assertEquals(asRead, saved.fields());
    }

    @Test
    void bothFieldsFailingAreEachReadAgainOnce() {
        when(reader.reread(any(), anyString(), any())).thenReturn(new FieldReading(null, "{}"));

        var saved = readInBackground(nfce("73010551000131", swap(KEY, 30, '7')), true);

        assertEquals(2, saved.recoveries().size());
        verify(reader, times(1)).reread(any(), anyString(), eq(CheckDigitField.CNPJ));
        verify(reader, times(1)).reread(any(), anyString(), eq(CheckDigitField.NFE_ACCESS_KEY));
        assertInstanceOf(Ambiguous.class, saved.recoveryOf("uniqueId").orElseThrow().result());
    }

    @Test
    void aFailedRereadStillLeavesTheSearchOnTheFirstReading() {
        when(reader.reread(any(), anyString(), any())).thenThrow(new ReceiptReadingException("busy", null));

        var saved = readInBackground(nfce("73010551000131", KEY), true);

        var recovery = saved.recoveryOf("taxId").orElseThrow();
        assertEquals(new Recovered(CNPJ, RecoverySource.CONFUSION_SEARCH), recovery.result());
        assertNull(recovery.rereadOutput());
    }

    @Test
    void withRereadTurnedOffOnlyTheSearchRuns() {
        var saved = readInBackground(nfce("73010551000131", KEY), false);

        assertEquals(new Recovered(CNPJ, RecoverySource.CONFUSION_SEARCH),
                saved.recoveryOf("taxId").orElseThrow().result());
        verify(reader, never()).reread(any(), anyString(), any());
    }

    @Test
    void aMissingOrUnguardedFieldIsNotReadAgain() {
        // A card slip without CNPJ, and a Pix whose ID has no check digit
        readInBackground(new ReceiptFields("CARD", "10.00", "2026-10-13", "Loja", null, "2UP8IQ", null, null,
                "DEBIT", null, null, null), true);
        readInBackground(new ReceiptFields("PIX", "10.00", "2026-10-13", "Loja", "123", "E123", null, null, null,
                null, null, null), true);

        verify(reader, never()).reread(any(), anyString(), any());
    }

    private ReceiptRecord readInBackground(ReceiptFields asRead, boolean rereadEnabled) {
        when(reader.read(any(), anyString())).thenReturn(reading(asRead));
        String sha256 = UUID.randomUUID().toString();
        storage.store(sha256, IMAGE);
        var receipt = ReceiptRecord.reading(sha256, "image/png", Fixtures.NOW.instant());
        repository.save(receipt);
        var recoverer = new FieldRecoverer(reader, new CheckDigitRecovery(), rereadEnabled);

        new BackgroundReceiptReader(reader, repository, storage, Fixtures.MAPPER, recoverer, Runnable::run)
                .submit(receipt.id());

        return repository.findById(receipt.id()).orElseThrow();
    }

    private static ReceiptFields nfce(String cnpj, String key) {
        return new ReceiptFields("NFCE", "129.80", "2026-10-13", "Posto Exemplo", cnpj, key, "FUEL_SUPPLY", "10:15",
                "CASH", "1", null, null);
    }

    private static String swap(String value, int index, char c) {
        return value.substring(0, index) + c + value.substring(index + 1);
    }
}
