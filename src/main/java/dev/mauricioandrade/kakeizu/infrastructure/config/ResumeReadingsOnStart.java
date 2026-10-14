package dev.mauricioandrade.kakeizu.infrastructure.config;

import dev.mauricioandrade.kakeizu.application.usecase.BackgroundReceiptReader;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Photos still marked as reading when the app stopped go back in line once the database is up.
 */
@ApplicationScoped
public class ResumeReadingsOnStart {

    private final BackgroundReceiptReader backgroundReader;

    public ResumeReadingsOnStart(BackgroundReceiptReader backgroundReader) {
        this.backgroundReader = backgroundReader;
    }

    void onStart(@Observes StartupEvent event) {
        backgroundReader.resumeAfterRestart();
    }
}
