package dev.mauricioandrade.kakeizu.domain.service;

import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.error;
import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.warning;

import dev.mauricioandrade.kakeizu.domain.exception.InvalidValueException;
import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ChequeReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Cmc7;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.Money;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.Payment;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.PixEndToEndId;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.Shop;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.time.LocalDate;
import java.time.LocalTime;
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

    static final String NO_CHEQUES = "This shop does not accept cheques (set KAKEIZU_SHOP_ACCEPTS_CHEQUES=true to change that)";

    private final ReceiptValidator validator;
    private final Shop shop;

    public ExtractionMapper(ReceiptValidator validator, Shop shop) {
        this.validator = validator;
        this.shop = shop;
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
                var endToEndId = parseIfPresent("uniqueId", fields.uniqueId(), PixEndToEndId::new, issues);
                yield buildIfValid(issues, () -> new PixReceipt(amount, date, counterparty, endToEndId));
            }
            case CARD -> {
                var cnpj = parseIfPresent("taxId", fields.taxId(), Cnpj::new, issues);
                var authorizationCode = blankToNull(fields.uniqueId());
                yield buildIfValid(issues, () -> new CardReceipt(amount, date, counterparty, cnpj, authorizationCode));
            }
            case CHEQUE -> {
                if (!shop.acceptsCheques()) {
                    issues.add(error("type", NO_CHEQUES));
                    yield Optional.empty();
                }
                var cmc7 = parseIfPresent("uniqueId", fields.uniqueId(), Cmc7::new, issues);
                yield buildIfValid(issues, () -> new ChequeReceipt(amount, date, counterparty, cmc7));
            }
        };
        receipt.ifPresent(r -> issues.addAll(validator.validate(r)));

        var payment = parsePayment(fields, type, shop.acceptsCheques(), issues);
        var direction = parseDirection(fields, type, issues);
        return new ExtractionResult(fields, receipt, parseCategory(fields.category(), issues), payment, direction,
                issues);
    }

    private static Optional<Payment> parsePayment(ReceiptFields fields, ReceiptType type, boolean acceptsCheques,
                                                  List<ValidationIssue> issues) {
        int before = issues.size();
        LocalTime time = parseIfPresent("time", fields.time(), ExtractionMapper::parseTime, issues);
        PaymentMethod method = parseIfPresent("paymentMethod", fields.paymentMethod(),
                raw -> parseEnum(PaymentMethod.class, raw, "Payment method"), issues);
        Integer installments = parseIfPresent("installments", fields.installments(),
                ExtractionMapper::parseInstallments, issues);
        CardBrand brand = parseCardBrand(fields.cardBrand(), issues);

        if (type == ReceiptType.PIX || type == ReceiptType.CHEQUE) {
            var only = type == ReceiptType.PIX ? PaymentMethod.PIX : PaymentMethod.CHEQUE;
            if (method == null) {
                method = only;
            } else if (method != only) {
                issues.add(error("paymentMethod", type == ReceiptType.PIX ? "A Pix receipt is paid by Pix"
                        : "A cheque is paid by cheque"));
            }
        } else if (type == ReceiptType.CARD && (method == null || !method.isCard())) {
            issues.add(error("paymentMethod", "A card receipt is paid by credit or debit"));
        }
        if (method == PaymentMethod.CHEQUE && !acceptsCheques && type != ReceiptType.CHEQUE) {
            issues.add(error("paymentMethod", NO_CHEQUES));
        }
        if (installments != null && installments > 1 && method != PaymentMethod.CREDIT) {
            issues.add(error("installments", "Only credit can be paid in installments"));
        }
        if (issues.subList(before, issues.size()).stream().anyMatch(ValidationIssue::isError)) {
            return Optional.empty();
        }
        return Optional.of(new Payment(time, method, installments == null ? 1 : installments, brand));
    }

    // Typed by a person on review; otherwise the shop's CNPJ tells: if the shop received the money, it is a sale.
    // A cheque names who pays instead, so there it is the other way round.
    // A CPF or a missing tax ID says nothing, so a person has to choose.
    private Direction parseDirection(ReceiptFields fields, ReceiptType type, List<ValidationIssue> issues) {
        if (fields.direction() != null && !fields.direction().isBlank()) {
            return parse("direction", fields.direction(), raw -> parseEnum(Direction.class, raw, "Direction"), issues);
        }
        var counterpartyCnpj = cnpjOrEmpty(fields.taxId());
        if (shop.cnpj().isPresent() && counterpartyCnpj.isPresent()) {
            boolean shopIsNamed = counterpartyCnpj.equals(shop.cnpj());
            boolean shopReceived = type == ReceiptType.CHEQUE ? !shopIsNamed : shopIsNamed;
            return shopReceived ? Direction.SALE : Direction.EXPENSE;
        }
        issues.add(error("direction", "Choose whether the shop received or paid this money"));
        return null;
    }

    private static <T> T parseIfPresent(String field, String raw, Function<String, T> parser,
                                        List<ValidationIssue> issues) {
        return raw == null || raw.isBlank() ? null : parse(field, raw, parser, issues);
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

    // HH:mm or HH:mm:ss, as printed
    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException _) {
            throw new InvalidValueException("Time must be HH:mm, got: " + raw);
        }
    }

    // "3", "3x" or "03"
    private static int parseInstallments(String raw) {
        String digits = raw.toLowerCase().replace("x", "").strip();
        int installments;
        try {
            installments = Integer.parseInt(digits);
        } catch (NumberFormatException _) {
            throw new InvalidValueException("Installments must be a number, got: " + raw);
        }
        if (installments < 1 || installments > Payment.MAX_INSTALLMENTS) {
            throw new InvalidValueException("Installments must be between 1 and " + Payment.MAX_INSTALLMENTS);
        }
        return installments;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String label) {
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new InvalidValueException(label + " not recognized: " + raw);
        }
    }

    // Like the category, an unknown brand is not worth blocking a receipt for
    private static CardBrand parseCardBrand(String raw, List<ValidationIssue> issues) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CardBrand.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException _) {
            issues.add(warning("cardBrand", "Card brand not recognized, using OTHER: " + raw));
            return CardBrand.OTHER;
        }
    }

    private static Optional<Cnpj> cnpjOrEmpty(String raw) {
        try {
            return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(new Cnpj(raw.strip()));
        } catch (InvalidValueException _) {
            return Optional.empty();
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
