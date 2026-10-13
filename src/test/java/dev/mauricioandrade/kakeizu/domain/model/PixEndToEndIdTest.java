package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PixEndToEndIdTest {

    @Test
    void acceptsValidId() {
        assertEquals("E00416968202208120422Mmzi9rPYPLT", new PixEndToEndId("E00416968202208120422Mmzi9rPYPLT").value());
    }

    @Test
    void rejectsWhatGemmaReadFromTheSample() {
        // Real gemma3:4b output for samples/pix-inter.png: one extra character
        var e = assertThrows(InvalidValueException.class, () -> new PixEndToEndId("E00416968202208120422Mzmiz9rPYPLT"));
        assertEquals("Pix transaction ID must have 32 characters, got 33", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"D00416968202208120422Mmzi9rPYPLT", "E00416968202208120422Mmzi9r-YPLT"})
    void rejectsWrongPrefixOrSymbols(String raw) {
        var e = assertThrows(InvalidValueException.class, () -> new PixEndToEndId(raw));
        assertEquals("Pix transaction ID must start with E and be alphanumeric", e.getMessage());
    }
}
