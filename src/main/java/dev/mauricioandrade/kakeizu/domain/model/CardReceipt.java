package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

/**
 * Merchant copy of a card machine slip (Stone, Cielo, Rede...). The authorization code is optional.
 */
public record CardReceipt(Money amount, LocalDate issuedOn, String counterparty,
                          Cnpj merchantCnpj, String authorizationCode) implements Receipt {
}
