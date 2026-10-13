package dev.mauricioandrade.kakeizu.domain.port.out;

import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository {

    /**
     * Inserts or updates.
     *
     * @throws DuplicateReceiptException if the image or the confirmed identifier already exists
     */
    void save(ReceiptRecord receipt);

    Optional<ReceiptRecord> findById(UUID id);

    Optional<ReceiptRecord> findByImageSha256(String imageSha256);

    Optional<ReceiptRecord> findConfirmedByUniqueId(ReceiptType type, String uniqueId);

    /**
     * Newest first; {@code to} is exclusive.
     */
    List<ReceiptRecord> findCreatedBetween(Instant from, Instant to);

    List<ReceiptRecord> findConfirmedIssuedOn(LocalDate day);
}
