package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;
import java.util.Optional;

public sealed interface Receipt permits FiscalInvoice, FiscalCoupon, PixReceipt, CardReceipt {

    Money amount();

    LocalDate issuedOn();

    String counterparty();

    default ReceiptType type() {
        return switch (this) {
            case FiscalInvoice _ -> ReceiptType.NFE;
            case FiscalCoupon _ -> ReceiptType.NFCE;
            case PixReceipt _ -> ReceiptType.PIX;
            case CardReceipt _ -> ReceiptType.CARD;
        };
    }

    /**
     * The identifier that makes this receipt unique across all others. Card authorization codes repeat
     * across days and machines, so card slips have none.
     */
    default Optional<String> deduplicationKey() {
        return switch (this) {
            case FiscalInvoice(_, _, _, _, var accessKey) -> Optional.of(accessKey.value());
            case FiscalCoupon(_, _, _, _, var accessKey) -> Optional.of(accessKey.value());
            case PixReceipt(_, _, _, var endToEndId) -> Optional.of(endToEndId.value());
            case CardReceipt _ -> Optional.empty();
        };
    }
}
