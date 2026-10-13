package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptImage;
import java.util.UUID;

public interface ReceiptImageUseCase {

    ReceiptImage image(UUID id);
}
