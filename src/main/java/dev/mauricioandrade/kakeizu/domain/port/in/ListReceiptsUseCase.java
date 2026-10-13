package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import java.time.LocalDate;
import java.util.List;

public interface ListReceiptsUseCase {

    /**
     * Receipts uploaded on the given day (business time zone), newest first.
     */
    List<ReceiptRecord> uploadedOn(LocalDate day);
}
