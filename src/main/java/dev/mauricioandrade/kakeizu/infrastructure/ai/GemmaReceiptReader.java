package dev.mauricioandrade.kakeizu.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One GPU, one model: readings beyond {@code kakeizu.ai.max-concurrent-readings} wait in line instead of
 * piling up on Ollama, and give up if the line does not move.
 */
@ApplicationScoped
public class GemmaReceiptReader implements ReceiptReader {

    private static final Logger LOG = Logger.getLogger(GemmaReceiptReader.class);
    private static final long MAX_WAIT_SECONDS = 120;

    private final ReceiptExtractionService extractionService;
    private final ObjectMapper objectMapper;
    private final Semaphore modelSlots;

    public GemmaReceiptReader(ReceiptExtractionService extractionService, ObjectMapper objectMapper,
                              @ConfigProperty(name = "kakeizu.ai.max-concurrent-readings") int maxConcurrentReadings) {
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
        this.modelSlots = new Semaphore(maxConcurrentReadings, true);
    }

    @Override
    public ModelReading read(byte[] image, String contentType) {
        acquireModelSlot();
        long start = System.nanoTime();
        ExtractedReceipt extracted;
        try {
            extracted = extractionService.extract(Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(image))
                    .mimeType(contentType)
                    .build());
        } catch (RuntimeException e) {
            throw new ReceiptReadingException("The model could not read the receipt", e);
        } finally {
            modelSlots.release();
        }
        LOG.infof("[%s] Model read the receipt in %d ms", Correlation.id(), (System.nanoTime() - start) / 1_000_000);
        return new ModelReading(toFields(extracted), toJson(extracted));
    }

    private void acquireModelSlot() {
        try {
            if (!modelSlots.tryAcquire(MAX_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new ReceiptReadingException("The model is busy with other receipts", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReceiptReadingException("Interrupted while waiting for the model", e);
        }
    }

    private static ReceiptFields toFields(ExtractedReceipt extracted) {
        return new ReceiptFields(absentToNull(extracted.type()), absentToNull(extracted.amount()),
                absentToNull(extracted.date()), absentToNull(extracted.counterparty()), absentToNull(extracted.taxId()),
                absentToNull(extracted.uniqueId()), absentToNull(extracted.suggestedCategory()),
                absentToNull(extracted.time()), absentToNull(extracted.paymentMethod()),
                absentToNull(extracted.installments()), absentToNull(extracted.cardBrand()), null);
    }

    // The JSON schema only allows strings and requires every field, and Ollama enforces it as a grammar: the
    // model cannot answer null, so it writes "null", or just punctuation like ", ", when a field is not visible
    private static String absentToNull(String value) {
        if (value == null || value.strip().equalsIgnoreCase("null")
                || value.chars().noneMatch(Character::isLetterOrDigit)) {
            return null;
        }
        return value;
    }

    private String toJson(ExtractedReceipt extracted) {
        try {
            return objectMapper.writeValueAsString(extracted);
        } catch (JsonProcessingException e) {
            throw new ReceiptReadingException("Could not serialize the model output", e);
        }
    }
}
