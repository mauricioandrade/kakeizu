package dev.mauricioandrade.kakeizu.domain.service;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import dev.mauricioandrade.kakeizu.domain.model.DailySummary.Cash;
import dev.mauricioandrade.kakeizu.domain.model.DailySummary.Expenses;
import dev.mauricioandrade.kakeizu.domain.model.DailySummary.Sales;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Closes the day: sales by payment method, expenses by category, each side paired on its own (an NF-e from a
 * supplier and the Pix that paid it are one expense, as an NFC-e and its card slip are one sale).
 */
public class DailySummaryCalculator {

    private static final Money ZERO = new Money(BigDecimal.ZERO);

    /**
     * @param confirmed receipts confirmed with this issue date
     * @param toReview  photos of the day not confirmed or rejected yet
     */
    public DailySummary calculate(LocalDate day, List<ReceiptRecord> confirmed, int toReview) {
        var sales = new ArrayList<ReceiptRecord>();
        var expenses = new ArrayList<ReceiptRecord>();
        int unclassified = 0;
        for (ReceiptRecord record : confirmed) {
            var direction = record.confirmation().orElseThrow().direction();
            if (direction == null) {
                unclassified++;
                continue;
            }
            switch (direction) {
                case SALE -> sales.add(record);
                case EXPENSE -> expenses.add(record);
            }
        }
        var countedSales = ReceiptPairing.countOnce(sales, r -> r.confirmation().orElseThrow());
        var countedExpenses = ReceiptPairing.countOnce(expenses, r -> r.confirmation().orElseThrow());
        return new DailySummary(day, sales(countedSales.counted()), expenses(countedExpenses.counted()),
                new Cash(cash(countedSales.counted()), cash(countedExpenses.counted())),
                countedSales.pairedDocuments() + countedExpenses.pairedDocuments(), unclassified, toReview);
    }

    private static Sales sales(List<ReceiptRecord> sales) {
        var byMethod = new EnumMap<PaymentMethod, Money>(PaymentMethod.class);
        var inInstallments = ZERO;
        for (ReceiptRecord sale : sales) {
            var payment = sale.confirmation().orElseThrow().payment();
            var amount = amount(sale);
            add(byMethod, Objects.requireNonNullElse(payment.method(), PaymentMethod.OTHER), amount);
            if (payment.method() == PaymentMethod.CREDIT && payment.installments() > 1) {
                inInstallments = inInstallments.plus(amount);
            }
        }
        return new Sales(sales.size(), total(sales), byMethod, inInstallments);
    }

    private static Expenses expenses(List<ReceiptRecord> expenses) {
        var byCategory = new EnumMap<Category, Money>(Category.class);
        expenses.forEach(expense -> add(byCategory, expense.category(), amount(expense)));
        return new Expenses(expenses.size(), total(expenses), byCategory);
    }

    private static Money cash(List<ReceiptRecord> receipts) {
        return total(receipts.stream()
                .filter(r -> r.confirmation().orElseThrow().payment().method() == PaymentMethod.CASH)
                .toList());
    }

    private static Money total(List<ReceiptRecord> receipts) {
        return receipts.stream().map(DailySummaryCalculator::amount).reduce(ZERO, Money::plus);
    }

    private static Money amount(ReceiptRecord record) {
        return record.confirmedReceipt().orElseThrow().amount();
    }

    private static <K> void add(Map<K, Money> totals, K key, Money amount) {
        totals.merge(key, amount, Money::plus);
    }
}
