package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Same rules as the database: unique image hash, unique confirmed identifier.
 */
class InMemoryReceiptRepository implements ReceiptRepository {

    final Map<UUID, ReceiptRecord> receipts = new LinkedHashMap<>();

    @Override
    public void save(ReceiptRecord receipt) {
        boolean sameImage = receipts.values().stream()
                .anyMatch(r -> !r.id().equals(receipt.id()) && r.imageSha256().equals(receipt.imageSha256()));
        if (sameImage) {
            throw DuplicateReceiptException.sameImage();
        }
        receipts.put(receipt.id(), receipt);
    }

    @Override
    public Optional<ReceiptRecord> findById(UUID id) {
        return Optional.ofNullable(receipts.get(id));
    }

    @Override
    public Optional<ReceiptRecord> findByImageSha256(String imageSha256) {
        return receipts.values().stream().filter(r -> r.imageSha256().equals(imageSha256)).findFirst();
    }

    @Override
    public Optional<ReceiptRecord> findConfirmedByUniqueId(ReceiptType type, String uniqueId) {
        return confirmed().filter(r -> {
            var receipt = r.confirmedReceipt().orElseThrow();
            return receipt.type() == type && receipt.deduplicationKey().equals(Optional.of(uniqueId));
        }).findFirst();
    }

    @Override
    public List<ReceiptRecord> findCreatedBetween(Instant from, Instant to) {
        return receipts.values().stream()
                .filter(r -> !r.createdAt().isBefore(from) && r.createdAt().isBefore(to))
                .sorted(Comparator.comparing(ReceiptRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public List<ReceiptRecord> findConfirmedIssuedOn(LocalDate day) {
        return confirmed().filter(r -> r.confirmedReceipt().orElseThrow().issuedOn().equals(day)).toList();
    }

    @Override
    public List<ReceiptRecord> findConfirmedSalesIssuedBetween(LocalDate from, LocalDate to) {
        return confirmed().filter(r -> {
            var confirmation = r.confirmation().orElseThrow();
            var day = confirmation.receipt().issuedOn();
            return confirmation.direction() == Direction.SALE && !day.isBefore(from) && !day.isAfter(to);
        }).toList();
    }

    private java.util.stream.Stream<ReceiptRecord> confirmed() {
        return receipts.values().stream().filter(r -> r.status() == ReceiptStatus.CONFIRMED);
    }
}
