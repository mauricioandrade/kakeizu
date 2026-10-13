package dev.mauricioandrade.kakeizu.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@TestTransaction
class PanacheReceiptRepositoryTest {

    static final LocalDate DAY = LocalDate.of(2022, 8, 12);
    static final ReceiptFields INVALID_FIELDS = new ReceiptFields("NFCE", "146.46", "2020-01-01", "LOJA RUA",
            "0000000000000000000000000000000", null, "GOODS");

    private final ReceiptRepository repository;

    PanacheReceiptRepositoryTest(ReceiptRepository repository) {
        this.repository = repository;
    }

    @Test
    void pendingReceiptKeepsInvalidFieldsForReview() {
        var pending = pending(INVALID_FIELDS, Instant.parse("2026-10-13T12:00:00Z"));
        repository.save(pending);

        var found = repository.findById(pending.id()).orElseThrow();

        assertEquals(pending, found);
        assertEquals("0000000000000000000000000000000", found.fields().taxId());
    }

    static Stream<Receipt> everyReceiptType() {
        return Stream.of(
                new FiscalInvoice(new Money("1.500,00"), DAY, "Distribuidora Exemplo", new Cnpj("11.222.333/0001-81"),
                        new NfeAccessKey("35221011222333000181550010000123451123456787")),
                new FiscalCoupon(new Money("42,90"), DAY, "Mercadinho Exemplo", new Cnpj("12.ABC.345/01DE-35"),
                        new NfeAccessKey("35261012ABC34501DE35651010000050961123456785")),
                new PixReceipt(new Money("80"), DAY, "Pagsmile", new PixEndToEndId("E00416968202208120422Mmzi9rPYPLT")),
                new CardReceipt(new Money("500"), DAY, "Melhor Grão Stone", new Cnpj("69.034.668/0001-56"), null));
    }

    @ParameterizedTest
    @MethodSource("everyReceiptType")
    void confirmedReceiptRoundTrips(Receipt receipt) {
        var confirmed = confirm(pending(INVALID_FIELDS, Instant.now()), receipt);

        assertEquals(confirmed, repository.findById(confirmed.id()).orElseThrow());
    }

    @Test
    void findsByImageHash() {
        var pending = pending(INVALID_FIELDS, Instant.now());
        repository.save(pending);

        assertEquals(pending.id(), repository.findByImageSha256(pending.imageSha256()).orElseThrow().id());
    }

    @Test
    void sameImageTwiceIsRejectedByTheDatabase() {
        var first = pending(INVALID_FIELDS, Instant.now());
        repository.save(first);
        var sameImage = new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.PENDING_REVIEW, INVALID_FIELDS, Category.OTHER,
                first.confirmedReceipt(), first.imageSha256(), "image/png", null, Instant.now());

        var e = assertThrows(DuplicateReceiptException.class, () -> repository.save(sameImage));
        assertEquals("This image was already uploaded", e.getMessage());
    }

    @Test
    void samePixConfirmedTwiceIsRejectedByTheDatabase() {
        var pix = everyReceiptType().filter(PixReceipt.class::isInstance).findFirst().orElseThrow();
        confirm(pending(INVALID_FIELDS, Instant.now()), pix);

        var secondPhotoOfTheSamePix = pending(INVALID_FIELDS, Instant.now());
        repository.save(secondPhotoOfTheSamePix);

        assertTrue(repository.findConfirmedByUniqueId(ReceiptType.PIX, "E00416968202208120422Mmzi9rPYPLT").isPresent());
        assertThrows(DuplicateReceiptException.class,
                () -> repository.save(secondPhotoOfTheSamePix.confirm(INVALID_FIELDS, pix, Category.OTHER)));
    }

    @Test
    void sameCardAuthorizationCodeOnDifferentSlipsIsAllowed() {
        var card = new CardReceipt(new Money("10"), DAY, "Loja", new Cnpj("69.034.668/0001-56"), "2UP8IQ");
        confirm(pending(INVALID_FIELDS, Instant.now()), card);

        assertDoesNotThrow(() -> confirm(pending(INVALID_FIELDS, Instant.now()), card));
    }

    @Test
    void listsCreatedInRangeNewestFirst() {
        var morning = pending(INVALID_FIELDS, Instant.parse("2020-03-10T11:00:00Z"));
        var evening = pending(INVALID_FIELDS, Instant.parse("2020-03-10T22:00:00Z"));
        var nextDay = pending(INVALID_FIELDS, Instant.parse("2020-03-11T03:00:00Z"));
        Stream.of(morning, evening, nextDay).forEach(repository::save);

        // Far from "now": other tests commit receipts created today
        var found = repository.findCreatedBetween(Instant.parse("2020-03-10T03:00:00Z"), Instant.parse("2020-03-11T03:00:00Z"));

        assertEquals(List.of(evening.id(), morning.id()), found.stream().map(ReceiptRecord::id).toList());
    }

    @Test
    void summaryQueryOnlySeesConfirmed() {
        var pix = everyReceiptType().filter(PixReceipt.class::isInstance).findFirst().orElseThrow();
        var confirmed = confirm(pending(INVALID_FIELDS, Instant.now()), pix);
        repository.save(pending(INVALID_FIELDS, Instant.now()));
        repository.save(pending(INVALID_FIELDS, Instant.now()).reject());

        assertEquals(List.of(confirmed.id()), repository.findConfirmedIssuedOn(DAY).stream().map(ReceiptRecord::id).toList());
    }

    @Test
    void queryParametersAreNotInterpretedAsSql() {
        repository.save(pending(INVALID_FIELDS, Instant.now()));

        assertTrue(repository.findByImageSha256("' OR '1'='1").isEmpty());
        assertTrue(repository.findConfirmedByUniqueId(ReceiptType.PIX, "x' OR 1=1 --").isEmpty());
    }

    private ReceiptRecord confirm(ReceiptRecord pending, Receipt receipt) {
        repository.save(pending);
        var confirmed = pending.confirm(pending.fields(), receipt, Category.GOODS);
        repository.save(confirmed);
        return confirmed;
    }

    private static ReceiptRecord pending(ReceiptFields fields, Instant createdAt) {
        return ReceiptRecord.pending(fields, Category.GOODS, randomSha256(), "image/png",
                "{\"type\":\"NFCE\"}", createdAt);
    }

    private static String randomSha256() {
        var bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
