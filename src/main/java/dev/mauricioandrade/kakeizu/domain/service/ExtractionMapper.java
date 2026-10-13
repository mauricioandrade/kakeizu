package dev.mauricioandrade.kakeizu.domain.service;

import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.error;
import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.warning;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Turns untrusted text fields into a {@link Receipt}, collecting every problem instead of stopping at the first.
 */
public class ExtractionMapper {

    private final ReceiptValidator validator;

    public ExtractionMapper(ReceiptValidator validator) {
        this.validator = validator;
    }

    public ExtractionResult map(ReceiptFields fields) {
        var issues = new ArrayList<ValidationIssue>();
        ReceiptType type = parse("type", fields.type(), ExtractionMapper::parseType, issues);
        Money amount = parse("amount", fields.amount(), Money::new, issues);
        LocalDate date = parse("date", fields.date(), ExtractionMapper::parseDate, issues);
        String counterparty = parse("counterparty", fields.counterparty(), Function.identity(), issues);

        Optional<Receipt> receipt = type == null ? Optional.empty() : switch (type) {
            case NFE -> {
                var cnpj = parse("taxId", fields.taxId(), Cnpj::new, issues);
                var accessKey = parse("uniqueId", fields.uniqueId(), NfeAccessKey::new, issues);
                yield buildIfValid(issues, () -> new FiscalInvoice(amount, date, counterparty, cnpj, accessKey));
            }
            case NFCE -> {
                var cnpj = parse("taxId", fields.taxId(), Cnpj::new, issues);
                var accessKey = parse("uniqueId", fields.uniqueId(), NfeAccessKey::new, issues);
                yield buildIfValid(issues, () -> new FiscalCoupon(amount, date, counterparty, cnpj, accessKey));
            }
            case PIX -> {
                var endToEndId = parse("uniqueId", fields.uniqueId(), PixEndToEndId::new, issues);
                yield buildIfValid(issues, () -> new PixReceipt(amount, date, counterparty, endToEndId));
            }
            case CARD -> {
                var cnpj = parse("taxId", fields.taxId(), Cnpj::new, issues);
                var authorizationCode = blankToNull(fields.uniqueId());
                yield buildIfValid(issues, () -> new CardReceipt(amount, date, counterparty, cnpj, authorizationCode));
            }
        };
        receipt.ifPresent(r -> issues.addAll(validator.validate(r)));

        return new ExtractionResult(fields, receipt, parseCategory(fields.category(), issues), issues);
    }

    private static <T> T parse(String field, String raw, Function<String, T> parser, List<ValidationIssue> issues) {
        if (raw == null || raw.isBlank()) {
            issues.add(error(field, "Not found on the receipt"));
            return null;
        }
        try {
            return parser.apply(raw.strip());
        } catch (InvalidValueException e) {
            issues.add(error(field, e.getMessage()));
            return null;
        }
    }

    private static Optional<Receipt> buildIfValid(List<ValidationIssue> issues, Supplier<Receipt> receipt) {
        return issues.stream().anyMatch(ValidationIssue::isError) ? Optional.empty() : Optional.of(receipt.get());
    }

    private static ReceiptType parseType(String raw) {
        try {
            return ReceiptType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new InvalidValueException("Receipt type not recognized: " + raw);
        }
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException _) {
            throw new InvalidValueException("Date must be yyyy-MM-dd, got: " + raw);
        }
    }

    // The category is only a suggestion: anything unknown falls back to OTHER
    private static Category parseCategory(String raw, List<ValidationIssue> issues) {
        if (raw == null || raw.isBlank()) {
            return Category.OTHER;
        }
        try {
            return Category.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException _) {
            issues.add(warning("category", "Category not recognized, using OTHER: " + raw));
            return Category.OTHER;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
