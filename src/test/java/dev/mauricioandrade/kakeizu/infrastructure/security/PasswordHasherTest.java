package dev.mauricioandrade.kakeizu.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    static final String HASH_OF_KAKEIZU =
            "pbkdf2-sha256:600000:zEWL24zRy3J8D/1XdDCFbw==:cyR2atKMWaNlJ2v1AwsG8v4e1ds69V+zSqQlsPYrNDo=";

    @Test
    void verifiesTheRightPasswordOnly() {
        assertTrue(PasswordHasher.verify("kakeizu".toCharArray(), HASH_OF_KAKEIZU));
        assertFalse(PasswordHasher.verify("Kakeizu".toCharArray(), HASH_OF_KAKEIZU));
        assertFalse(PasswordHasher.verify(new char[0], HASH_OF_KAKEIZU));
    }

    @Test
    void samePasswordGetsADifferentSaltEachTime() {
        String first = PasswordHasher.hash("secret".toCharArray());
        String second = PasswordHasher.hash("secret".toCharArray());

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify("secret".toCharArray(), first));
        assertTrue(PasswordHasher.verify("secret".toCharArray(), second));
    }

    @Test
    void rejectsPlainTextOrUnknownFormat() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.verify("kakeizu".toCharArray(), "kakeizu"));
    }
}
