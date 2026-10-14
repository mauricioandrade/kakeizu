package dev.mauricioandrade.kakeizu.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mauricioandrade.kakeizu.domain.model.ChequeReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptValidatorTest {

    static final Clock TODAY = Clock.fixed(ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneId.of("America/Sao_Paulo")).toInstant(),
            ZoneId.of("America/Sao_Paulo"));
    static final Cnpj ISSUER = new Cnpj("11.222.333/0001-81");
    static final NfeAccessKey NFE_KEY = new NfeAccessKey("35221011222333000181550010000123451123456787");
    static final NfeAccessKey NFCE_KEY = new NfeAccessKey("35221011222333000181650010000123451123456780");

    final ReceiptValidator validator = new ReceiptValidator(TODAY);

    @Test
    void consistentInvoiceHasNoIssues() {
        assertEquals(List.of(), validator.validate(invoice(LocalDate.of(2022, 10, 5), ISSUER, NFE_KEY)));
    }

    @Test
    void futureDateIsAnError() {
        var pix = new PixReceipt(new Money("80"), LocalDate.of(2026, 10, 14), "Pagsmile",
                new PixEndToEndId("E00416968202208120422Mmzi9rPYPLT"));

        assertEquals(List.of(ValidationIssue.error("date", "Date is in the future: 2026-10-14")), validator.validate(pix));
    }

    @Test
    void postDatedChequeIsOnlyAWarning() {
        var cheque = new ChequeReceipt(new Money("9657"), LocalDate.of(2026, 11, 13), "Transportadora Exemplo", null);

        assertEquals(List.of(ValidationIssue.warning("date", "Post-dated cheque: it can only be deposited on 2026-11-13")),
                validator.validate(cheque));
    }

    @Test
    void chequeOlderThanSixMonthsIsOnlyAWarning() {
        // The model read the handwritten "26" of a real cheque as 2022
        var cheque = new ChequeReceipt(new Money("9657"), LocalDate.of(2022, 10, 13), "Transportadora Exemplo", null);

        assertEquals(List.of(ValidationIssue.warning("date",
                        "Cheque dated more than 6 months ago: check the year, an old cheque may no longer be cashed")),
                validator.validate(cheque));
    }

    @Test
    void chequeFromLastMonthHasNoIssues() {
        var cheque = new ChequeReceipt(new Money("9657"), LocalDate.of(2026, 9, 13), "Transportadora Exemplo", null);

        assertEquals(List.of(), validator.validate(cheque));
    }

    @Test
    void oldPixIsNotFlagged() {
        var pix = new PixReceipt(new Money("80"), LocalDate.of(2022, 8, 12), "Pagsmile",
                new PixEndToEndId("E00416968202208120422Mmzi9rPYPLT"));

        assertEquals(List.of(), validator.validate(pix));
    }

    @Test
    void dateOutsideTheKeyMonthIsAWarning() {
        var issues = validator.validate(invoice(LocalDate.of(2022, 11, 5), ISSUER, NFE_KEY));

        assertEquals(List.of(ValidationIssue.warning("date", "Date does not match the access key month (2022-10)")), issues);
    }

    @Test
    void cnpjDifferentFromTheKeyIsAWarning() {
        var issues = validator.validate(invoice(LocalDate.of(2022, 10, 5), new Cnpj("23.010.551/0001-31"), NFE_KEY));

        assertEquals(List.of(ValidationIssue.warning("taxId", "CNPJ does not match the one in the access key (11222333000181)")),
                issues);
    }

    @Test
    void nfceKeyOnAnInvoiceIsAWarning() {
        var issues = validator.validate(invoice(LocalDate.of(2022, 10, 5), ISSUER, NFCE_KEY));

        assertEquals(List.of(ValidationIssue.warning("uniqueId", "Access key is from model 65, expected 55")), issues);
    }

    @Test
    void couponIsCheckedAgainstItsKeyToo() {
        var coupon = new FiscalCoupon(new Money("146,46"), LocalDate.of(2022, 10, 1), "Loja", ISSUER, NFE_KEY);

        assertEquals(List.of(ValidationIssue.warning("uniqueId", "Access key is from model 55, expected 65")),
                validator.validate(coupon));
    }

    private static FiscalInvoice invoice(LocalDate issuedOn, Cnpj cnpj, NfeAccessKey key) {
        return new FiscalInvoice(new Money("1.500,00"), issuedOn, "Distribuidora Exemplo", cnpj, key);
    }
}
