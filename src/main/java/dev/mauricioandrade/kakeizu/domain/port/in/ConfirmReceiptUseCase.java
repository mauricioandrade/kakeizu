package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.ConfirmResult;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import java.util.UUID;

public interface ConfirmReceiptUseCase {

    ConfirmResult confirm(UUID id, ReceiptFields correctedFields);
}
