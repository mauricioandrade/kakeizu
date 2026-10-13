package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.port.in.ListReceiptsUseCase;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class HomeResource {

    // Type-safe template: templates/HomeResource/home.html, checked at build time
    record home(LocalDate today, List<ReceiptRecord> receipts) implements TemplateInstance {
    }

    private final ListReceiptsUseCase listReceipts;
    private final Clock clock;

    public HomeResource(ListReceiptsUseCase listReceipts, Clock clock) {
        this.listReceipts = listReceipts;
        this.clock = clock;
    }

    @GET
    public TemplateInstance home() {
        var today = LocalDate.now(clock);
        return new home(today, listReceipts.uploadedOn(today));
    }
}
