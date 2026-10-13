package dev.mauricioandrade.kakeizu.infrastructure.config;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.ZoneId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class ClockProducer {

    @Produces
    @Singleton
    Clock clock(@ConfigProperty(name = "kakeizu.zone") ZoneId zone) {
        return Clock.system(zone);
    }
}
