package dev.mauricioandrade.kakeizu.infrastructure.config;

import dev.mauricioandrade.kakeizu.domain.model.Cnpj;
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
    ExtractionMapper extractionMapper(ReceiptValidator validator,
                                      @ConfigProperty(name = "kakeizu.shop.cnpj") Optional<String> shopCnpj) {
        return new ExtractionMapper(validator, shopCnpj.filter(s -> !s.isBlank()).map(Cnpj::new));
    }

    @Produces
    @Singleton
    SalesReportCalculator salesReportCalculator() {
        return new SalesReportCalculator();
    }
}
