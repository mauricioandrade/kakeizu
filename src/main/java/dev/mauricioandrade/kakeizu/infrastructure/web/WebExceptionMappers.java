package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.exception.DuplicateReceiptException;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptAlreadyReviewedException;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptNotFoundException;
import dev.mauricioandrade.kakeizu.domain.exception.ReceiptReadingException;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Turns known failures into an error page with a fixed message: exception details never reach the browser.
 */
public class WebExceptionMappers {

    private static final Logger LOG = Logger.getLogger(WebExceptionMappers.class);

    record error(int status, String message) implements TemplateInstance {
    }

    @ServerExceptionMapper(ReceiptNotFoundException.class)
    public Response notFound() {
        return page(404, "Receipt not found.");
    }

    @ServerExceptionMapper(ReceiptAlreadyReviewedException.class)
    public Response alreadyReviewed() {
        return page(409, "This receipt was already confirmed or rejected.");
    }

    @ServerExceptionMapper(DuplicateReceiptException.class)
    public Response duplicate() {
        return page(409, "This receipt is already registered.");
    }

    @ServerExceptionMapper
    public Response readingFailed(ReceiptReadingException e) {
        LOG.warnf("Receipt reading failed: %s", e.getCause() == null ? e.getMessage() : e.getCause().toString());
        return page(503, "The model could not read this receipt. Check that Ollama is running and try again.");
    }

    @ServerExceptionMapper
    public Response invalidUpload(InvalidUploadException e) {
        return page(400, e.getMessage());
    }

    private static Response page(int status, String message) {
        return Response.status(status).type(MediaType.TEXT_HTML_TYPE).entity(new error(status, message)).build();
    }
}
