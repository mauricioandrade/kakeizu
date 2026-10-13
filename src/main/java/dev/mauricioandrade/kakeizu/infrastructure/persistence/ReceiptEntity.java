package dev.mauricioandrade.kakeizu.infrastructure.persistence;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipts")
public class ReceiptEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    public ReceiptStatus status;

    @Enumerated(EnumType.STRING)
    public Category category;

    @JdbcTypeCode(SqlTypes.JSON)
    public String fields;

    public String imageSha256;

    public String imageContentType;

    @JdbcTypeCode(SqlTypes.JSON)
    public String rawModelOutput;

    public Instant createdAt;

    @Enumerated(EnumType.STRING)
    public ReceiptType type;

    @Column(precision = 14, scale = 2)
    public BigDecimal amount;

    public LocalDate issuedOn;

    public String counterparty;

    public String taxId;

    public String uniqueId;
}
