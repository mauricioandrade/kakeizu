package dev.mauricioandrade.kakeizu.domain.model;

import java.util.Optional;

/**
 * The shop using the app.
 *
 * @param cnpj           its own CNPJ, used to tell sales from expenses; empty if not configured
 * @param acceptsCheques whether it takes cheques at all; most shops no longer do
 */
public record Shop(Optional<Cnpj> cnpj, boolean acceptsCheques) {
}
