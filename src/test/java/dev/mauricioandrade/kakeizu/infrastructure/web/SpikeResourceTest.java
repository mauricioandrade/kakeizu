package dev.mauricioandrade.kakeizu.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.mauricioandrade.kakeizu.infrastructure.ai.ExtractedReceipt;
import dev.mauricioandrade.kakeizu.infrastructure.ai.ReceiptExtractionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * Wiring only: the model is mocked. Real extraction is checked manually against Ollama.
 */
@QuarkusTest
class SpikeResourceTest {

    @InjectMock
    ReceiptExtractionService extractionService;

    @Test
    void returnsWhatTheModelExtracted() {
        when(extractionService.extract(any())).thenReturn(new ExtractedReceipt(
                "PIX", "80.00", "2022-08-12", "Pagsmile", "23010551000131", "E00416968202208120422Mmzi9rPYPLT", "OTHER"));

        given()
                .multiPart("image", new File("src/test/resources/samples/pix-inter.png"), "image/png")
                .when().post("/spike/extract")
                .then()
                .statusCode(200)
                .body("type", equalTo("PIX"))
                .body("amount", equalTo("80.00"));
    }
}
