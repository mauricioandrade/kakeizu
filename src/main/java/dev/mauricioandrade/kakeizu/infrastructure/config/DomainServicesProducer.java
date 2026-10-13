package dev.mauricioandrade.kakeizu.infrastructure.config;

import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
import dev.mauricioandrade.kakeizu.domain.model.Shop;
import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import dev.mauricioandrade.kakeizu.domain.service.ReceiptValidator;
import dev.mauricioandrade.kakeizu.domain.service.SalesReportCalculator;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The domain has no framework annotations, so its services are wired here.
 */
public class DomainServicesProducer {

    @Produces
    @Singleton
    ReceiptValidator receiptValidator(Clock clock) {
        return new ReceiptValidator(clock);
    }

    // An invalid CNPJ in the configuration fails at startup, not on the first receipt
    @Produces
    @Singleton
    Shop shop(@ConfigProperty(name = "kakeizu.shop.cnpj") Optional<String> cnpj,
              @ConfigProperty(name = "kakeizu.shop.accepts-cheques") boolean acceptsCheques) {
        return new Shop(cnpj.filter(s -> !s.isBlank()).map(Cnpj::new), acceptsCheques);
    }

    @Produces
    @Singleton
    ExtractionMapper extractionMapper(ReceiptValidator validator, Shop shop) {
        return new ExtractionMapper(validator, shop);
    }

    @Produces
    @Singleton
    SalesReportCalculator salesReportCalculator() {
        return new SalesReportCalculator();
    }
}
