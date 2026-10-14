package dev.mauricioandrade.kakeizu.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
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
    // What the review page says while the model is still reading
    static final String READING_MARK = "is reading this photo";
    // Made up, with valid check digits: state 35, October 2026, CNPJ 23.010.551/0001-31
    static final String KEY = "35261023010551000131650010000123451123456787";
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
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId + "X", "OTHER", null, null, null, null, null));

        String review = upload(uniquePng());

        user().when().get(review)
                .then().statusCode(200)
                .body(containsString("Pagsmile"))
                .body(containsString("aria-invalid=\"true\""))
                .body(containsString("Some fields need fixing"));
    }

    @Test
    void aMisreadCnpjComesWithASuggestedCorrectionThatIsNotApplied() {
        // 2 read as 7 in the first digit; the re-read (mocked) answers nothing, so the search finds it
        modelReads(nfce("73.010.551/0001-31", KEY));

        user().when().get(upload(uniquePng()))
                .then().statusCode(200)
                .body(containsString("Suggested correction"))
                .body(containsString("likely misread digit"))
                .body(containsString("data-fill=\"taxId\" data-value=\"23010551000131\""))
                .body(containsString("<mark>2</mark>"))
                .body(containsString("value=\"73.010.551/0001-31\""));
    }

    @Test
    void aMisreadAccessKeyWithSeveralCandidatesListsThemWithoutChoosing() {
        modelReads(nfce("23.010.551/0001-31", KEY.substring(0, 30) + "7" + KEY.substring(31)));

        user().when().get(upload(uniquePng()))
                .then().statusCode(200)
                .body(containsString("More than one value passes the check"))
                .body(containsString("data-value=\"" + KEY + "\""))
                .body(not(containsString("Suggested correction")));
    }

    @Test
    void theSuggestionGoesOnceTheFieldIsFixed() {
        modelReads(nfce("73.010.551/0001-31", KEY));
        String review = upload(uniquePng());

        // Takes the suggestion, but the amount is still wrong: the page comes back without it
        user().formParams(Map.of("type", "NFCE", "amount", "eighty", "date", date, "counterparty", "Posto",
                        "taxId", "23010551000131", "uniqueId", KEY, "category", "FUEL_SUPPLY", "direction", "SALE"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(422)
                .body(not(containsString("Suggested correction")));
    }

    @Test
    void batchQueuesEveryPhotoAndMarksTheOneAlreadyUploaded() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER", null, null, null, null, null));
        byte[] seenBefore = uniquePng();
        upload(seenBefore);

        user().multiPart("images", "first.png", uniquePng(), "image/png")
                .multiPart("images", "again.png", seenBefore, "image/png")
                .when().post("/receipts/batch")
                .then().statusCode(200)
                .body(containsString("Reading"))
                .body(containsString("again.png was already uploaded"));
    }

    @Test
    void batchRefusesMoreThanFivePhotosBeforeReadingAny() {
        var request = user();
        for (int i = 0; i < 6; i++) {
            request = request.multiPart("images", i + ".png", uniquePng(), "image/png");
        }

        request.when().post("/receipts/batch")
                .then().statusCode(400).body(containsString("Up to 5 photos"));
        verifyNoInteractions(reader);
    }

    @Test
    void batchRefusesAFileThatIsNotAnImage() {
        user().multiPart("images", "ok.png", uniquePng(), "image/png")
                .multiPart("images", "notes.png", "not an image".getBytes(StandardCharsets.UTF_8), "image/png")
                .when().post("/receipts/batch")
                .then().statusCode(400).body(containsString("Only JPG and PNG"));
        verifyNoInteractions(reader);
    }

    @Test
    void todaysListShowsAmountsInReais() {
        modelReads(new ReceiptFields("PIX", "7654.3", date, "Pagsmile", null, pixId, "OTHER", null, null, null, null, null));
        upload(uniquePng());

        user().when().get("/")
                .then().statusCode(200)
                .body(containsString("7.654,30"))
                .body(not(containsString("7654.3")));
    }

    @Test
    void confirmedReceiptShowsUpInTheSummaryOfItsDay() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId + "X", "OTHER", null, null, null, null, null));
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
    void confirmedSaleShowsUpInTheSalesReportByTimeAndMethod() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER", "14:05", null, null,
                null, null));
        String review = upload(uniquePng());

        user().formParams(fields("PIX", "80,00", "Pagsmile", pixId, "OTHER"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(303);

        user().queryParam("from", date).queryParam("to", date).when().get("/report")
                .then().statusCode(200)
                .body(containsString("1 sale confirmed"))
                .body(containsString("14:00–14:59"))
                .body(containsString("<meter min=\"0\" max=\"100\" value=\"100\">"));
    }

    @Test
    void reportWithTheRangeBackwardsIsABadRequest() {
        user().queryParam("from", "2026-10-13").queryParam("to", "2026-10-01").when().get("/report")
                .then().statusCode(400).body(containsString("end date is before"));
    }

    @Test
    void invalidCorrectionShowsTheFormAgainWithWhatWasTyped() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER", null, null, null, null, null));
        String review = upload(uniquePng());

        user().formParams(fields("PIX", "eighty", "Typed by hand", pixId, "OTHER"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(422)
                .body(containsString("Unreadable amount"))
                .body(containsString("Typed by hand"));
    }

    @Test
    void rejectedReceiptCannotBeConfirmedLater() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER", null, null, null, null, null));
        String review = upload(uniquePng());

        user().when().post(review.replace("/review", "/reject")).then().statusCode(303);

        user().formParams(fields("PIX", "80.00", "Pagsmile", pixId, "OTHER"))
                .when().post(review.replace("/review", "/confirm"))
                .then().statusCode(409).body(containsString("already confirmed or rejected"));
        user().when().get(review).then().statusCode(200).body(containsString("Rejected"));
    }

    @Test
    void sameImageTwiceOpensTheExistingReceiptWithoutCallingTheModelAgain() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER", null, null, null, null, null));
        byte[] image = uniquePng();

        String first = upload(image);
        String second = user().multiPart("image", "receipt.png", image, "image/png")
                .when().post("/receipts")
                .then().statusCode(303)
                .extract().header("Location");

        assertTrue(second.endsWith(first + "?duplicate=true"), second);
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
    void photoOverTenMegabytesIsRefused() {
        byte[] big = Arrays.copyOf(PNG_SIGNATURE, 11 * 1024 * 1024);

        user().multiPart("image", "receipt.png", big, "image/png")
                .when().post("/receipts")
                .then().statusCode(400).body(containsString("up to 10 MB"));

        verifyNoInteractions(reader);
    }

    @Test
    void requestOverTheBodyLimitIsRefused() {
        byte[] huge = Arrays.copyOf(PNG_SIGNATURE, 31 * 1024 * 1024);

        try {
            user().multiPart("image", "receipt.png", huge, "image/png")
                    .when().post("/receipts")
                    .then().statusCode(413);
        } catch (Exception _) {
            // The server may close the connection before the client finishes sending: refused all the same.
            // A wrong status code is an AssertionError, not an Exception, so it still fails the test
        }

        verifyNoInteractions(reader);
    }

    @Test
    void modelOutageLeavesThePhotoToTypeWithAGenericMessage() {
        when(reader.read(any(), any())).thenThrow(new ReceiptReadingException("The model could not read the receipt",
                new ConnectException("Connection refused: localhost/127.0.0.1:11434")));

        // The upload still goes through: the photo waits on review, for the person to type it
        user().when().get(upload(uniquePng()))
                .then().statusCode(200)
                .body(containsString("Check that Ollama is running"))
                .body(not(containsString("11434")));
    }

    @Test
    void textFromTheImageIsEscapedNotExecuted() {
        // A receipt image can carry any text, and the model will happily copy it
        modelReads(new ReceiptFields("PIX", "80.00", date, "<script>alert('pwned')</script>", null, pixId, "OTHER", null, null, null, null, null));

        user().when().get(upload(uniquePng()))
                .then().statusCode(200)
                .body(containsString("&lt;script&gt;"))
                .body(not(containsString("<script>alert")));
    }

    @Test
    void imageIsServedWithTheTypeDetectedFromItsBytes() {
        modelReads(new ReceiptFields("PIX", "80.00", date, "Pagsmile", null, pixId, "OTHER", null, null, null, null, null));
        byte[] image = uniquePng();

        // The client claims JPEG; the bytes say PNG
        String location = user().multiPart("image", "receipt.jpg", image, "image/jpeg")
                .when().post("/receipts")
                .then().statusCode(303).extract().header("Location");
        String id = location.substring(location.indexOf('=') + 1);

        user().when().get("/receipts/" + id + "/image")
                .then().statusCode(200)
                .contentType(startsWith("image/png"))
                .header("X-Content-Type-Options", "nosniff");
        verify(reader, timeout(5000)).read(any(), eq("image/png"));
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

    // Uploads, waits for the background reading, and returns the review path
    private String upload(byte[] image) {
        String location = user().multiPart("image", "receipt.png", image, "image/png")
                .when().post("/receipts")
                .then().statusCode(303).header("Location", containsString("/?uploaded="))
                .extract().header("Location");
        String review = "/receipts/" + location.substring(location.indexOf('=') + 1) + "/review";
        for (int i = 0; i < 100 && user().get(review).asString().contains(READING_MARK); i++) {
            sleep();
        }
        return review;
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReceiptFields nfce(String cnpj, String key) {
        return new ReceiptFields("NFCE", "129.80", date, "Posto Exemplo", cnpj, key, "FUEL_SUPPLY", "10:15", "CASH",
                "1", null, null);
    }

    private void modelReads(ReceiptFields fields) {
        when(reader.read(any(), any())).thenReturn(new ModelReading(fields, "{}"));
    }

    private Map<String, String> fields(String type, String amount, String counterparty, String uniqueId,
                                       String category) {
        // No shop CNPJ in tests and no CNPJ on these Pix: the person says it is a sale
        return Map.of("type", type, "amount", amount, "date", date, "counterparty", counterparty,
                "taxId", "", "uniqueId", uniqueId, "category", category, "time", "14:05", "direction", "SALE");
    }

    private static byte[] uniquePng() {
        byte[] image = Arrays.copyOf(PNG_SIGNATURE, 64);
        byte[] noise = new byte[56];
        RANDOM.nextBytes(noise);
        System.arraycopy(noise, 0, image, PNG_SIGNATURE.length, noise.length);
        return image;
    }
}
