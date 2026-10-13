package dev.mauricioandrade.kakeizu.domain.model;

import java.time.LocalDate;

public sealed interface Receipt permits FiscalInvoice, FiscalCoupon, PixReceipt, CardReceipt {

    Money amount();

    LocalDate issuedOn();

    String counterparty();
}
