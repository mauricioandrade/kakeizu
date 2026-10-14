package dev.mauricioandrade.kakeizu.domain.service;

import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Ambiguous;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Recovered;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Unrecoverable;
import dev.mauricioandrade.kakeizu.domain.model.RecoverySource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.function.Predicate;

/**
 * Looks for the value the model meant when a field fails its check digits, by swapping digits that small
 * printed fonts make look alike, one position at a time (check digits included: the misread may be there).
 *
 * <p>A key with a single mod 11 check digit lets about 1 in 11 swaps pass by chance, so one reading of a
 * 44-digit key almost always leaves several candidates. A second reading that misread a different digit
 * narrows them down: the right value is a candidate of both.
 */
public class CheckDigitRecovery {

    private static final List<String> CONFUSED_PAIRS = List.of("83", "17", "08", "56", "68", "27");

    // Both ways: '8' -> "306", '3' -> "8", ...
    static final Map<Character, String> CONFUSIONS = confusions();

    /**
     * @param original what the model read first, already known to fail {@code isValid}
     * @param reread   what it read when asked for this field alone; null if not asked or not readable
     */
    public RecoveryResult recover(String original, String reread, Predicate<String> isValid) {
        if (reread != null && isValid.test(normalize(reread))) {
            return new Recovered(normalize(reread), RecoverySource.REREAD);
        }
        var fromOriginal = candidates(original, isValid);
        var fromReread = candidates(reread, isValid);
        var inBoth = new LinkedHashSet<>(fromOriginal);
        inBoth.retainAll(fromReread);
        SequencedSet<String> pool = inBoth;
        if (pool.isEmpty()) {
            // The readings do not agree on anything, or there is only one: keep every candidate
            pool = new LinkedHashSet<>(fromOriginal);
            pool.addAll(fromReread);
        }
        return switch (pool.size()) {
            case 0 -> new Unrecoverable();
            case 1 -> new Recovered(pool.getFirst(), RecoverySource.CONFUSION_SEARCH);
            default -> new Ambiguous(List.copyOf(pool));
        };
    }

    /**
     * Every value one confused digit away from {@code value} that passes {@code isValid}, in position order.
     */
    public List<String> candidates(String value, Predicate<String> isValid) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String read = normalize(value);
        var found = new LinkedHashSet<String>();
        char[] chars = read.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char misread = chars[i];
            for (char meant : CONFUSIONS.getOrDefault(misread, "").toCharArray()) {
                chars[i] = meant;
                String candidate = new String(chars);
                if (isValid.test(candidate)) {
                    found.add(candidate);
                }
            }
            chars[i] = misread;
        }
        return new ArrayList<>(found);
    }

    // Same cleanup as the value objects, so positions line up whatever the model put between the groups
    private static String normalize(String value) {
        return value.strip().replaceAll("[.\\-/\\s]", "").toUpperCase();
    }

    private static Map<Character, String> confusions() {
        var confusions = new HashMap<Character, String>();
        for (String pair : CONFUSED_PAIRS) {
            confusions.merge(pair.charAt(0), String.valueOf(pair.charAt(1)), String::concat);
            confusions.merge(pair.charAt(1), String.valueOf(pair.charAt(0)), String::concat);
        }
        return Map.copyOf(confusions);
    }
}
