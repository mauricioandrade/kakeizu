package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

/**
 * A Pix transfer: the receipt in a bank app, or the slip of a Pix paid on a card machine. Machines print their
 * own transaction ID instead of the end-to-end ID, so the end-to-end ID is optional (null); when present, it is
 * a valid one.
 */
public record PixReceipt(Money amount, LocalDate issuedOn, String counterparty,
                         PixEndToEndId endToEndId) implements Receipt {
}
