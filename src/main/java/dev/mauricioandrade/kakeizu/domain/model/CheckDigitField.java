package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.util.function.Function;

/**
 * Receipt fields guarded by check digits: when the model misreads one, the check digits notice, and the
 * field is worth reading again. The Pix ID has no check digit, so a misread there cannot be told apart.
 */
public enum CheckDigitField {
    CNPJ("taxId", 14, Cnpj::new),
    NFE_ACCESS_KEY("uniqueId", 44, NfeAccessKey::new);

    private final String formField;
    private final int length;
    private final Function<String, ?> valueObject;

    CheckDigitField(String formField, int length, Function<String, ?> valueObject) {
        this.formField = formField;
        this.length = length;
        this.valueObject = valueObject;
    }

    /**
     * The {@link ReceiptFields} component (and review form field) that holds it.
     */
    public String formField() {
        return formField;
    }

    public int length() {
        return length;
    }

    public boolean isValid(String raw) {
        try {
            valueObject.apply(raw);
            return true;
        } catch (InvalidValueException _) {
            return false;
        }
    }
}
