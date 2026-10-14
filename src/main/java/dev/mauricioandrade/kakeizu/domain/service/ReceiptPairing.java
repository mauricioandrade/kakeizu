package dev.mauricioandrade.kakeizu.domain.service;

import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ChequeReceipt;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord.Confirmation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Counts each payment once.
 *
 * <p>A card sale often leaves two receipts: the NFC-e and the card slip. An NF-e or NFC-e paid by card, Pix or
 * cheque is paired with a card slip, Pix receipt or cheque of the same amount, on the same day, at most 15
 * minutes apart (or on the same day if either shows no time, as on a cheque), and only the slip, Pix or cheque
 * is counted. Pairs are one to one, so two equal sales in a row stay two sales. A fiscal document with no pair
 * still counts: the owner may have photographed only the NFC-e.
 *
 * <p>Pair receipts of one side at a time (sales, or expenses): a sale never pays for an expense.
 */
public final class ReceiptPairing {

    static final Duration PAIRING_WINDOW = Duration.ofMinutes(15);

    /**
     * @param counted         what to count, in the order given, fiscal documents left without a pair at the end
     * @param pairedDocuments NF-e/NFC-e left out because their slip, Pix or cheque was counted instead
     */
    public record Counted<T>(List<T> counted, int pairedDocuments) {
    }

    private ReceiptPairing() {
    }

    public static Counted<Confirmation> countOnce(List<Confirmation> receipts) {
        return countOnce(receipts, Function.identity());
    }

    /**
     * @param confirmation the confirmed receipt and payment of each item
     */
    public static <T> Counted<T> countOnce(List<T> items, Function<T, Confirmation> confirmation) {
        var counted = new ArrayList<T>();
        var unpairedMoney = new ArrayList<T>();
        var documentsToPair = new ArrayList<T>();
        for (T item : items) {
            var confirmed = confirmation.apply(item);
            switch (confirmed.receipt()) {
                case CardReceipt _, PixReceipt _, ChequeReceipt _ -> {
                    counted.add(item);
                    unpairedMoney.add(item);
                }
                case FiscalInvoice _, FiscalCoupon _ -> {
                    if (hasItsOwnReceipt(confirmed)) {
                        documentsToPair.add(item);
                    } else {
                        counted.add(item);
                    }
                }
            }
        }
        int paired = 0;
        for (T document : documentsToPair) {
            var pair = closestPair(confirmation.apply(document), unpairedMoney, confirmation);
            if (pair.isPresent()) {
                unpairedMoney.remove(pair.get());
                paired++;
            } else {
                counted.add(document);
            }
        }
        return new Counted<>(counted, paired);
    }

    // Paid by card, Pix or cheque: the slip, the Pix or the cheque may have been photographed too
    private static boolean hasItsOwnReceipt(Confirmation document) {
        var method = document.payment().method();
        return method != null && (method.isCard() || method == PaymentMethod.PIX || method == PaymentMethod.CHEQUE);
    }

    private static <T> Optional<T> closestPair(Confirmation document, List<T> candidates,
                                               Function<T, Confirmation> confirmation) {
        return candidates.stream()
                .filter(money -> pays(document, confirmation.apply(money)))
                .min(Comparator.comparing(money -> gap(document, confirmation.apply(money))));
    }

    private static boolean pays(Confirmation document, Confirmation money) {
        var doc = document.receipt();
        var paid = money.receipt();
        boolean sameMethod = switch (paid) {
            case PixReceipt _ -> document.payment().method() == PaymentMethod.PIX;
            case CardReceipt _ -> document.payment().method() == money.payment().method();
            case ChequeReceipt _ -> document.payment().method() == PaymentMethod.CHEQUE;
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
}
