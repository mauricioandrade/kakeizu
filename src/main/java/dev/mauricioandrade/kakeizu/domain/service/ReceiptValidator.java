package dev.mauricioandrade.kakeizu.domain.service;

import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.error;
import static dev.mauricioandrade.kakeizu.domain.model.ValidationIssue.warning;

import dev.mauricioandrade.kakeizu.domain.model.CardReceipt;
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

    private final Clock clock;

    public ReceiptValidator(Clock clock) {
        this.clock = clock;
    }

    public List<ValidationIssue> validate(Receipt receipt) {
        var issues = new ArrayList<ValidationIssue>();
        if (receipt.issuedOn().isAfter(LocalDate.now(clock))) {
            issues.add(error("date", "Date is in the future: " + receipt.issuedOn()));
        }
        issues.addAll(switch (receipt) {
            case FiscalInvoice(_, var issuedOn, _, var issuerCnpj, var accessKey) ->
                    accessKeyIssues(accessKey, NfeAccessKey.NFE_MODEL, issuedOn, issuerCnpj);
            case FiscalCoupon(_, var issuedOn, _, var issuerCnpj, var accessKey) ->
                    accessKeyIssues(accessKey, NfeAccessKey.NFCE_MODEL, issuedOn, issuerCnpj);
            case PixReceipt _, CardReceipt _ -> List.of();
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
