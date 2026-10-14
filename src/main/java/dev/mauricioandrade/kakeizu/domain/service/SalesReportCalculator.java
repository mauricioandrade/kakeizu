package dev.mauricioandrade.kakeizu.domain.service;

import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord.Confirmation;
import dev.mauricioandrade.kakeizu.domain.model.SalesReport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the sales report, counting each sale once (see {@link ReceiptPairing}).
 */
public class SalesReportCalculator {

    private static final Money ZERO = new Money(BigDecimal.ZERO);

    public SalesReport calculate(LocalDate from, LocalDate to, List<Confirmation> sales) {
        var counted = ReceiptPairing.countOnce(sales);
        return aggregate(from, to, counted.counted(), counted.pairedDocuments());
    }

    private static SalesReport aggregate(LocalDate from, LocalDate to, List<Confirmation> sales, int paired) {
        var byHour = new HashMap<Integer, Money>();
        var byWeekday = new HashMap<DayOfWeek, Money>();
        var byMethod = new HashMap<PaymentMethod, Money>();
        var byInstallments = new HashMap<Integer, Money>();
        var byBrand = new HashMap<CardBrand, Money>();
        var total = ZERO;
        int withoutTime = 0;
        for (Confirmation sale : sales) {
            var amount = sale.receipt().amount();
            var payment = sale.payment();
            total = total.plus(amount);
            add(byWeekday, sale.receipt().issuedOn().getDayOfWeek(), amount);
            if (payment.time() != null) {
                add(byHour, payment.time().getHour(), amount);
            } else {
                withoutTime++;
            }
            if (payment.method() != null) {
                add(byMethod, payment.method(), amount);
            }
            if (payment.method() == PaymentMethod.CREDIT) {
                add(byInstallments, payment.installments(), amount);
            }
            if (payment.brand() != null) {
                add(byBrand, payment.brand(), amount);
            }
        }
        return new SalesReport(from, to, sales.size(), total, paired, withoutTime, byHour, byWeekday, byMethod,
                byInstallments, byBrand);
    }

    private static <K> void add(Map<K, Money> totals, K key, Money amount) {
        totals.merge(key, amount, Money::plus);
    }
}
