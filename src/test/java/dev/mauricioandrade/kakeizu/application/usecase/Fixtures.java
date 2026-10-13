package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.ModelReading;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptFields;
import dev.mauricioandrade.kakeizu.domain.model.Shop;
import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import dev.mauricioandrade.kakeizu.domain.service.ReceiptValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

final class Fixtures {

    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final Clock NOW = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), SAO_PAULO);
    // The shop is Melhor Grão Stone: its card slips are sales, the Pix to Pagsmile and the NFC-e are expenses
    static final ExtractionMapper MAPPER = new ExtractionMapper(new ReceiptValidator(NOW),
            new Shop(Optional.of(new Cnpj("69.034.668/0001-56")), false));

    // gemma3:4b output for samples/pix-inter.png: the transaction ID has one extra character
    static final ReceiptFields PIX_AS_READ = new ReceiptFields("PIX", "80.00", "2022-08-12", "Pagsmile",
            "23.010.551/0001-31", "E00416968202208120422Mzmiz9rPYPLT", "OTHER", null, null, null, null, null);
    static final ReceiptFields PIX_CORRECTED = new ReceiptFields("PIX", "80.00", "2022-08-12", "Pagsmile",
            "23.010.551/0001-31", "E00416968202208120422Mmzi9rPYPLT", "SERVICES", null, null, null, null, null);
    static final ReceiptFields NFCE = new ReceiptFields("NFCE", "146,46", "2022-08-12", "Loja Exemplo",
            "12.ABC.345/01DE-35", "35220812ABC34501DE35651010000050961123456784", "GOODS", null, null, null, null, null);
    static final ReceiptFields CARD = new ReceiptFields("CARD", "500,00", "2022-08-12", "Melhor Grão Stone",
            "69.034.668/0001-56", "2UP8IQ", "FUEL_SUPPLY", "11:23", "CREDIT", "1", "MASTERCARD", null);

    static ModelReading reading(ReceiptFields fields) {
        return new ModelReading(fields, "{\"model\":\"output\"}");
    }

    private Fixtures() {
    }
}
