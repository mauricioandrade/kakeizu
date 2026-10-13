package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

/**
 * NFC-e / cupom fiscal (model 65).
 */
public record FiscalCoupon(Money amount, LocalDate issuedOn, String counterparty,
                           Cnpj issuerCnpj) implements Receipt {
}
