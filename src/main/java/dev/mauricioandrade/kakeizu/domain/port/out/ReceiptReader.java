package dev.mauricioandrade.kakeizu.domain.port.out;

import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import dev.mauricioandrade.kakeizu.domain.model.CheckDigitField;
import dev.mauricioandrade.kakeizu.domain.model.FieldReading;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;

/**
 * Reads receipt data from an image. Implemented by the local AI model; nothing it returns is trusted.
 */
public interface ReceiptReader {

    /**
     * @throws ReceiptReadingException if the model is unavailable or its answer cannot be used
     */
    ModelReading read(byte[] image, String contentType);

    /**
     * Reads a single field again, from the whole image, when the first reading failed its check digits.
     *
     * @throws ReceiptReadingException if the model is unavailable or its answer cannot be used
     */
    FieldReading reread(byte[] image, String contentType, CheckDigitField field);
}
