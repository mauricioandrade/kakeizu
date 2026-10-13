package dev.mauricioandrade.kakeizu.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.Payment;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord.Confirmation;
import dev.mauricioandrade.kakeizu.domain.model.SalesReport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesReportCalculatorTest {

    // A Tuesday
    static final LocalDate DAY = LocalDate.of(2026, 10, 13);
    static final Cnpj SHOP = new Cnpj("69.034.668/0001-56");

    final SalesReportCalculator calculator = new SalesReportCalculator();

    @Test
    void nfceAndTheCardSlipOfTheSameSaleCountOnce() {
        var report = calculate(
                coupon("89.90", "14:00", PaymentMethod.CREDIT),
                card("89.90", "14:10", PaymentMethod.CREDIT, 1, CardBrand.VISA));

        assertEquals(1, report.count());
        assertEquals(new Money("89.90"), report.total());
        assertEquals(1, report.pairedDocuments());
    }

    @Test
    void nfcePaidInCashIsAlwaysItsOwnSale() {
        var report = calculate(
                coupon("21.60", "10:09", PaymentMethod.CASH),
                card("21.60", "10:10", PaymentMethod.DEBIT, 1, CardBrand.ELO));

        assertEquals(2, report.count());
        assertEquals(0, report.pairedDocuments());
    }

    @Test
    void moreThanFifteenMinutesApartAreTwoSales() {
        var report = calculate(
                coupon("50.00", "14:00", PaymentMethod.CREDIT),
                card("50.00", "14:16", PaymentMethod.CREDIT, 1, CardBrand.VISA));

        assertEquals(2, report.count());
    }

    @Test
    void exactlyFifteenMinutesApartStillPair() {
        var report = calculate(
                coupon("50.00", "14:00", PaymentMethod.CREDIT),
                card("50.00", "14:15", PaymentMethod.CREDIT, 1, CardBrand.VISA));

        assertEquals(1, report.count());
    }

    @Test
    void differentAmountOrMethodNeverPair() {
        var report = calculate(
                coupon("50.00", "14:00", PaymentMethod.DEBIT),
                card("50.00", "14:01", PaymentMethod.CREDIT, 1, CardBrand.VISA),
                coupon("30.00", "15:00", PaymentMethod.CREDIT),
                card("30.01", "15:01", PaymentMethod.CREDIT, 1, CardBrand.VISA));

        assertEquals(4, report.count());
        assertEquals(0, report.pairedDocuments());
    }

    @Test
    void pairsAreOneToOneSoTwoEqualSalesStayTwo() {
        // Two coffees at R$ 5,00 a minute apart, but only one NFC-e photographed
        var report = calculate(
                coupon("5.00", "08:00", PaymentMethod.DEBIT),
                card("5.00", "08:00", PaymentMethod.DEBIT, 1, CardBrand.ELO),
                card("5.00", "08:01", PaymentMethod.DEBIT, 1, CardBrand.ELO));

        assertEquals(2, report.count());
        assertEquals(new Money("10.00"), report.total());
        assertEquals(1, report.pairedDocuments());
    }

    @Test
    void eachDocumentTakesTheClosestSlip() {
        var report = calculate(
                card("20.00", "09:00", PaymentMethod.CREDIT, 1, CardBrand.VISA),
                card("20.00", "09:12", PaymentMethod.CREDIT, 1, CardBrand.VISA),
                coupon("20.00", "09:13", PaymentMethod.CREDIT),
                coupon("20.00", "09:01", PaymentMethod.CREDIT));

        assertEquals(2, report.count());
        assertEquals(2, report.pairedDocuments());
    }

    @Test
    void nfcePaidByPixPairsWithThePixReceipt() {
        var report = calculate(coupon("70.00", "17:20", PaymentMethod.PIX), pix("70.00", "17:21"));

        assertEquals(1, report.count());
        assertEquals(Map.of(PaymentMethod.PIX, new Money("70.00")), report.byMethod());
    }

    @Test
    void withoutTimeOnEitherSideTheDayIsEnough() {
        var report = calculate(
                coupon("12.00", null, PaymentMethod.CREDIT),
                card("12.00", "11:00", PaymentMethod.CREDIT, 1, CardBrand.VISA));

        assertEquals(1, report.count());
    }

    @Test
    void nfcePaidByCardWithoutItsSlipStillCounts() {
        // The owner photographed only the NFC-e
        var report = calculate(coupon("99.00", "13:00", PaymentMethod.CREDIT));

        assertEquals(1, report.count());
        assertEquals(new Money("99.00"), report.total());
    }

    @Test
    void breaksDownByHourWeekdayMethodInstallmentsAndBrand() {
        var report = calculate(
                card("300.00", "14:05", PaymentMethod.CREDIT, 3, CardBrand.MASTERCARD),
                card("100.00", "14:50", PaymentMethod.CREDIT, 1, CardBrand.VISA),
                card("40.00", "09:30", PaymentMethod.DEBIT, 1, CardBrand.VISA),
                pix("60.00", null),
                coupon("25.00", "18:00", PaymentMethod.CASH));

        assertEquals(5, report.count());
        assertEquals(new Money("525.00"), report.total());
        assertEquals(Map.of(9, new Money("40.00"), 14, new Money("400.00"), 18, new Money("25.00")), report.byHour());
        assertEquals(1, report.withoutTime());
        assertEquals(Map.of(DayOfWeek.TUESDAY, new Money("525.00")), report.byWeekday());
        assertEquals(Map.of(PaymentMethod.CASH, new Money("25.00"), PaymentMethod.CREDIT, new Money("400.00"),
                PaymentMethod.DEBIT, new Money("40.00"), PaymentMethod.PIX, new Money("60.00")), report.byMethod());
        // Only credit can be in installments
        assertEquals(Map.of(1, new Money("100.00"), 3, new Money("300.00")), report.byInstallments());
        assertEquals(Map.of(CardBrand.VISA, new Money("140.00"), CardBrand.MASTERCARD, new Money("300.00")),
                report.byBrand());
        assertEquals(List.of(9, 14, 18), List.copyOf(report.byHour().keySet()));
    }

    @Test
    void noSalesIsAnEmptyReport() {
        var report = calculate();

        assertEquals(0, report.count());
        assertEquals(new Money("0"), report.total());
        assertEquals(Map.of(), report.byMethod());
    }

    private SalesReport calculate(Confirmation... sales) {
        return calculator.calculate(DAY.minusDays(29), DAY, List.of(sales));
    }

    private static Confirmation card(String amount, String time, PaymentMethod method, int installments,
                                     CardBrand brand) {
        return new Confirmation(new CardReceipt(new Money(amount), DAY, "Melhor Grão Stone", SHOP, "2UP8IQ"),
                new Payment(time(time), method, installments, brand), Direction.SALE);
    }

    private static Confirmation coupon(String amount, String time, PaymentMethod method) {
        return new Confirmation(new FiscalCoupon(new Money(amount), DAY, "Melhor Grão Stone", SHOP,
                new NfeAccessKey("35200169034668000156651010000050961123456788")),
                new Payment(time(time), method, 1, null), Direction.SALE);
    }

    private static Confirmation pix(String amount, String time) {
        return new Confirmation(new PixReceipt(new Money(amount), DAY, "Melhor Grão Stone",
                new PixEndToEndId("E00416968202208120422Mmzi9rPYPLT")),
                new Payment(time(time), PaymentMethod.PIX, 1, null), Direction.SALE);
    }

    private static LocalTime time(String value) {
        return value == null ? null : LocalTime.parse(value);
    }
}
