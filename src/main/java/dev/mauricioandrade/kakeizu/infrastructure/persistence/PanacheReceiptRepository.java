package dev.mauricioandrade.kakeizu.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ChequeReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Cmc7;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.FieldRecovery;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.Payment;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord.Confirmation;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.port.out.ReceiptRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class PanacheReceiptRepository implements ReceiptRepository {

    private final ObjectMapper objectMapper;

    public PanacheReceiptRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(ReceiptRecord receipt) {
        ReceiptEntity entity = ReceiptEntity.<ReceiptEntity>findByIdOptional(receipt.id()).orElseGet(ReceiptEntity::new);
        copy(receipt, entity);
        try {
            entity.persistAndFlush();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public Optional<ReceiptRecord> findById(UUID id) {
        return ReceiptEntity.<ReceiptEntity>findByIdOptional(id).map(this::toDomain);
    }

    @Override
    public Optional<ReceiptRecord> findByImageSha256(String imageSha256) {
        return ReceiptEntity.<ReceiptEntity>find("imageSha256", imageSha256).firstResultOptional().map(this::toDomain);
    }

    @Override
    public List<ReceiptRecord> findReading() {
        return ReceiptEntity.<ReceiptEntity>list("status", Sort.ascending("createdAt"), ReceiptStatus.READING)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ReceiptRecord> findConfirmedByUniqueId(ReceiptType type, String uniqueId) {
        return ReceiptEntity.<ReceiptEntity>find("status = ?1 and type = ?2 and uniqueId = ?3",
                        ReceiptStatus.CONFIRMED, type, uniqueId)
                .firstResultOptional()
                .map(this::toDomain);
    }

    @Override
    public List<ReceiptRecord> findCreatedBetween(Instant from, Instant to) {
        return ReceiptEntity.<ReceiptEntity>list("createdAt >= ?1 and createdAt < ?2",
                        Sort.descending("createdAt"), from, to)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ReceiptRecord> findConfirmedIssuedOn(LocalDate day) {
        return ReceiptEntity.<ReceiptEntity>list("status = ?1 and issuedOn = ?2", ReceiptStatus.CONFIRMED, day)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ReceiptRecord> findConfirmedSalesIssuedBetween(LocalDate from, LocalDate to) {
        return ReceiptEntity.<ReceiptEntity>list("status = ?1 and direction = ?2 and issuedOn between ?3 and ?4",
                        ReceiptStatus.CONFIRMED, Direction.SALE, from, to)
                .stream().map(this::toDomain).toList();
    }

    private void copy(ReceiptRecord receipt, ReceiptEntity entity) {
        entity.id = receipt.id();
        entity.status = receipt.status();
        entity.category = receipt.category();
        entity.fields = toJson(receipt.fields());
        entity.imageSha256 = receipt.imageSha256();
        entity.imageContentType = receipt.imageContentType();
        entity.rawModelOutput = receipt.rawModelOutput();
        entity.fieldRecoveries = toJson(receipt.recoveries().stream().map(StoredRecovery::from).toList());
        entity.createdAt = receipt.createdAt();
        receipt.confirmation().ifPresentOrElse(c -> copyConfirmed(c, entity), () -> clearConfirmed(entity));
    }

    private static void copyConfirmed(Confirmation confirmation, ReceiptEntity entity) {
        var payment = confirmation.payment();
        entity.issuedTime = payment.time();
        entity.paymentMethod = payment.method();
        entity.installments = (short) payment.installments();
        entity.cardBrand = payment.brand();
        entity.direction = confirmation.direction();
        copyReceipt(confirmation.receipt(), entity);
    }

    private static void copyReceipt(Receipt receipt, ReceiptEntity entity) {
        entity.amount = receipt.amount().value();
        entity.issuedOn = receipt.issuedOn();
        entity.counterparty = receipt.counterparty();
        switch (receipt) {
            case FiscalInvoice(_, _, _, var cnpj, var accessKey) -> setIdentity(entity, ReceiptType.NFE, cnpj.value(), accessKey.value());
            case FiscalCoupon(_, _, _, var cnpj, var accessKey) -> setIdentity(entity, ReceiptType.NFCE, cnpj.value(), accessKey.value());
            case PixReceipt(_, _, _, var endToEndId) ->
                    setIdentity(entity, ReceiptType.PIX, null, endToEndId == null ? null : endToEndId.value());
            case CardReceipt(_, _, _, var cnpj, var authorizationCode) ->
                    setIdentity(entity, ReceiptType.CARD, cnpj == null ? null : cnpj.value(), authorizationCode);
            case ChequeReceipt(_, _, _, var cmc7) ->
                    setIdentity(entity, ReceiptType.CHEQUE, null, cmc7 == null ? null : cmc7.value());
        }
    }

    private static void setIdentity(ReceiptEntity entity, ReceiptType type, String taxId, String uniqueId) {
        entity.type = type;
        entity.taxId = taxId;
        entity.uniqueId = uniqueId;
    }

    private static void clearConfirmed(ReceiptEntity entity) {
        entity.amount = null;
        entity.issuedOn = null;
        entity.counterparty = null;
        setIdentity(entity, null, null, null);
        entity.issuedTime = null;
        entity.paymentMethod = null;
        entity.installments = null;
        entity.cardBrand = null;
        entity.direction = null;
    }

    private ReceiptRecord toDomain(ReceiptEntity entity) {
        return new ReceiptRecord(entity.id, entity.status, fromJson(entity.fields), entity.category,
                Optional.ofNullable(entity.type).map(_ -> toConfirmation(entity)),
                entity.imageSha256, entity.imageContentType, entity.rawModelOutput,
                recoveriesFromJson(entity.fieldRecoveries), entity.createdAt);
    }

    // Receipts confirmed before V2 have no payment columns
    private static Confirmation toConfirmation(ReceiptEntity entity) {
        var payment = entity.installments == null ? Payment.unknown()
                : new Payment(entity.issuedTime, entity.paymentMethod, entity.installments, entity.cardBrand);
        return new Confirmation(toReceipt(entity), payment, entity.direction);
    }

    private static Receipt toReceipt(ReceiptEntity entity) {
        var amount = new Money(entity.amount);
        return switch (entity.type) {
            case NFE -> new FiscalInvoice(amount, entity.issuedOn, entity.counterparty,
                    new Cnpj(entity.taxId), new NfeAccessKey(entity.uniqueId));
            case NFCE -> new FiscalCoupon(amount, entity.issuedOn, entity.counterparty,
                    new Cnpj(entity.taxId), new NfeAccessKey(entity.uniqueId));
            case PIX -> new PixReceipt(amount, entity.issuedOn, entity.counterparty,
                    entity.uniqueId == null ? null : new PixEndToEndId(entity.uniqueId));
            case CARD -> new CardReceipt(amount, entity.issuedOn, entity.counterparty,
                    entity.taxId == null ? null : new Cnpj(entity.taxId), entity.uniqueId);
            case CHEQUE -> new ChequeReceipt(amount, entity.issuedOn, entity.counterparty,
                    entity.uniqueId == null ? null : new Cmc7(entity.uniqueId));
        };
    }

    // Unique constraints are the final word on duplicates, even with concurrent uploads
    private static RuntimeException translate(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                switch (violation.getConstraintName()) {
                    case "uk_receipts_image_sha256" -> { return DuplicateReceiptException.sameImage(); }
                    case "uk_receipts_confirmed_unique_id" -> { return DuplicateReceiptException.sameUniqueId(); }
                    default -> { }
                }
            }
        }
        return e;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ReceiptFields fromJson(String json) {
        try {
            return objectMapper.readValue(json, ReceiptFields.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<FieldRecovery> recoveriesFromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<StoredRecovery>>() { }).stream()
                    .map(StoredRecovery::toDomain).toList();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
