package dev.mauricioandrade.kakeizu.infrastructure.persistence;

import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.FieldRecovery;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Ambiguous;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Recovered;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult.Unrecoverable;
import dev.mauricioandrade.kakeizu.domain.model.RecoverySource;
import java.util.List;

/**
 * A {@link FieldRecovery} as one flat JSON object in {@code field_recoveries}, so the sealed result needs no
 * type annotations in the domain and stays easy to query from psql.
 */
record StoredRecovery(CheckDigitField field, String original, Outcome outcome, String suggestion,
                      RecoverySource source, List<String> candidates, String rereadOutput) {

    enum Outcome {
        RECOVERED, AMBIGUOUS, UNRECOVERABLE
    }

    static StoredRecovery from(FieldRecovery recovery) {
        var field = recovery.field();
        var original = recovery.original();
        var rereadOutput = recovery.rereadOutput();
        return switch (recovery.result()) {
            case Recovered(var value, var source) ->
                    new StoredRecovery(field, original, Outcome.RECOVERED, value, source, List.of(), rereadOutput);
            case Ambiguous(var candidates) ->
                    new StoredRecovery(field, original, Outcome.AMBIGUOUS, null, null, candidates, rereadOutput);
            case Unrecoverable() ->
                    new StoredRecovery(field, original, Outcome.UNRECOVERABLE, null, null, List.of(), rereadOutput);
        };
    }

    FieldRecovery toDomain() {
        RecoveryResult result = switch (outcome) {
            case RECOVERED -> new Recovered(suggestion, source);
            case AMBIGUOUS -> new Ambiguous(candidates);
            case UNRECOVERABLE -> new Unrecoverable();
        };
        return new FieldRecovery(field, original, result, rereadOutput);
    }
}
