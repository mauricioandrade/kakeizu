package dev.mauricioandrade.kakeizu.infrastructure.config;

import dev.mauricioandrade.kakeizu.domain.service.ExtractionMapper;
import dev.mauricioandrade.kakeizu.domain.service.ReceiptValidator;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/**
 * The domain has no framework annotations, so its services are wired here.
 */
public class DomainServicesProducer {

    @Produces
    @Singleton
    ReceiptValidator receiptValidator(Clock clock) {
        return new ReceiptValidator(clock);
    }

    @Produces
    @Singleton
    ExtractionMapper extractionMapper(ReceiptValidator validator) {
        return new ExtractionMapper(validator);
    }
}
