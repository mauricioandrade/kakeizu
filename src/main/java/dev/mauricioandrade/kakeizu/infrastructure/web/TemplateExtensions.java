package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptStatus;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import io.quarkus.qute.TemplateExtension;
import java.text.NumberFormat;
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

    static String label(ReceiptType type) {
        return switch (type) {
            case NFE -> "NF-e";
            case NFCE -> "NFC-e";
            case PIX -> "Pix";
            case CARD -> "Card";
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
