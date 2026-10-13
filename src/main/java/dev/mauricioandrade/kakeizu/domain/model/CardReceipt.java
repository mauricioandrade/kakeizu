package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

/**
 * A card payment: the merchant copy of a card machine slip (Stone, Cielo, Rede...) or the receipt in the
 * payer's bank app. Both the CNPJ and the authorization code are optional (null): bank apps never show the
 * merchant CNPJ. When a CNPJ is present, it is a valid one.
 */
public record CardReceipt(Money amount, LocalDate issuedOn, String counterparty,
                          Cnpj merchantCnpj, String authorizationCode) implements Receipt {
}
