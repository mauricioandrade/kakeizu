package dev.mauricioandrade.kakeizu.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ChequeReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Cmc7;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.FieldRecovery;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.Payment;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Ambiguous;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Recovered;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Unrecoverable;
import dev.mauricioandrade.kakeizu.domain.model.RecoverySource;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@TestTransaction
class PanacheReceiptRepositoryTest {

    // Made up, with valid check digits
    static final String CMC7 = "00112341 0180001235 200000123455";

    static final LocalDate DAY = LocalDate.of(2022, 8, 12);
    static final Payment CREDIT_5X_AT_NOON = new Payment(LocalTime.NOON, PaymentMethod.CREDIT, 5, CardBrand.VISA);
    static final ReceiptFields INVALID_FIELDS = new ReceiptFields("NFCE", "146.46", "2020-01-01", "LOJA RUA",
            "0000000000000000000000000000000", null, "GOODS", null, null, null, null, null);

    private final ReceiptRepository repository;

    PanacheReceiptRepositoryTest(ReceiptRepository repository) {
        this.repository = repository;
    }

    @Test
    void keepsWhatWasFoundForFieldsThatFailedTheirCheckDigits() {
        var recoveries = List.of(
                new FieldRecovery(CheckDigitField.CNPJ, "23010551000181",
                        new Recovered("23010551000131", RecoverySource.REREAD), "{\"value\":\"23010551000131\"}"),
                new FieldRecovery(CheckDigitField.NFE_ACCESS_KEY, "3526", new Ambiguous(List.of("a", "b")), null),
                new FieldRecovery(CheckDigitField.CNPJ, "1", new Unrecoverable(), null));
        var read = ReceiptRecord.reading(randomSha256(), "image/png", Instant.parse("2026-10-13T12:00:00Z"))
                .read(new ModelReading(INVALID_FIELDS, "{}"), Category.GOODS, recoveries);
        repository.save(read);

        assertEquals(recoveries, repository.findById(read.id()).orElseThrow().recoveries());
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
                new PixReceipt(new Money("300"), DAY, "Melhor Grão Stone", null),
                new CardReceipt(new Money("500"), DAY, "Melhor Grão Stone", new Cnpj("69.034.668/0001-56"), null),
                new CardReceipt(new Money("89.82"), DAY, "Toesca Supermercado", null, "434817"),
                new ChequeReceipt(new Money("9657"), DAY, "Transportadora Exemplo", null),
                new ChequeReceipt(new Money("9657"), DAY, "Transportadora Exemplo", new Cmc7(CMC7)));
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
                first.confirmation(), first.imageSha256(), "image/png", null, List.of(), Instant.now());

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
                () -> repository.save(secondPhotoOfTheSamePix.confirm(valid(pix, Payment.unknown(), Direction.SALE))));
    }

    @Test
    void sameCardAuthorizationCodeOnDifferentSlipsIsAllowed() {
        var card = new CardReceipt(new Money("10"), DAY, "Loja", new Cnpj("69.034.668/0001-56"), "2UP8IQ");
        confirm(pending(INVALID_FIELDS, Instant.now()), card);

        assertDoesNotThrow(() -> confirm(pending(INVALID_FIELDS, Instant.now()), card));
    }

    @Test
    void sameChequeConfirmedTwiceIsRejectedByTheDatabase() {
        var cheque = new ChequeReceipt(new Money("9657"), DAY, "Transportadora Exemplo", new Cmc7(CMC7));
        confirm(pending(INVALID_FIELDS, Instant.now()), cheque);

        assertThrows(DuplicateReceiptException.class, () -> confirm(pending(INVALID_FIELDS, Instant.now()), cheque));
    }

    @Test
    void pixSlipsWithoutEndToEndIdAreNotDuplicatesOfEachOther() {
        var pix = new PixReceipt(new Money("300"), DAY, "Melhor Grão Stone", null);
        confirm(pending(INVALID_FIELDS, Instant.now()), pix);

        assertDoesNotThrow(() -> confirm(pending(INVALID_FIELDS, Instant.now()), pix));
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
    void paymentAndDirectionRoundTripIncludingWhatIsUnknown() {
        var card = new CardReceipt(new Money("13"), DAY, "L P Trabalhador", null, "375579");
        var debit = new Payment(LocalTime.of(20, 59), PaymentMethod.DEBIT, 1, CardBrand.VISA);
        var nothingShown = Payment.unknown();

        var first = confirm(pending(INVALID_FIELDS, Instant.now()), card, debit, Direction.SALE);
        var second = confirm(pending(INVALID_FIELDS, Instant.now()), card, nothingShown, Direction.EXPENSE);

        assertEquals(first.confirmation(), repository.findById(first.id()).orElseThrow().confirmation());
        assertEquals(second.confirmation(), repository.findById(second.id()).orElseThrow().confirmation());
    }

    @Test
    void salesQueryKeepsSalesInTheRangeBothEndsIncluded() {
        // Far from DAY: other tests commit confirmed receipts on it
        var first = LocalDate.of(2019, 4, 1);
        var last = LocalDate.of(2019, 4, 30);
        var sale = confirmOn(first, Direction.SALE);
        var lastDaySale = confirmOn(last, Direction.SALE);
        confirmOn(first, Direction.EXPENSE);
        confirmOn(last.plusDays(1), Direction.SALE);

        assertEquals(Set.of(sale.id(), lastDaySale.id()), repository.findConfirmedSalesIssuedBetween(first, last).stream()
                .map(ReceiptRecord::id).collect(Collectors.toSet()));
    }

    private ReceiptRecord confirmOn(LocalDate day, Direction direction) {
        var card = new CardReceipt(new Money("10"), day, "Loja", new Cnpj("69.034.668/0001-56"), null);
        return confirm(pending(INVALID_FIELDS, Instant.now()), card, CREDIT_5X_AT_NOON, direction);
    }

    @Test
    void queryParametersAreNotInterpretedAsSql() {
        repository.save(pending(INVALID_FIELDS, Instant.now()));

        assertTrue(repository.findByImageSha256("' OR '1'='1").isEmpty());
        assertTrue(repository.findConfirmedByUniqueId(ReceiptType.PIX, "x' OR 1=1 --").isEmpty());
    }

    private ReceiptRecord confirm(ReceiptRecord pending, Receipt receipt) {
        return confirm(pending, receipt, CREDIT_5X_AT_NOON, Direction.SALE);
    }

    private ReceiptRecord confirm(ReceiptRecord pending, Receipt receipt, Payment payment, Direction direction) {
        repository.save(pending);
        var confirmed = pending.confirm(valid(receipt, payment, direction));
        repository.save(confirmed);
        return confirmed;
    }

    private static ExtractionResult valid(Receipt receipt, Payment payment, Direction direction) {
        return new ExtractionResult(INVALID_FIELDS, Optional.of(receipt), Category.GOODS, Optional.of(payment),
                direction, List.of());
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
