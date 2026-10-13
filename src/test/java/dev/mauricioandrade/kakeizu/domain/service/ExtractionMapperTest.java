package dev.mauricioandrade.kakeizu.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionMapperTest {

    final ExtractionMapper mapper = new ExtractionMapper(new ReceiptValidator(ReceiptValidatorTest.TODAY));

    @Test
    void gemmaOutputForThePixSampleIsCaughtOnTheTransactionId() {
        // Real gemma3:4b output for samples/pix-inter.png
        var result = mapper.map(new ReceiptFields("PIX", "80.00", "2022-08-12", "Pagsmile",
                "23.010.551/0001-31", "E00416968202208120422Mzmiz9rPYPLT", "OTHER"));

        assertFalse(result.isValid());
        assertTrue(result.receipt().isEmpty());
        assertEquals(List.of(ValidationIssue.error("uniqueId", "Pix transaction ID must have 32 characters, got 33")),
                result.issues());
    }

    @Test
    void correctedPixBecomesAReceipt() {
        var result = mapper.map(new ReceiptFields("PIX", "80,00", "2022-08-12", "Pagsmile",
                null, "E00416968202208120422Mmzi9rPYPLT", "OTHER"));

        assertTrue(result.isValid());
        var pix = assertInstanceOf(PixReceipt.class, result.receipt().orElseThrow());
        assertEquals(new Money("80.00"), pix.amount());
        assertEquals("E00416968202208120422Mmzi9rPYPLT", pix.endToEndId().value());
    }

    @Test
    void validInvoice() {
        var result = mapper.map(new ReceiptFields("nfe", "1.500,00", "2022-10-05", "Distribuidora Exemplo",
                "11.222.333/0001-81", "35221011222333000181550010000123451123456787", "FUEL_SUPPLY"));

        assertEquals(List.of(), result.issues());
        assertInstanceOf(FiscalInvoice.class, result.receipt().orElseThrow());
        assertEquals(Category.FUEL_SUPPLY, result.category());
    }

    @Test
    void invoiceWithWarningsIsStillValid() {
        var result = mapper.map(new ReceiptFields("NFE", "1500.00", "2022-11-05", "Distribuidora Exemplo",
                "11.222.333/0001-81", "35221011222333000181550010000123451123456787", "GOODS"));

        assertTrue(result.isValid());
        assertTrue(result.receipt().isPresent());
        assertEquals(ValidationIssue.Severity.WARNING, result.issues().getFirst().severity());
    }

    @Test
    void couponNeedsOnlyTheCnpj() {
        var result = mapper.map(new ReceiptFields("NFCE", "42,90", "2026-10-12", "Mercadinho Exemplo",
                "69.034.668/0001-56", null, "GOODS"));

        assertInstanceOf(FiscalCoupon.class, result.receipt().orElseThrow());
    }

    @Test
    void cardWithoutAuthorizationCode() {
        var result = mapper.map(new ReceiptFields("CARD", "500.00", "2018-03-15", "Melhor Grão Stone",
                "69.034.668/0001-56", "  ", "GOODS"));

        var card = assertInstanceOf(CardReceipt.class, result.receipt().orElseThrow());
        assertNull(card.authorizationCode());
    }

    @Test
    void gemmaOutputForTheLargeStoneSlipIsCaughtOnTheCnpj() {
        // Real gemma3:4b output for samples/card-stone-large.png: a placeholder CNPJ printed on a demo slip
        var result = mapper.map(new ReceiptFields("CARD", "25000.00", "2018-03-15", "DAIANE",
                "11111.111/0001-11", "A6D25ED683JAS", "OTHER"));

        assertEquals(List.of("taxId"), errorFields(result));
    }

    @Test
    void allFieldsMissingReportsEachOne() {
        var result = mapper.map(new ReceiptFields(null, null, null, null, null, null, null));

        assertTrue(result.receipt().isEmpty());
        assertEquals(List.of("type", "amount", "date", "counterparty"), errorFields(result));
        assertEquals(Category.OTHER, result.category());
    }

    @Test
    void missingTypeSpecificFields() {
        var result = mapper.map(new ReceiptFields("NFE", "10.00", "2022-10-05", "X", null, null, null));

        assertEquals(List.of("taxId", "uniqueId"), errorFields(result));
    }

    @Test
    void unknownTypeAndNonIsoDateAreErrors() {
        // What gemma3:4b returned before the prompt explained the fields
        var result = mapper.map(new ReceiptFields("UNKNOWN", "80.00", "Sexta, 12/08/2022", "Pagsmile", null, null, null));

        assertEquals(List.of(
                ValidationIssue.error("type", "Receipt type not recognized: UNKNOWN"),
                ValidationIssue.error("date", "Date must be yyyy-MM-dd, got: Sexta, 12/08/2022")), result.issues());
    }

    @Test
    void unknownCategoryFallsBackToOtherWithAWarning() {
        var result = mapper.map(new ReceiptFields("CARD", "500.00", "2018-03-15", "Melhor Grão Stone",
                "69.034.668/0001-56", "2UP8IQ", "CRÉDITO"));

        assertTrue(result.isValid());
        assertEquals(Category.OTHER, result.category());
        assertEquals(List.of(ValidationIssue.warning("category", "Category not recognized, using OTHER: CRÉDITO")),
                result.issues());
    }

    @Test
    void futureDateBlocksTheReceipt() {
        var result = mapper.map(new ReceiptFields("NFCE", "10.00", "2027-01-01", "X", "69.034.668/0001-56", null, null));

        assertFalse(result.isValid());
        assertTrue(result.receipt().isPresent()); // built, but the error blocks confirmation
        assertEquals(LocalDate.of(2027, 1, 1), result.receipt().orElseThrow().issuedOn());
    }

    private static List<String> errorFields(ExtractionResult result) {
        return result.issues().stream().filter(ValidationIssue::isError).map(ValidationIssue::field).toList();
    }
}
