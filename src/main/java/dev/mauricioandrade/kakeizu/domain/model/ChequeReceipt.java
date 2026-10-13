package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

/**
 * A cheque the shop received. Unlike every other receipt, the counterparty is who pays: the account holder
 * printed on the cheque. The CMC-7 line is optional (null) because it is hard to read on a photo; when
 * present, it is a valid one and makes the cheque unique.
 */
public record ChequeReceipt(Money amount, LocalDate issuedOn, String counterparty, Cmc7 cmc7) implements Receipt {
}
