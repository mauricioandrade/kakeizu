package dev.mauricioandrade.kakeizu.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;

@ApplicationScoped
public class GemmaReceiptReader implements ReceiptReader {

    private final ReceiptExtractionService extractionService;
    private final ObjectMapper objectMapper;

    public GemmaReceiptReader(ReceiptExtractionService extractionService, ObjectMapper objectMapper) {
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelReading read(byte[] image, String contentType) {
        ExtractedReceipt extracted;
        try {
            extracted = extractionService.extract(Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(image))
                    .mimeType(contentType)
                    .build());
        } catch (RuntimeException e) {
            throw new ReceiptReadingException("The model could not read the receipt", e);
        }
        return new ModelReading(toFields(extracted), toJson(extracted));
    }

    private static ReceiptFields toFields(ExtractedReceipt extracted) {
        return new ReceiptFields(extracted.type(), extracted.amount(), extracted.date(), extracted.counterparty(),
                extracted.taxId(), extracted.uniqueId(), extracted.suggestedCategory());
    }

    private String toJson(ExtractedReceipt extracted) {
        try {
            return objectMapper.writeValueAsString(extracted);
        } catch (JsonProcessingException e) {
            throw new ReceiptReadingException("Could not serialize the model output", e);
        }
    }
}
