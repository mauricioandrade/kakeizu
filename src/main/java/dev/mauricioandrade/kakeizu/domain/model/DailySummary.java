package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Totals of the receipts confirmed for a day. Maps follow enum order and only contain what appeared.
 */
public record DailySummary(LocalDate day, int count, Money total, Map<ReceiptType, Money> byType,
                           Map<Category, Money> byCategory) {

    public DailySummary {
        byType = Collections.unmodifiableMap(copy(ReceiptType.class, byType));
        byCategory = Collections.unmodifiableMap(copy(Category.class, byCategory));
    }

    private static <K extends Enum<K>> EnumMap<K, Money> copy(Class<K> type, Map<K, Money> source) {
        var copy = new EnumMap<K, Money>(type);
        copy.putAll(source);
        return copy;
    }
}
