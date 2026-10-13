package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1234.56      | 1234.56",
            "1.234,56     | 1234.56",
            "R$ 1.234,56  | 1234.56",
            "R$ 80,00 | 80.00",
            "25.000,00    | 25000.00",
            "500,5        | 500.50",
            "80           | 80.00",
            "0            | 0.00"
    })
    void parsesBothFormats(String raw, String expected) {
        assertEquals(new BigDecimal(expected), new Money(raw).value());
    }

    @Test
    void alwaysHasScaleTwo() {
        assertEquals(2, new Money(new BigDecimal("80")).value().scale());
    }

    @Test
    void rejectsNegative() {
        var e = assertThrows(InvalidValueException.class, () -> new Money("-10,00"));
        assertEquals("Amount cannot be negative", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.234", "12.345"})
    void dotWithoutCommaIsADecimalPointSoThreeDecimalsAreRejected(String raw) {
        assertThrows(InvalidValueException.class, () -> new Money(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.23,45", "1,234.56", "12,34,56", "R$"})
    void rejectsUnreadable(String raw) {
        assertThrows(InvalidValueException.class, () -> new Money(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissing(String raw) {
        assertThrows(InvalidValueException.class, () -> new Money(raw));
    }

    @Test
    void adds() {
        assertEquals(new Money("1.000,10"), new Money("999.60").plus(new Money("0,50")));
    }
}
