package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import io.quarkus.qute.TemplateExtension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Display helpers for the templates, so the domain stays free of presentation code.
 */
@TemplateExtension
public class TemplateExtensions {

    private static final Locale BRAZIL = Locale.of("pt", "BR");

    private TemplateExtensions() {
    }

    // {money.brl} -> R$ 1.234,56
    static String brl(Money money) {
        return NumberFormat.getCurrencyInstance(BRAZIL).format(money.value());
    }

    // {receipt.amountLabel} -> R$ 1.234,56 when the amount parses, else as typed (a pending receipt may be wrong)
    static String amountLabel(ReceiptRecord receipt) {
        String raw = receipt.fields().amount();
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        try {
            return brl(new Money(raw));
        } catch (InvalidValueException _) {
            return raw;
        }
    }

    static String label(ReceiptType type) {
        return switch (type) {
            case NFE -> "NF-e";
            case NFCE -> "NFC-e";
            case PIX -> "Pix";
            case CARD -> "Card";
            case CHEQUE -> "Cheque";
        };
    }

    static String label(Category category) {
        return switch (category) {
            case FUEL_SUPPLY -> "Fuel supply";
            case GOODS -> "Goods";
            case SERVICES -> "Services";
            case UTILITIES -> "Utilities";
            case PAYROLL -> "Payroll";
            case OTHER -> "Other";
        };
    }

    static String label(ReceiptStatus status) {
        return switch (status) {
            case PENDING_REVIEW -> "To review";
            case CONFIRMED -> "Confirmed";
            case REJECTED -> "Rejected";
        };
    }

    static String label(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Cash";
            case CREDIT -> "Credit";
            case DEBIT -> "Debit";
            case PIX -> "Pix";
            case CHEQUE -> "Cheque";
            case OTHER -> "Other";
        };
    }

    static String label(CardBrand brand) {
        return switch (brand) {
            case VISA -> "Visa";
            case MASTERCARD -> "Mastercard";
            case ELO -> "Elo";
            case AMEX -> "American Express";
            case HIPERCARD -> "Hipercard";
            case OTHER -> "Other";
        };
    }

    static String label(Direction direction) {
        return switch (direction) {
            case SALE -> "Sale (the shop received it)";
            case EXPENSE -> "Expense (the shop paid it)";
        };
    }

    static String label(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    // {hour.hourRange} -> 14:00–14:59
    static String hourRange(Integer hour) {
        return "%02d:00–%02d:59".formatted(hour, hour);
    }

    // {count.installmentsLabel} -> "Upfront" or "3 installments"
    static String installmentsLabel(Integer installments) {
        return installments == 1 ? "Upfront (1x)" : installments + " installments";
    }

    // Share of the total, 0-100, for a <meter>: the strict CSP forbids inline styles for bar widths
    static long percentOf(Money part, Money total) {
        if (total.value().signum() == 0) {
            return 0;
        }
        return part.value().multiply(BigDecimal.valueOf(100)).divide(total.value(), 0, RoundingMode.HALF_UP).longValue();
    }

    static boolean pending(ReceiptRecord receipt) {
        return receipt.status() == ReceiptStatus.PENDING_REVIEW;
    }

    // {result.issuesFor('amount')}
    static List<ValidationIssue> issuesFor(ExtractionResult result, String field) {
        return result.issues().stream().filter(issue -> issue.field().equals(field)).toList();
    }

    static boolean hasErrorIn(ExtractionResult result, String field) {
        return issuesFor(result, field).stream().anyMatch(ValidationIssue::isError);
    }

    // {status.cssClass} -> pending-review
    static String cssClass(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
