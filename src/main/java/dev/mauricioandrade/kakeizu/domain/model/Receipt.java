package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;
import java.util.Optional;

public sealed interface Receipt permits FiscalInvoice, FiscalCoupon, PixReceipt, CardReceipt, ChequeReceipt {

    Money amount();

    LocalDate issuedOn();

    String counterparty();

    default ReceiptType type() {
        return switch (this) {
            case FiscalInvoice _ -> ReceiptType.NFE;
            case FiscalCoupon _ -> ReceiptType.NFCE;
            case PixReceipt _ -> ReceiptType.PIX;
            case CardReceipt _ -> ReceiptType.CARD;
            case ChequeReceipt _ -> ReceiptType.CHEQUE;
        };
    }

    /**
     * The identifier that makes this receipt unique across all others. Card authorization codes repeat
     * across days and machines, so card slips have none, and neither do Pix slips from a card machine.
     */
    default Optional<String> deduplicationKey() {
        return switch (this) {
            case FiscalInvoice(_, _, _, _, var accessKey) -> Optional.of(accessKey.value());
            case FiscalCoupon(_, _, _, _, var accessKey) -> Optional.of(accessKey.value());
            case PixReceipt(_, _, _, var endToEndId) -> Optional.ofNullable(endToEndId).map(PixEndToEndId::value);
            case CardReceipt _ -> Optional.empty();
            case ChequeReceipt(_, _, _, var cmc7) -> Optional.ofNullable(cmc7).map(Cmc7::value);
        };
    }
}
