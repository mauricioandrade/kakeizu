package dev.mauricioandrade.kakeizu;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApplicationStartupTest {

    private final Flyway flyway;

    ApplicationStartupTest(Flyway flyway) {
        this.flyway = flyway;
    }

    @Test
    void bootsWithDatabaseAndFlyway() {
        assertNotNull(flyway.info());
    }
}
