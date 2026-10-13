package dev.mauricioandrade.kakeizu.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.mauricioandrade.kakeizu.infrastructure.ai.ReceiptExtractionService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Temporary (phase 2): proves that Ollama + Gemma can read a receipt. Removed once the real flow exists.
 */
@Path("/spike/extract")
public class SpikeResource {

    private final ReceiptExtractionService extractionService;
    private final ObjectMapper objectMapper;

    public SpikeResource(ReceiptExtractionService extractionService, ObjectMapper objectMapper) {
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public String extract(@RestForm("image") FileUpload upload) throws IOException {
        Image image = Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(Files.readAllBytes(upload.uploadedFile())))
                .mimeType(upload.contentType())
                .build();
        return toJson(extractionService.extract(image));
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }
}
