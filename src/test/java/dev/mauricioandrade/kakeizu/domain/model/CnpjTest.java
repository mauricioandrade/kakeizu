package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CnpjTest {

    @ParameterizedTest
    @CsvSource({
            "23.010.551/0001-31, 23010551000131",
            "69.034.668/0001-56, 69034668000156",
            "11222333000181,     11222333000181",
            "'11 222 333 0001 81', 11222333000181"
    })
    void acceptsValidWithOrWithoutPunctuation(String raw, String digits) {
        assertEquals(digits, new Cnpj(raw).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11.111.111/0001-11", // placeholder on a Stone demo slip
            "11111.111/0001-11",  // same, as gemma3:4b read it: misplaced dot is fine, check digits are not
            "23.010.551/0001-32", // last digit misread
            "23.010.551/0001-21", // first check digit misread
            "23.016.551/0001-31"  // base digit misread
    })
    void rejectsWrongCheckDigits(String raw) {
        var e = assertThrows(InvalidValueException.class, () -> new Cnpj(raw));
        assertTrue(e.getMessage().startsWith("CNPJ check digits do not match"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "68889271-20",         // the model put the machine serial number here
            "23.010.551/0001-311",
            "23.010.551/OOO1-31"   // letter O instead of zero
    })
    void rejectsWrongShape(String raw) {
        var e = assertThrows(InvalidValueException.class, () -> new Cnpj(raw));
        assertTrue(e.getMessage().startsWith("CNPJ must have 14 digits"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000000", "99.999.999/9999-99"})
    void rejectsAllDigitsEqual(String raw) {
        assertThrows(InvalidValueException.class, () -> new Cnpj(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissing(String raw) {
        assertThrows(InvalidValueException.class, () -> new Cnpj(raw));
    }
}
