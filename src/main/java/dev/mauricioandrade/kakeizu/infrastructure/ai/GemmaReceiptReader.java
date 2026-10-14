package dev.mauricioandrade.kakeizu.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.FieldReading;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One GPU, one model: readings (and re-readings of a field) beyond {@code kakeizu.ai.max-concurrent-readings}
 * wait in line instead of piling up on Ollama, and give up if the line does not move.
 */
@ApplicationScoped
public class GemmaReceiptReader implements ReceiptReader {

    private static final Logger LOG = Logger.getLogger(GemmaReceiptReader.class);
    private static final long MAX_WAIT_SECONDS = 120;

    private final ReceiptExtractionService extractionService;
    private final FieldRereadService rereadService;
    private final ObjectMapper objectMapper;
    private final Semaphore modelSlots;

    public GemmaReceiptReader(ReceiptExtractionService extractionService, FieldRereadService rereadService,
                              ObjectMapper objectMapper,
                              @ConfigProperty(name = "kakeizu.ai.max-concurrent-readings") int maxConcurrentReadings) {
        this.extractionService = extractionService;
        this.rereadService = rereadService;
        this.objectMapper = objectMapper;
        this.modelSlots = new Semaphore(maxConcurrentReadings, true);
    }

    @Override
    public ModelReading read(byte[] image, String contentType) {
        long start = System.nanoTime();
        var extracted = withModelSlot("The model could not read the receipt",
                () -> extractionService.extract(toImage(image, contentType)));
        LOG.infof("[%s] Model read the receipt in %d ms", Correlation.id(), (System.nanoTime() - start) / 1_000_000);
        return new ModelReading(toFields(extracted), toJson(extracted));
    }

    @Override
    public FieldReading reread(byte[] image, String contentType, CheckDigitField field) {
        long start = System.nanoTime();
        var reread = withModelSlot("The model could not read the field again",
                () -> rereadService.reread(toImage(image, contentType), describe(field), field.length()));
        LOG.infof("[%s] Model read the %s again in %d ms", Correlation.id(), field,
                (System.nanoTime() - start) / 1_000_000);
        return new FieldReading(absentToNull(reread.value()), toJson(reread));
    }

    // The model only sees this name: say where the field is printed, as the extraction prompt does
    private static String describe(CheckDigitField field) {
        return switch (field) {
            case CNPJ -> "CNPJ of the company that received the money, printed next to its name"
                    + " like 12.345.678/0001-90";
            case NFE_ACCESS_KEY -> "access key (Chave de Acesso), printed in groups of 4 digits"
                    + " under \"Consulte pela Chave de Acesso\"";
        };
    }

    private <T> T withModelSlot(String failure, Supplier<T> call) {
        acquireModelSlot();
        try {
            return call.get();
        } catch (RuntimeException e) {
            throw new ReceiptReadingException(failure, e);
        } finally {
            modelSlots.release();
        }
    }

    private static Image toImage(byte[] image, String contentType) {
        return Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(image))
                .mimeType(contentType)
                .build();
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

    private String toJson(Object modelOutput) {
        try {
            return objectMapper.writeValueAsString(modelOutput);
        } catch (JsonProcessingException e) {
            throw new ReceiptReadingException("Could not serialize the model output", e);
        }
    }
}
