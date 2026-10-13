package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

/**
 * NF-e (model 55).
 */
public record FiscalInvoice(Money amount, LocalDate issuedOn, String counterparty,
                            Cnpj issuerCnpj, NfeAccessKey accessKey) implements Receipt {
}
