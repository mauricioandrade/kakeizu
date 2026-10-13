package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import jakarta.validation.constraints.Size;
import org.jboss.resteasy.reactive.RestForm;

/**
 * The review form as posted. Only sizes are checked here; the domain validates the content, exactly as it
 * does with the model output.
 */
public record ReviewForm(
        @RestForm @Size(max = 10) String type,
        @RestForm @Size(max = 30) String amount,
        @RestForm @Size(max = 10) String date,
        @RestForm @Size(max = 120) String counterparty,
        @RestForm @Size(max = 30) String taxId,
        @RestForm @Size(max = 60) String uniqueId,
        @RestForm @Size(max = 20) String category,
        @RestForm @Size(max = 8) String time,
        @RestForm @Size(max = 10) String paymentMethod,
        @RestForm @Size(max = 3) String installments,
        @RestForm @Size(max = 12) String cardBrand,
        @RestForm @Size(max = 10) String direction) {

    ReceiptFields toFields() {
        return new ReceiptFields(type, amount, date, counterparty, taxId, uniqueId, category, time, paymentMethod,
                installments, cardBrand, direction);
    }
}
