package dev.mauricioandrade.kakeizu.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Upload → review → confirm/reject → summary through HTTP, with the model mocked at the domain port.
 * Each test uses its own image bytes, Pix ID and issue date, so tests never see each other's receipts.
 */
@QuarkusTest
class ReceiptWebFlowTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @InjectMock
    ReceiptReader reader;

    private String session;
    private String date;
    private String pixId;

    @BeforeEach
    void setUp() {
        session = TestLogin.sessionCookie();
        date = LocalDate.of(2001, 1, 1).plusDays(RANDOM.nextInt(7000)).toString();
        pixId = "E" + UUID.randomUUID().toString().replace("-", "").substring(1).toUpperCase();
    }

    @Test
    void uploadGoesToReviewWhereWhatIsWrongIsHighlighted() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId + "X", "OTHER"));

        String review = upload(uniquePng());

        user().when().get(review)
                .then().statusCode(200)
                .body(containsString("Pagsmile"))
                .body(containsString("aria-invalid=\"true\""))
                .body(containsString("Some fields need fixing"));
    }

    @Test
    void confirmedReceiptShowsUpInTheSummaryOfItsDay() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId + "X", "OTHER"));
        String review = upload(uniquePng());

        user().formParams(fields("PIX", "1.234,56", "Pagsmile", pixId, "SERVICES"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(303).header("Location", endsWith("/"));

        user().queryParam("date", date).when().get("/summary")
                .then().statusCode(200)
                .body(containsString("1.234,56"))
                .body(containsString("Services"))
                .body(containsString("1 confirmed receipt issued"));
    }

    @Test
    void invalidCorrectionShowsTheFormAgainWithWhatWasTyped() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER"));
        String review = upload(uniquePng());

        user().formParams(fields("PIX", "eighty", "Typed by hand", pixId, "OTHER"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(422)
                .body(containsString("Unreadable amount"))
                .body(containsString("Typed by hand"));
    }

    @Test
    void rejectedReceiptCannotBeConfirmedLater() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER"));
        String review = upload(uniquePng());

        user().when().post(review.replace("/review", "/reject")).then().statusCode(303);

        user().formParams(fields("PIX", "80.00", "Pagsmile", pixId, "OTHER"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(409).body(containsString("already confirmed or rejected"));
        user().when().get(review).then().statusCode(200).body(containsString("Rejected"));
    }

    @Test
    void sameImageTwiceOpensTheExistingReceiptWithoutCallingTheModelAgain() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER"));
        byte[] image = uniquePng();

        String first = upload(image);
        String second = upload(image);

        assertEquals(first + "?duplicate=true", second);
        verify(reader, times(1)).read(any(), any());
        user().when().get(second).then().statusCode(200).body(containsString("already uploaded"));
    }

    @Test
    void fileThatIsNotAnImageNeverReachesTheModel() {
        user().multiPart("image", "receipt.png", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8), "image/png")
                .when().post("/receipts")
                .then().statusCode(400).body(containsString("Only JPG and PNG"));

        verifyNoInteractions(reader);
    }

    @Test
    void oversizedUploadIsRefused() {
        byte[] huge = Arrays.copyOf(PNG_SIGNATURE, 11 * 1024 * 1024);

        user().multiPart("image", "receipt.png", huge, "image/png")
                .when().post("/receipts")
                .then().statusCode(413);

        verifyNoInteractions(reader);
    }

    @Test
    void modelOutageShowsAGenericMessage() {
        when(reader.read(any(), any())).thenThrow(new ReceiptReadingException("The model could not read the receipt",
                new ConnectException("Connection refused: localhost/127.0.0.1:11434")));

        user().multiPart("image", "receipt.png", uniquePng(), "image/png")
                .when().post("/receipts")
                .then().statusCode(503)
                .body(containsString("Check that Ollama is running"))
                .body(not(containsString("11434")));
    }

    @Test
    void textFromTheImageIsEscapedNotExecuted() {
        // A receipt image can carry any text, and the model will happily copy it
        modelReads(new ReceiptFields("PIX", "80.00", date, "<script>alert('pwned')</script>", null, pixId, "OTHER"));

        user().when().get(upload(uniquePng()))
                .then().statusCode(200)
                .body(containsString("&lt;script&gt;"))
                .body(not(containsString("<script>alert")));
    }

    @Test
    void imageIsServedWithTheTypeDetectedFromItsBytes() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER"));
        byte[] image = uniquePng();

        // The client claims JPEG; the bytes say PNG
        String review = user().multiPart("image", "receipt.jpg", image, "image/jpeg")
                .when().post("/receipts")
                .then().statusCode(303).extract().header("Location");

        user().when().get(review.replace("/review", "/image"))
                .then().statusCode(200)
                .contentType(startsWith("image/png"))
                .header("X-Content-Type-Options", "nosniff");
        verify(reader).read(any(), eq("image/png"));
    }

    @Test
    void unknownReceiptIsNotFound() {
        user().when().get("/receipts/" + UUID.randomUUID() + "/review")
                .then().statusCode(404).body(containsString("Receipt not found"));
    }

    @Test
    void pagesForbidInlineScriptsAndFraming() {
        user().when().get("/")
                .then().statusCode(200)
                .header("Content-Security-Policy", containsString("default-src 'self'"))
                .header("Content-Security-Policy", containsString("frame-ancestors 'none'"));
    }

    private RequestSpecification user() {
        return given().redirects().follow(false).cookie(TestLogin.COOKIE, session);
    }

    private String upload(byte[] image) {
        return user().multiPart("image", "receipt.png", image, "image/png")
                .when().post("/receipts")
                .then().statusCode(303)
                .extract().header("Location");
    }

    private void modelReads(ReceiptFields fields) {
        when(reader.read(any(), any())).thenReturn(new ModelReading(fields, "{}"));
    }

    private Map<String, String> fields(String type, String amount, String counterparty, String uniqueId,
                                       String category) {
        return Map.of("type", type, "amount", amount, "date", date, "counterparty", counterparty,
                "taxId", "", "uniqueId", uniqueId, "category", category);
    }

    private static byte[] uniquePng() {
        byte[] image = Arrays.copyOf(PNG_SIGNATURE, 64);
        byte[] noise = new byte[56];
        RANDOM.nextBytes(noise);
        System.arraycopy(noise, 0, image, PNG_SIGNATURE.length, noise.length);
        return image;
    }
}
