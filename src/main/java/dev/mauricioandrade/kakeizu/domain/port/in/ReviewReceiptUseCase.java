package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptReview;
import java.util.UUID;

public interface ReviewReceiptUseCase {

    ReceiptReview review(UUID id);
}
