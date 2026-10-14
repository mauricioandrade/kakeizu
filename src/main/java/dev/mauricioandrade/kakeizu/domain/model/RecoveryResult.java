package dev.mauricioandrade.kakeizu.domain.model;

import java.util.List;

/**
 * What could be done about a field that failed its check digits. Always a suggestion for review:
 * a value that passes the check digits can still be wrong.
 */
public sealed interface RecoveryResult {

    /**
     * One value passes: shown as a suggested correction.
     */
    record Recovered(String value, RecoverySource source) implements RecoveryResult {
    }

    /**
     * Several values pass: shown as hints, none chosen.
     */
    record Ambiguous(List<String> candidates) implements RecoveryResult {

        public Ambiguous {
            candidates = List.copyOf(candidates);
        }
    }

    /**
     * Nothing passes: the field stays flagged.
     */
    record Unrecoverable() implements RecoveryResult {
    }
}
