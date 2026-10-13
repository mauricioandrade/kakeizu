package dev.mauricioandrade.kakeizu.domain.port.in;

import java.util.UUID;

public interface RejectReceiptUseCase {

    void reject(UUID id);
}
