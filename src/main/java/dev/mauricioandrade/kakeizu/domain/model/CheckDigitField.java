package dev.mauricioandrade.kakeizu.domain.model;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.util.List;
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
     * The guarded fields that were read but fail their check digits (or their shape). Missing ones are left alone:
     * "not found" is a different problem, and a CNPJ is optional on a card slip.
     */
    public static List<CheckDigitField> failingIn(ReceiptFields fields) {
        return guardedIn(fields.type()).stream()
                .filter(field -> {
                    String value = field.valueIn(fields);
                    return value != null && !value.isBlank() && !field.isValid(value);
                })
                .toList();
    }

    private static List<CheckDigitField> guardedIn(String type) {
        ReceiptType parsed;
        try {
            parsed = type == null ? null : ReceiptType.valueOf(type.strip().toUpperCase());
        } catch (IllegalArgumentException _) {
            parsed = null;
        }
        return parsed == null ? List.of() : switch (parsed) {
            case NFE, NFCE -> List.of(CNPJ, NFE_ACCESS_KEY);
            case CARD -> List.of(CNPJ);
            case PIX, CHEQUE -> List.of();
        };
    }

    public String valueIn(ReceiptFields fields) {
        return switch (this) {
            case CNPJ -> fields.taxId();
            case NFE_ACCESS_KEY -> fields.uniqueId();
        };
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
