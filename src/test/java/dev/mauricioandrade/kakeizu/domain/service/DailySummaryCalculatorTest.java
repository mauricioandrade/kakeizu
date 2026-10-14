package dev.mauricioandrade.kakeizu.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.Payment;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord.Confirmation;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailySummaryCalculatorTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 13);
    static final Cnpj SHOP = new Cnpj("69.034.668/0001-56");
    static final Cnpj SUPPLIER = new Cnpj("23.010.551/0001-31");

    final DailySummaryCalculator calculator = new DailySummaryCalculator();

    @Test
    void salesComeInByPaymentMethodAndExpensesGoOutByCategory() {
        var close = calculate(
                sale(card("300.00", PaymentMethod.CREDIT), "14:05", PaymentMethod.CREDIT, 3),
                sale(card("100.00", PaymentMethod.CREDIT), "14:50", PaymentMethod.CREDIT, 1),
                sale(card("40.00", PaymentMethod.DEBIT), "09:30", PaymentMethod.DEBIT, 1),
                sale(pix("60.00"), "11:00", PaymentMethod.PIX, 1),
                expense(invoice("1200.00", SUPPLIER), Category.FUEL_SUPPLY, PaymentMethod.OTHER),
                expense(pix("89.90"), Category.UTILITIES, PaymentMethod.PIX));

        assertEquals(4, close.sales().count());
        assertEquals(new Money("500.00"), close.sales().total());
        assertEquals(Map.of(PaymentMethod.CREDIT, new Money("400.00"), PaymentMethod.DEBIT, new Money("40.00"),
                PaymentMethod.PIX, new Money("60.00")), close.sales().byMethod());
        assertEquals(new Money("300.00"), close.sales().inInstallments());
        assertEquals(2, close.expenses().count());
        assertEquals(new Money("1289.90"), close.expenses().total());
        assertEquals(Map.of(Category.FUEL_SUPPLY, new Money("1200.00"), Category.UTILITIES, new Money("89.90")),
                close.expenses().byCategory());
        assertEquals(new BigDecimal("-789.90"), close.balance()); // a day that spent more than it sold
        assertEquals(6, close.count());
    }

    @Test
    void theDrawerIsCashInMinusCashOut() {
        var close = calculate(
                sale(card("50.00", PaymentMethod.DEBIT), "10:00", PaymentMethod.DEBIT, 1),
                sale(invoice("25.00", SHOP), "10:30", PaymentMethod.CASH, 1),
                sale(invoice("12.50", SHOP), "11:00", PaymentMethod.CASH, 1),
                expense(invoice("8.00", SUPPLIER), Category.GOODS, PaymentMethod.CASH));

        assertEquals(new Money("37.50"), close.cash().received());
        assertEquals(new Money("8.00"), close.cash().paidOut());
        assertEquals(new BigDecimal("29.50"), close.cash().drawer());
        assertTrue(close.cash().moved());
    }

    @Test
    void anNfceAndItsCardSlipAreOneSale() {
        var close = calculate(
                sale(invoice("89.90", SHOP), "14:00", PaymentMethod.CREDIT, 1),
                sale(card("89.90", PaymentMethod.CREDIT), "14:10", PaymentMethod.CREDIT, 1));

        assertEquals(1, close.sales().count());
        assertEquals(new Money("89.90"), close.sales().total());
        assertEquals(1, close.pairedDocuments());
        assertEquals(2, close.count()); // both were confirmed
    }

    @Test
    void aSupplierInvoiceAndThePixThatPaidItAreOneExpense() {
        var close = calculate(
                expense(invoice("1200.00", SUPPLIER), Category.FUEL_SUPPLY, PaymentMethod.PIX),
                expense(pix("1200.00"), Category.FUEL_SUPPLY, PaymentMethod.PIX));

        assertEquals(1, close.expenses().count());
        assertEquals(new Money("1200.00"), close.expenses().total());
        assertEquals(1, close.pairedDocuments());
    }

    @Test
    void aSaleNeverPairsWithAnExpense() {
        var close = calculate(
                sale(invoice("70.00", SHOP), "17:20", PaymentMethod.PIX, 1),
                expense(pix("70.00"), Category.OTHER, PaymentMethod.PIX));

        assertEquals(1, close.sales().count());
        assertEquals(1, close.expenses().count());
        assertEquals(0, close.pairedDocuments());
    }

    @Test
    void aSaleWithNoPaymentMethodCountsAsOther() {
        var close = calculate(sale(invoice("10.00", SHOP), null, null, 1));

        assertEquals(Map.of(PaymentMethod.OTHER, new Money("10.00")), close.sales().byMethod());
    }

    @Test
    void receiptsConfirmedBeforeSalesAndExpensesWereToldApartAreOnNeitherSide() {
        var close = calculate(confirmed(card("30.00", PaymentMethod.CREDIT), Category.GOODS, Payment.unknown(), null));

        assertEquals(1, close.unclassified());
        assertEquals(0, close.sales().count());
        assertEquals(0, close.expenses().count());
        assertEquals(new BigDecimal("0.00"), close.balance());
    }

    @Test
    void anEmptyDayIsZeroWithWhatIsLeftToReview() {
        var close = calculator.calculate(DAY, List.of(), 3);

        assertEquals(0, close.count());
        assertEquals(new BigDecimal("0.00"), close.balance());
        assertFalse(close.cash().moved());
        assertEquals(3, close.toReview());
        assertEquals(Map.of(), close.sales().byMethod());
    }

    private DailySummary calculate(ReceiptRecord... confirmed) {
        return calculator.calculate(DAY, List.of(confirmed), 0);
    }

    private static ReceiptRecord sale(Receipt receipt, String time, PaymentMethod method, int installments) {
        return confirmed(receipt, Category.GOODS,
                new Payment(time == null ? null : LocalTime.parse(time), method, installments, null), Direction.SALE);
    }

    private static ReceiptRecord expense(Receipt receipt, Category category, PaymentMethod method) {
        return confirmed(receipt, category, new Payment(null, method, 1, null), Direction.EXPENSE);
    }

    private static ReceiptRecord confirmed(Receipt receipt, Category category, Payment payment, Direction direction) {
        var fields = new ReceiptFields(null, null, null, null, null, null, null, null, null, null, null, null);
        return new ReceiptRecord(UUID.randomUUID(), ReceiptStatus.CONFIRMED, fields, category,
                Optional.of(new Confirmation(receipt, payment, direction)), "a".repeat(64), "image/png", "{}",
                List.of(), Instant.now());
    }

    private static Receipt card(String amount, PaymentMethod method) {
        return new CardReceipt(new Money(amount), DAY, "Melhor Grão Stone", SHOP, method.name());
    }

    private static Receipt invoice(String amount, Cnpj issuer) {
        return new FiscalInvoice(new Money(amount), DAY, "Fornecedor", issuer,
                new NfeAccessKey("35261023010551000131650010000123451123456787"));
    }

    private static Receipt pix(String amount) {
        return new PixReceipt(new Money(amount), DAY, "Fornecedor", null);
    }
}
