package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

public record PixReceipt(Money amount, LocalDate issuedOn, String counterparty,
                         PixEndToEndId endToEndId) implements Receipt {
}
