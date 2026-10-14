package dev.mauricioandrade.kakeizu.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Ambiguous;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Recovered;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Unrecoverable;
import dev.mauricioandrade.kakeizu.domain.model.RecoverySource;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CheckDigitRecoveryTest {

    private static final Predicate<String> VALID_CNPJ = passes(Cnpj::new);
    private static final Predicate<String> VALID_KEY = passes(NfeAccessKey::new);

    private static final String CNPJ = "23010551000131";
    // State 35, October 2026, the CNPJ above, NFC-e, series 1, note 123451, random code 12345678
    private static final String KEY = withKeyCheckDigit("3526102301055100013165001000012345112345678");

    private final CheckDigitRecovery recovery = new CheckDigitRecovery();

    @ParameterizedTest
    @CsvSource({
            "73010551000131", // 2 read as 7
            "23010557000131", // 1 read as 7
            "23010551800131", // 0 read as 8
            "23010551000181", // first check digit: 3 read as 8
            "23010551000137", // second check digit: 1 read as 7
            "23.010.551/0001-81"
    })
    void recoversACnpjWithOneMisreadDigit(String misread) {
        var result = recovery.recover(misread, null, VALID_CNPJ);

        assertEquals(new Recovered(CNPJ, RecoverySource.CONFUSION_SEARCH), result);
    }

    @Test
    void everyDetectedConfusionOfACnpjKeepsTheRightValueAmongCandidates() {
        var random = new Random(42);
        for (int n = 0; n < 50; n++) {
            String cnpj = randomCnpj(random);
            for (String misread : singleConfusions(cnpj)) {
                if (VALID_CNPJ.test(misread)) {
                    continue; // mod 11 maps remainders 0 and 1 to the same check digit: rare, but possible
                }
                assertTrue(recovery.candidates(misread, VALID_CNPJ).contains(cnpj), misread);
            }
        }
    }

    @Test
    void everyDetectedConfusionOfAnAccessKeyKeepsTheRightValueAmongCandidates() {
        var random = new Random(42);
        for (int n = 0; n < 50; n++) {
            String key = randomKey(random);
            for (String misread : singleConfusions(key)) {
                if (VALID_KEY.test(misread)) {
                    continue;
                }
                assertTrue(recovery.candidates(misread, VALID_KEY).contains(key), misread);
            }
        }
    }

    // Why a fix found this way is only ever a suggestion
    @Test
    void aMisreadDigitCanPassTheAccessKeyCheckDigit() {
        var random = new Random(42);
        boolean undetected = false;
        for (int n = 0; n < 50 && !undetected; n++) {
            undetected = singleConfusions(randomKey(random)).stream().anyMatch(VALID_KEY);
        }
        assertTrue(undetected);
    }

    @Test
    void ambiguousWhenTwoSwapsPassAndNoneIsChosen() {
        var result = recovery.recover("11272333000181", null, VALID_CNPJ);

        assertEquals(new Ambiguous(List.of("11222333000181", "11272333000781")), result);
    }

    @Test
    void oneReadingOfAnAccessKeyUsuallyLeavesSeveralCandidates() {
        String misread = swap(KEY, 30, '7'); // 2 of the note number read as 7

        var result = assertInstanceOf(Ambiguous.class, recovery.recover(misread, null, VALID_KEY));

        assertTrue(result.candidates().size() > 1);
        assertTrue(result.candidates().contains(KEY));
    }

    @Test
    void aRereadWithAnotherMisreadDigitNarrowsTheCandidatesDown() {
        String first = swap(KEY, 30, '7');
        String reread = swap(KEY, 37, '8'); // 3 of the random code read as 8

        var result = recovery.recover(first, reread, VALID_KEY);

        assertEquals(new Recovered(KEY, RecoverySource.CONFUSION_SEARCH), result);
    }

    @Test
    void aRereadThatPassesIsTakenAsIs() {
        String grouped = KEY.replaceAll("(.{4})", "$1 ").strip();

        var result = recovery.recover(swap(KEY, 30, '7'), grouped, VALID_KEY);

        assertEquals(new Recovered(KEY, RecoverySource.REREAD), result);
    }

    @Test
    void theSameMisreadTwiceIsLikeOneReading() {
        String misread = swap(KEY, 30, '7');

        assertEquals(recovery.recover(misread, null, VALID_KEY), recovery.recover(misread, misread, VALID_KEY));
    }

    @Test
    void aRereadOfTheWrongLengthIsIgnored() {
        var result = recovery.recover("73010551000131", "2301055100013", VALID_CNPJ);

        assertEquals(new Recovered(CNPJ, RecoverySource.CONFUSION_SEARCH), result);
    }

    @Test
    void readingsWithNoCandidateInCommonKeepAllOfThem() {
        var result = recovery.recover("11272333000181", "73010551000131", VALID_CNPJ);

        assertEquals(new Ambiguous(List.of("11222333000181", "11272333000781", CNPJ)), result);
    }

    @ParameterizedTest
    @CsvSource({
            "23010551000431", // 1 read as 4: not a confusion we look for
            "23010551000",    // digits missing
            "2301055100013X"
    })
    void unrecoverableWhenNoConfusedDigitExplainsIt(String misread) {
        assertEquals(new Unrecoverable(), recovery.recover(misread, null, VALID_CNPJ));
    }

    @Test
    void unrecoverableWhenNothingWasRead() {
        assertEquals(new Unrecoverable(), recovery.recover(null, null, VALID_CNPJ));
    }

    @Test
    void confusionsGoBothWays() {
        CheckDigitRecovery.CONFUSIONS.forEach((misread, meant) -> {
            for (char m : meant.toCharArray()) {
                assertTrue(CheckDigitRecovery.CONFUSIONS.get(m).indexOf(misread) >= 0, misread + "<->" + m);
            }
        });
    }

    private static List<String> singleConfusions(String value) {
        var misreads = new java.util.ArrayList<String>();
        for (int i = 0; i < value.length(); i++) {
            for (char c : CheckDigitRecovery.CONFUSIONS.getOrDefault(value.charAt(i), "").toCharArray()) {
                misreads.add(swap(value, i, c));
            }
        }
        return misreads;
    }

    private static String swap(String value, int index, char c) {
        return value.substring(0, index) + c + value.substring(index + 1);
    }

    private static String randomCnpj(Random random) {
        String base = digits(random, 12);
        base += mod11(base, new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return base + mod11(base, new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
    }

    private static String randomKey(Random random) {
        return withKeyCheckDigit("35" + "2610" + randomCnpj(random) + "65" + digits(random, 21));
    }

    private static String withKeyCheckDigit(String first43) {
        int[] weights = new int[43];
        for (int i = 0; i < 43; i++) {
            weights[42 - i] = 2 + i % 8; // 2..9 from right to left
        }
        return first43 + mod11(first43, weights);
    }

    private static int mod11(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static String digits(Random random, int count) {
        var digits = new StringBuilder();
        for (int i = 0; i < count; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }

    private static Predicate<String> passes(Function<String, ?> valueObject) {
        return raw -> {
            try {
                valueObject.apply(raw);
                return true;
            } catch (InvalidValueException _) {
                return false;
            }
        };
    }
}
