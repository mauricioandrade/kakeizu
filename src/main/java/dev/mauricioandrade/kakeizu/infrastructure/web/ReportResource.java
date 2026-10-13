package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.model.SalesReport;
import dev.mauricioandrade.kakeizu.domain.port.in.SalesReportUseCase;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import java.time.LocalDate;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/report")
@Produces(MediaType.TEXT_HTML)
public class ReportResource {

    private static final int DEFAULT_DAYS = 30;

    record report(SalesReport report) implements TemplateInstance {
    }

    private final SalesReportUseCase salesReport;
    private final Clock clock;

    public ReportResource(SalesReportUseCase salesReport, Clock clock) {
        this.salesReport = salesReport;
        this.clock = clock;
    }

    @GET
    public TemplateInstance report(@RestQuery LocalDate from, @RestQuery LocalDate to) {
        var end = to != null ? to : LocalDate.now(clock);
        var start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (end.isBefore(start)) {
            throw new InvalidInputException("The end date is before the start date.");
        }
        return new report(salesReport.report(start, end));
    }
}
