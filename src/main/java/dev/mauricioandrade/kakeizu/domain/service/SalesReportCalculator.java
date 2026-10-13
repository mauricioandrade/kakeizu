package dev.mauricioandrade.kakeizu.domain.service;

import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord.Confirmation;
import dev.mauricioandrade.kakeizu.domain.model.SalesReport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the sales report, counting each sale once.
 *
 * <p>A card sale often leaves two receipts: the NFC-e and the card slip. An NF-e or NFC-e paid by card or Pix
 * is paired with a card slip or Pix receipt of the same amount, on the same day, at most 15 minutes apart
 * (or on the same day if either shows no time), and only the slip or Pix receipt is counted. Pairs are one to
 * one, so two equal sales in a row stay two sales. A fiscal document with no pair still counts: the owner may
 * have photographed only the NFC-e.
 */
public class SalesReportCalculator {

    static final Duration PAIRING_WINDOW = Duration.ofMinutes(15);
    private static final Money ZERO = new Money(BigDecimal.ZERO);

    public SalesReport calculate(LocalDate from, LocalDate to, List<Confirmation> sales) {
        var counted = new ArrayList<Confirmation>();
        var unpairedMoney = new ArrayList<Confirmation>();
        var documentsToPair = new ArrayList<Confirmation>();
        for (Confirmation sale : sales) {
            switch (sale.receipt()) {
                case CardReceipt _, PixReceipt _ -> {
                    counted.add(sale);
                    unpairedMoney.add(sale);
                }
                case FiscalInvoice _, FiscalCoupon _ -> {
                    if (isPaidElectronically(sale)) {
                        documentsToPair.add(sale);
                    } else {
                        counted.add(sale);
                    }
                }
            }
        }
        int paired = 0;
        for (Confirmation document : documentsToPair) {
            var pair = closestPair(document, unpairedMoney);
            if (pair.isPresent()) {
                unpairedMoney.remove(pair.get());
                paired++;
            } else {
                counted.add(document);
            }
        }
        return aggregate(from, to, counted, paired);
    }

    private static boolean isPaidElectronically(Confirmation document) {
        var method = document.payment().method();
        return method != null && (method.isCard() || method == PaymentMethod.PIX);
    }

    private static Optional<Confirmation> closestPair(Confirmation document, List<Confirmation> candidates) {
        return candidates.stream()
                .filter(money -> pays(document, money))
                .min(Comparator.comparing(money -> gap(document, money)));
    }

    private static boolean pays(Confirmation document, Confirmation money) {
        var doc = document.receipt();
        var paid = money.receipt();
        boolean sameMethod = switch (paid) {
            case PixReceipt _ -> document.payment().method() == PaymentMethod.PIX;
            case CardReceipt _ -> document.payment().method() == money.payment().method();
            case FiscalInvoice _, FiscalCoupon _ -> false;
        };
        return sameMethod
                && doc.amount().equals(paid.amount())
                && doc.issuedOn().equals(paid.issuedOn())
                && gap(document, money).compareTo(PAIRING_WINDOW) <= 0;
    }

    // Unknown time on either side: only the day is compared
    private static Duration gap(Confirmation a, Confirmation b) {
        var timeA = a.payment().time();
        var timeB = b.payment().time();
        return timeA == null || timeB == null ? Duration.ZERO : Duration.between(timeA, timeB).abs();
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
