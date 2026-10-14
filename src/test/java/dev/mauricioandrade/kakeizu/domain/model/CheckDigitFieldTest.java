package dev.mauricioandrade.kakeizu.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CheckDigitFieldTest {

    static final String KEY = "35261023010551000131650010000123451123456787";

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
            "NFCE, 23.010.551/0001-31, " + KEY + ",                                  ''",
            "NFE,  23.010.551/0001-81, " + KEY + ",                                  CNPJ",
            "nfce, 23.010.551/0001-31, 35261023010551000131650070000123451123456787, NFE_ACCESS_KEY",
            "NFCE, 2301055100013,      3526,                                         CNPJ NFE_ACCESS_KEY",
            "NFCE, -,                  -,                                            ''",   // missing: not a misread
            "CARD, 23.010.551/0001-81, 2UP8IQ,                                       CNPJ",
            "CARD, -,                  2UP8IQ,                                       ''",
            "PIX,  23.010.551/0001-81, E123,                                         ''",   // no check digit on a Pix ID
            "CHEQUE, -,                123,                                          ''",
            "-,    23.010.551/0001-81, -,                                            ''",
            "BOLETO, 23.010.551/0001-81, -,                                          ''"
    })
    void failingFieldsAreTheGuardedOnesReadButInvalid(String type, String taxId, String uniqueId, String failing) {
        var fields = new ReceiptFields(type, null, null, null, taxId, uniqueId, null, null, null, null, null, null);

        var expected = failing.isBlank() ? List.<CheckDigitField>of()
                : Arrays.stream(failing.split(" ")).map(CheckDigitField::valueOf).toList();
        assertEquals(expected, CheckDigitField.failingIn(fields));
    }
}
