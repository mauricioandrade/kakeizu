package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NfeAccessKeyTest {

    // SP, 2022-10, CNPJ 11.222.333/0001-81, model 55; check digit computed independently
    static final String VALID = "35221011222333000181550010000123451123456787";

    @Test
    void acceptsValidKeyAndExposesItsParts() {
        var key = new NfeAccessKey(VALID);
        assertEquals(YearMonth.of(2022, 10), key.issuedIn());
        assertEquals("11222333000181", key.issuerCnpj());
        assertEquals(NfeAccessKey.NFE_MODEL, key.model());
    }

    @Test
    void acceptsKeyPrintedInGroupsOfFour() {
        var grouped = "3522 1011 2223 3300 0181 5500 1000 0123 4511 2345 6787";
        assertEquals(VALID, new NfeAccessKey(grouped).value());
    }

    @Test
    void rejectsWrongCheckDigit() {
        var e = assertThrows(InvalidValueException.class,
                () -> new NfeAccessKey(VALID.substring(0, 43) + "8"));
        assertEquals("Access key check digit does not match", e.getMessage());
    }

    @Test
    void rejectsSingleMisreadDigit() {
        var misread = VALID.substring(0, 30) + "9" + VALID.substring(31);
        assertThrows(InvalidValueException.class, () -> new NfeAccessKey(misread));
    }

    @Test
    void rejectsInvalidMonthEvenWithMatchingCheckDigit() {
        // month 13, check digit recomputed so only the month is wrong
        var e = assertThrows(InvalidValueException.class,
                () -> new NfeAccessKey("35221311222333000181550010000123451123456788"));
        assertEquals("Access key has an invalid year/month: 2213", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3522101122233300018155001000012345112345678", "352210112223330001815500100001234511234567877"})
    void rejectsWrongLength(String raw) {
        assertThrows(InvalidValueException.class, () -> new NfeAccessKey(raw));
    }
}
