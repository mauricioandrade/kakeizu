package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Cmc7Test {

    // Made up: bank 001, agency 1234, cheque 000123, account 12345
    static final String LINE = "00112341 0180001235 200000123455";

    @Test
    void acceptsTheLineAsPrintedWithItsSeparators() {
        assertEquals("001123410180001235200000123455", new Cmc7("<00112341<0180001235>200000123455:").value());
        assertEquals("001123410180001235200000123455", new Cmc7(LINE).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00112341 0180001235 200000123465", // account misread
            "00112341 0180001285 200000123455", // cheque number misread
            "00172341 0180001235 200000123455"  // agency misread
    })
    void anyMisreadDigitBreaksItsGroupCheckDigit(String raw) {
        var e = assertThrows(InvalidValueException.class, () -> new Cmc7(raw));
        assertEquals("CMC-7 check digit does not match", e.getMessage());
    }

    @Test
    void mustHaveThirtyDigits() {
        var e = assertThrows(InvalidValueException.class, () -> new Cmc7("00112341 0180001235 20000012345"));
        assertEquals("CMC-7 line must have 30 digits, got 29", e.getMessage());
        assertThrows(InvalidValueException.class, () -> new Cmc7("00112341 018000123S 200000123455"));
    }
}
