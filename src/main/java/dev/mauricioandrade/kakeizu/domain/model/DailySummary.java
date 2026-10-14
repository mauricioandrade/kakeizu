package dev.mauricioandrade.kakeizu.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The day close, from the receipts confirmed with that issue date: what came in, what went out, the balance and
 * the cash that should be in the drawer. Each payment is counted once. Maps follow enum order and only contain
 * what appeared.
 *
 * @param cash            cash received and paid out, for the drawer
 * @param pairedDocuments NF-e/NFC-e not counted because their card slip, Pix or cheque was
 * @param unclassified    confirmed before sales and expenses were told apart: on neither side
 * @param toReview        photos uploaded that day still being read or waiting for review: not counted yet
 */
public record DailySummary(LocalDate day, Sales sales, Expenses expenses, Cash cash, int pairedDocuments,
                           int unclassified, int toReview) {

    /**
     * @param byMethod      a sale whose receipt shows no payment method counts as OTHER
     * @param inInstallments credit sales in more than one installment, also part of CREDIT
     */
    public record Sales(int count, Money total, Map<PaymentMethod, Money> byMethod, Money inInstallments) {

        public Sales {
            byMethod = Collections.unmodifiableMap(copy(PaymentMethod.class, byMethod));
        }
    }

    public record Expenses(int count, Money total, Map<Category, Money> byCategory) {

        public Expenses {
            byCategory = Collections.unmodifiableMap(copy(Category.class, byCategory));
        }
    }

    public record Cash(Money received, Money paidOut) {

        /**
         * Cash sales minus cash expenses; negative if more cash went out than came in.
         */
        public BigDecimal drawer() {
            return received.value().subtract(paidOut.value());
        }

        public boolean moved() {
            return received.value().signum() > 0 || paidOut.value().signum() > 0;
        }
    }

    /**
     * Sales minus expenses; negative on a day that spent more than it sold.
     */
    public BigDecimal balance() {
        return sales.total().value().subtract(expenses.total().value());
    }

    /**
     * Every receipt confirmed with this issue date, the paired NF-e/NFC-e included.
     */
    public int count() {
        return sales.count() + expenses.count() + pairedDocuments + unclassified;
    }

    private static <K extends Enum<K>> EnumMap<K, Money> copy(Class<K> type, Map<K, Money> source) {
        var copy = new EnumMap<K, Money>(type);
        copy.putAll(source);
        return copy;
    }
}
