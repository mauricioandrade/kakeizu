package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.model.DailySummary;
import dev.mauricioandrade.kakeizu.domain.port.in.DailySummaryUseCase;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import java.time.LocalDate;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/summary")
@Produces(MediaType.TEXT_HTML)
public class SummaryResource {

    record summary(DailySummary summary) implements TemplateInstance {
    }

    private final DailySummaryUseCase dailySummary;
    private final Clock clock;

    public SummaryResource(DailySummaryUseCase dailySummary, Clock clock) {
        this.dailySummary = dailySummary;
        this.clock = clock;
    }

    @GET
    public TemplateInstance summary(@RestQuery LocalDate date) {
        return new summary(dailySummary.summarize(date != null ? date : LocalDate.now(clock)));
    }
}
