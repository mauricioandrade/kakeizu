package dev.mauricioandrade.kakeizu.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.Payment;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExtractionMapperTest {

    // The shop is Melhor Grão Stone: its own card slips are sales, other CNPJs are expenses
    static final Cnpj SHOP = new Cnpj("69.034.668/0001-56");

    final ExtractionMapper mapper = new ExtractionMapper(new ReceiptValidator(ReceiptValidatorTest.TODAY),
            Optional.of(SHOP));

    @Test
    void gemmaOutputForThePixSampleIsCaughtOnTheTransactionId() {
        // Real gemma3:4b output for samples/pix-inter.png
        var result = mapper.map(new ReceiptFields("PIX", "80.00", "2022-08-12", "Pagsmile",
                "23.010.551/0001-31", "E00416968202208120422Mzmiz9rPYPLT", "OTHER", null, null, null, null, null));

        assertFalse(result.isValid());
        assertTrue(result.receipt().isEmpty());
        assertEquals(List.of(ValidationIssue.error("uniqueId", "Pix transaction ID must have 32 characters, got 33")),
                result.issues());
    }

    @Test
    void correctedPixBecomesAReceipt() {
        var result = mapper.map(new ReceiptFields("PIX", "80,00", "2022-08-12", "Pagsmile",
                null, "E00416968202208120422Mmzi9rPYPLT", "OTHER", null, null, null, null, "SALE"));

        assertTrue(result.isValid());
        var pix = assertInstanceOf(PixReceipt.class, result.receipt().orElseThrow());
        assertEquals(new Money("80.00"), pix.amount());
        assertEquals("E00416968202208120422Mmzi9rPYPLT", pix.endToEndId().value());
    }

    @Test
    void validInvoice() {
        var result = mapper.map(new ReceiptFields("nfe", "1.500,00", "2022-10-05", "Distribuidora Exemplo",
                "11.222.333/0001-81", "35221011222333000181550010000123451123456787", "FUEL_SUPPLY", null, null, null, null, null));

        assertEquals(List.of(), result.issues());
        assertInstanceOf(FiscalInvoice.class, result.receipt().orElseThrow());
        assertEquals(Category.FUEL_SUPPLY, result.category());
    }

    @Test
    void invoiceWithWarningsIsStillValid() {
        var result = mapper.map(new ReceiptFields("NFE", "1500.00", "2022-11-05", "Distribuidora Exemplo",
                "11.222.333/0001-81", "35221011222333000181550010000123451123456787", "GOODS", null, null, null, null, null));

        assertTrue(result.isValid());
        assertTrue(result.receipt().isPresent());
        assertEquals(ValidationIssue.Severity.WARNING, result.issues().getFirst().severity());
    }

    @Test
    void validCouponWithAlphanumericCnpj() {
        var result = mapper.map(new ReceiptFields("NFCE", "42,90", "2026-10-12", "Mercadinho Exemplo",
                "12.ABC.345/01DE-35", "3526 1012 ABC3 4501 DE35 6510 1000 0050 9611 2345 6785", "GOODS", null, null, null, null, null));

        assertEquals(List.of(), result.issues());
        var coupon = assertInstanceOf(FiscalCoupon.class, result.receipt().orElseThrow());
        assertEquals("35261012ABC34501DE35651010000050961123456785", coupon.accessKey().value());
    }

    @Test
    void gemmaOutputForTheDanfeTemplateIsCaughtOnCnpjAndKey() {
        // Real gemma3:4b output for samples/nfce-template.png: the CNPJ is blank on the image, the model invented zeros
        var result = mapper.map(new ReceiptFields("NFCE", "146.46", "2020-01-01", "LOJA RUA",
                "0000000000000000000000000000000", "00000-0000-0000-0000-00-0000-0000-0000-000-0", "GOODS", null, null, null, null, null));

        assertEquals(List.of("taxId", "uniqueId", "direction"), errorFields(result));
    }

    @Test
    void cardWithoutAuthorizationCode() {
        var result = mapper.map(new ReceiptFields("CARD", "500.00", "2018-03-15", "Melhor Grão Stone",
                "69.034.668/0001-56", "  ", "GOODS", null, "CREDIT", null, null, null));

        var card = assertInstanceOf(CardReceipt.class, result.receipt().orElseThrow());
        assertNull(card.authorizationCode());
    }

    @Test
    void cardFromABankAppHasNoCnpjAndIsStillValid() {
        // Real gemma4:e4b output for a card purchase in the Nubank app, which never shows the merchant CNPJ
        var result = mapper.map(new ReceiptFields("CARD", "89.82", "2026-09-28", "TOESCA SUPERMERCADO EI",
                null, "434817", "GOODS", "14:14", "CREDIT", "1", null, "EXPENSE"));

        assertTrue(result.isValid());
        var card = assertInstanceOf(CardReceipt.class, result.receipt().orElseThrow());
        assertNull(card.merchantCnpj());
        assertEquals("434817", card.authorizationCode());
    }

    @Test
    void cardCnpjIsStillCheckedWhenPresent() {
        // Real gemma4:e4b output for a Cielo slip: CNPJ 13.458.397/0001-01 misread, caught by the check digits
        var result = mapper.map(new ReceiptFields("CARD", "13,00", "2016-08-21", "L P TRABALHADOR",
                "13.438.977/0001-00", "375759", "OTHER", "20:59", "DEBIT", "1", "VISA", "SALE"));

        assertEquals(List.of("taxId"), errorFields(result));
    }

    @Test
    void gemmaOutputForTheLargeStoneSlipIsCaughtOnTheCnpj() {
        // Real gemma3:4b output for samples/card-stone-large.png: a placeholder CNPJ printed on a demo slip
        var result = mapper.map(new ReceiptFields("CARD", "25000.00", "2018-03-15", "DAIANE",
                "11111.111/0001-11", "A6D25ED683JAS", "OTHER", "11:23", "CREDIT", "5", "MASTERCARD", "SALE"));

        assertEquals(List.of("taxId"), errorFields(result));
    }

    @Test
    void allFieldsMissingReportsEachOne() {
        var result = mapper.map(new ReceiptFields(null, null, null, null, null, null, null, null, null, null, null, null));

        assertTrue(result.receipt().isEmpty());
        assertEquals(List.of("type", "amount", "date", "counterparty", "direction"), errorFields(result));
        assertEquals(Category.OTHER, result.category());
    }

    @Test
    void missingTypeSpecificFields() {
        var result = mapper.map(new ReceiptFields("NFE", "10.00", "2022-10-05", "X", null, null, null, null, null, null, null, null));

        assertEquals(List.of("taxId", "uniqueId", "direction"), errorFields(result));
    }

    @Test
    void unknownTypeAndNonIsoDateAreErrors() {
        // What gemma3:4b returned before the prompt explained the fields
        var result = mapper.map(new ReceiptFields("UNKNOWN", "80.00", "Sexta, 12/08/2022", "Pagsmile", null, null, null, null, null, null, null, null));

        assertEquals(List.of(
                ValidationIssue.error("type", "Receipt type not recognized: UNKNOWN"),
                ValidationIssue.error("date", "Date must be yyyy-MM-dd, got: Sexta, 12/08/2022"),
                ValidationIssue.error("direction", "Choose whether the shop received or paid this money")),
                result.issues());
    }

    @Test
    void unknownCategoryFallsBackToOtherWithAWarning() {
        var result = mapper.map(new ReceiptFields("CARD", "500.00", "2018-03-15", "Melhor Grão Stone",
                "69.034.668/0001-56", "2UP8IQ", "CRÉDITO", null, "CREDIT", null, null, null));

        assertTrue(result.isValid());
        assertEquals(Category.OTHER, result.category());
        assertEquals(List.of(ValidationIssue.warning("category", "Category not recognized, using OTHER: CRÉDITO")),
                result.issues());
    }

    @Test
    void futureDateBlocksTheReceipt() {
        var result = mapper.map(new ReceiptFields("NFCE", "10.00", "2027-01-01", "X", "69.034.668/0001-56",
                "35200169034668000156651010000050961123456788", null, null, null, null, null, null));

        assertFalse(result.isValid());
        assertTrue(result.receipt().isPresent()); // built, but the error blocks confirmation
        assertEquals(LocalDate.of(2027, 1, 1), result.receipt().orElseThrow().issuedOn());
    }

    @Test
    void pixIsPaidByPixEvenWhenTheModelSaysNothing() {
        var result = mapper.map(pix(null));

        assertEquals(PaymentMethod.PIX, result.payment().orElseThrow().method());
        assertEquals(List.of("paymentMethod"), errorFields(mapper.map(pix("CREDIT"))));
    }

    @Test
    void cardIsPaidByCreditOrDebit() {
        assertEquals(List.of("paymentMethod"), errorFields(mapper.map(card(null, null, null))));
        assertEquals(List.of("paymentMethod"), errorFields(mapper.map(card("CASH", null, null))));
        assertEquals(PaymentMethod.DEBIT, mapper.map(card("debit", null, null)).payment().orElseThrow().method());
    }

    @Test
    void creditCanBeInInstallmentsAndDebitCannot() {
        // As printed on samples/card-stone-large.png: "5X SEM JUROS"
        var credit = mapper.map(card("CREDIT", "5x", "MASTERCARD"));
        assertEquals(new Payment(LocalTime.of(11, 23), PaymentMethod.CREDIT, 5, CardBrand.MASTERCARD),
                credit.payment().orElseThrow());

        var debit = mapper.map(card("DEBIT", "3", "VISA"));
        assertEquals(List.of("installments"), errorFields(debit));
        assertTrue(debit.payment().isEmpty());
    }

    @Test
    void installmentsOutOfRangeOrUnreadableAreErrors() {
        assertEquals(List.of("installments"), errorFields(mapper.map(card("CREDIT", "0", null))));
        assertEquals(List.of("installments"), errorFields(mapper.map(card("CREDIT", "25", null))));
        assertEquals(List.of("installments"), errorFields(mapper.map(card("CREDIT", "sem juros", null))));
        assertEquals(1, mapper.map(card("CREDIT", null, null)).payment().orElseThrow().installments());
    }

    @Test
    void unknownBrandIsOtherWithAWarningAndBrandsOnlyBelongToCards() {
        var result = mapper.map(card("CREDIT", "1", "DINERS"));
        assertTrue(result.isValid());
        assertEquals(CardBrand.OTHER, result.payment().orElseThrow().brand());
        assertEquals(List.of(ValidationIssue.warning("cardBrand", "Card brand not recognized, using OTHER: DINERS")),
                result.issues());

        var cash = mapper.map(new ReceiptFields("NFCE", "21.60", "2014-07-14", "N. Claudino e Cia Ltda",
                "08.995.631/0031-15", "25140708995631003115650060000000091000000094", "GOODS", "10:09", "CASH", null,
                "VISA", "SALE"));
        assertNull(cash.payment().orElseThrow().brand());
    }

    @Test
    void timeMustBeReadable() {
        assertEquals(LocalTime.of(9, 25, 11), mapper.map(cardAt("09:25:11")).payment().orElseThrow().time());
        assertEquals(List.of("time"), errorFields(mapper.map(cardAt("9h25"))));
        assertNull(mapper.map(cardAt(null)).payment().orElseThrow().time());
    }

    @Test
    void directionComesFromTheShopCnpj() {
        assertEquals(Direction.SALE, mapper.map(card("CREDIT", null, null)).direction());
        // An invoice from a supplier: someone else received the money
        var invoice = mapper.map(new ReceiptFields("NFE", "1500.00", "2022-10-05", "Distribuidora Exemplo",
                "11.222.333/0001-81", "35221011222333000181550010000123451123456787", "GOODS", null, null, null, null,
                null));
        assertEquals(Direction.EXPENSE, invoice.direction());
    }

    @Test
    void personChoosesTheDirectionWhenTheCnpjCannotTell() {
        // A CPF on a Pix says nothing: the owner may receive Pix in their own name
        var cpf = mapper.map(new ReceiptFields("PIX", "70.00", "2022-06-30", "Savio", "123.456.789-09",
                "E3788020620220630201900034125938", "OTHER", null, null, null, null, null));
        assertEquals(List.of("direction"), errorFields(cpf));
        assertNull(cpf.direction());

        var withoutShopCnpj = new ExtractionMapper(new ReceiptValidator(ReceiptValidatorTest.TODAY), Optional.empty());
        assertEquals(List.of("direction"), errorFields(withoutShopCnpj.map(card("CREDIT", null, null))));

        var chosen = mapper.map(new ReceiptFields("PIX", "70.00", "2022-06-30", "Savio", null,
                "E3788020620220630201900034125938", "OTHER", null, null, null, null, "expense"));
        assertEquals(Direction.EXPENSE, chosen.direction());
        assertEquals(List.of("direction"), errorFields(mapper.map(new ReceiptFields("PIX", "70.00", "2022-06-30",
                "Savio", null, "E3788020620220630201900034125938", "OTHER", null, null, null, null, "BOTH"))));
    }

    private static ReceiptFields pix(String method) {
        return new ReceiptFields("PIX", "80.00", "2022-08-12", "Pagsmile", "23.010.551/0001-31",
                "E00416968202208120422Mmzi9rPYPLT", "OTHER", "04:22", method, null, null, null);
    }

    private static ReceiptFields card(String method, String installments, String brand) {
        return new ReceiptFields("CARD", "25000.00", "2018-03-15", "Melhor Grão Stone", "69.034.668/0001-56", null,
                "GOODS", "11:23", method, installments, brand, null);
    }

    private static ReceiptFields cardAt(String time) {
        return new ReceiptFields("CARD", "500.00", "2018-03-15", "Melhor Grão Stone", "69.034.668/0001-56", "2UP8IQ",
                "GOODS", time, "CREDIT", null, null, null);
    }

    private static List<String> errorFields(ExtractionResult result) {
        return result.issues().stream().filter(ValidationIssue::isError).map(ValidationIssue::field).toList();
    }
}
