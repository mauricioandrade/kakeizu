package dev.mauricioandrade.kakeizu.domain.model;

/**
 * How a field that failed its check digits got a suggested correction.
 */
public enum RecoverySource {
    /** A second reading of that field alone passed the check digits. */
    REREAD,
    /** Exactly one commonly misread digit, swapped, makes the check digits pass. */
    CONFUSION_SEARCH
}
