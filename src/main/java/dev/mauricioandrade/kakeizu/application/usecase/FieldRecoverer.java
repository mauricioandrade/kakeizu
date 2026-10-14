package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.FieldReading;
import dev.mauricioandrade.kakeizu.domain.model.FieldRecovery;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.RecoveryResult;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptReader;
import dev.mauricioandrade.kakeizu.domain.service.CheckDigitRecovery;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * For each CNPJ or NF-e access key the model read but that fails its check digits: asks the model for that
 * field alone, once, then looks for commonly misread digits in both readings. Only suggests; the fields as
 * read go to review unchanged.
 */
@ApplicationScoped
public class FieldRecoverer {

    private static final Logger LOG = Logger.getLogger(FieldRecoverer.class);

    private final ReceiptReader reader;
    private final CheckDigitRecovery recovery;
    private final boolean rereadEnabled;

    public FieldRecoverer(ReceiptReader reader, CheckDigitRecovery recovery,
                          @ConfigProperty(name = "kakeizu.reread.enabled") boolean rereadEnabled) {
        this.reader = reader;
        this.recovery = recovery;
        this.rereadEnabled = rereadEnabled;
    }

    public List<FieldRecovery> recover(ReceiptFields fields, byte[] image, String contentType) {
        return CheckDigitField.failingIn(fields).stream()
                .map(field -> recover(field, field.valueIn(fields), image, contentType))
                .toList();
    }

    private FieldRecovery recover(CheckDigitField field, String original, byte[] image, String contentType) {
        FieldReading reread = rereadEnabled ? reread(field, image, contentType) : null;
        var result = recovery.recover(original, reread == null ? null : reread.value(), field::isValid);
        // Outcome only: the values are the shop's financial data
        LOG.infof("[%s] %s failed its check digits: %s", Correlation.id(), field, describe(result));
        return new FieldRecovery(field, original, result, reread == null ? null : reread.rawOutput());
    }

    // A failed re-read leaves the misread-digit search on the first reading
    private FieldReading reread(CheckDigitField field, byte[] image, String contentType) {
        try {
            return reader.reread(image, contentType, field);
        } catch (ReceiptReadingException e) {
            LOG.warnf("[%s] Could not read the %s again: %s", Correlation.id(), field, e.toString());
            return null;
        }
    }

    private static String describe(RecoveryResult result) {
        return switch (result) {
            case RecoveryResult.Recovered(_, var source) -> "one suggestion, from " + source;
            case RecoveryResult.Ambiguous(var candidates) -> candidates.size() + " candidates, none suggested";
            case RecoveryResult.Unrecoverable() -> "nothing found";
        };
    }
}
