package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.time.LocalTime;

/**
 * How and when the money moved. Time, method and brand are null when the receipt does not show them;
 * the brand is only kept for card payments.
 */
public record Payment(LocalTime time, PaymentMethod method, int installments, CardBrand brand) {

    public static final int MAX_INSTALLMENTS = 24;

    public Payment {
        if (installments < 1 || installments > MAX_INSTALLMENTS) {
            throw new InvalidValueException("Installments must be between 1 and " + MAX_INSTALLMENTS);
        }
        if (installments > 1 && method != PaymentMethod.CREDIT) {
            throw new InvalidValueException("Only credit can be paid in installments");
        }
        if (method == null || !method.isCard()) {
            brand = null;
        }
    }

    public static Payment unknown() {
        return new Payment(null, null, 1, null);
    }

    public boolean inInstallments() {
        return installments > 1;
    }
}
