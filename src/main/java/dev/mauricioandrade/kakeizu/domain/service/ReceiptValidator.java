package dev.mauricioandrade.kakeizu.domain.service;

import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.error;
import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.warning;

import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
import dev.mauricioandrade.kakeizu.domain.model.ChequeReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.FiscalCoupon;
import dev.mauricioandrade.kakeizu.domain.model.FiscalInvoice;
import dev.mauricioandrade.kakeizu.domain.model.NfeAccessKey;
import dev.mauricioandrade.kakeizu.domain.model.PixReceipt;
import dev.mauricioandrade.kakeizu.domain.model.Receipt;
import dev.mauricioandrade.kakeizu.domain.model.ValidationIssue;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks across fields of a receipt whose individual values are already valid.
 */
public class ReceiptValidator {

    static final int STALE_CHEQUE_MONTHS = 6;

    private final Clock clock;

    public ReceiptValidator(Clock clock) {
        this.clock = clock;
    }

    public List<ValidationIssue> validate(Receipt receipt) {
        var issues = new ArrayList<ValidationIssue>();
        if (receipt.issuedOn().isAfter(LocalDate.now(clock))) {
            // Post-dated cheques are common: the date is when it can be deposited
            issues.add(receipt instanceof ChequeReceipt
                    ? warning("date", "Post-dated cheque: it can only be deposited on " + receipt.issuedOn())
                    : error("date", "Date is in the future: " + receipt.issuedOn()));
        } else if (receipt instanceof ChequeReceipt && receipt.issuedOn().isBefore(LocalDate.now(clock)
                .minusMonths(STALE_CHEQUE_MONTHS))) {
            // A cheque must be cashed within 30 to 60 days and expires 6 months after that, so a date this old
            // is either a stale cheque or a misread year (the model reads a handwritten "26" as 2022)
            issues.add(warning("date", "Cheque dated more than " + STALE_CHEQUE_MONTHS
                    + " months ago: check the year, an old cheque may no longer be cashed"));
        }
        issues.addAll(switch (receipt) {
            case FiscalInvoice(_, var issuedOn, _, var issuerCnpj, var accessKey) ->
                    accessKeyIssues(accessKey, NfeAccessKey.NFE_MODEL, issuedOn, issuerCnpj);
            case FiscalCoupon(_, var issuedOn, _, var issuerCnpj, var accessKey) ->
                    accessKeyIssues(accessKey, NfeAccessKey.NFCE_MODEL, issuedOn, issuerCnpj);
            case PixReceipt _, CardReceipt _, ChequeReceipt _ -> List.of();
        });
        return issues;
    }

    // The key embeds the issue month and the issuer CNPJ: a mismatch means one of them was misread
    private static List<ValidationIssue> accessKeyIssues(NfeAccessKey key, String expectedModel, LocalDate issuedOn,
                                                         Cnpj issuerCnpj) {
        var issues = new ArrayList<ValidationIssue>();
        if (!key.model().equals(expectedModel)) {
            issues.add(warning("uniqueId", "Access key is from model " + key.model() + ", expected " + expectedModel));
        }
        if (!key.issuedIn().equals(YearMonth.from(issuedOn))) {
            issues.add(warning("date", "Date does not match the access key month (" + key.issuedIn() + ")"));
        }
        if (!key.issuerCnpj().equals(issuerCnpj.value())) {
            issues.add(warning("taxId", "CNPJ does not match the one in the access key (" + key.issuerCnpj() + ")"));
        }
        return issues;
    }
}
