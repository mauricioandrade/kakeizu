package dev.mauricioandrade.kakeizu.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Sales in a date range, each sale counted once. Maps are sorted by key and only contain what appeared.
 *
 * @param pairedDocuments NF-e/NFC-e paid by card or Pix that matched a card slip or Pix receipt and were not
 *                        counted again
 * @param withoutTime     sales left out of {@code byHour} because the receipt shows no time
 * @param byInstallments  credit sales by number of installments (1 = paid upfront)
 */
public record SalesReport(LocalDate from, LocalDate to, int count, Money total, int pairedDocuments, int withoutTime,
                          Map<Integer, Money> byHour, Map<DayOfWeek, Money> byWeekday,
                          Map<PaymentMethod, Money> byMethod, Map<Integer, Money> byInstallments,
                          Map<CardBrand, Money> byBrand) {

    public SalesReport {
        byHour = sorted(byHour);
        byWeekday = sorted(byWeekday);
        byMethod = sorted(byMethod);
        byInstallments = sorted(byInstallments);
        byBrand = sorted(byBrand);
    }

    private static <K extends Comparable<K>> SortedMap<K, Money> sorted(Map<K, Money> source) {
        return Collections.unmodifiableSortedMap(new TreeMap<>(source));
    }
}
