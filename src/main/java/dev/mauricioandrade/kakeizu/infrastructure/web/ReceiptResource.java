package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ConfirmResult;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.port.in.ConfirmReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.ExtractReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.ReceiptImageUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.RejectReceiptUseCase;
import dev.mauricioandrade.kakeizu.domain.port.in.ReviewReceiptUseCase;
import io.quarkus.qute.TemplateInstance;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/receipts")
public class ReceiptResource {

    private static final List<ReceiptType> TYPES = Arrays.asList(ReceiptType.values());
    private static final List<Category> CATEGORIES = Arrays.asList(Category.values());

    record review(ReceiptRecord receipt, ExtractionResult result, boolean duplicate, List<ReceiptType> types,
                  List<Category> categories) implements TemplateInstance {

        review(ReceiptRecord receipt, ExtractionResult result, boolean duplicate) {
            this(receipt, result, duplicate, TYPES, CATEGORIES);
        }
    }

    private final ExtractReceiptUseCase extractReceipt;
    private final ReviewReceiptUseCase reviewReceipt;
    private final ConfirmReceiptUseCase confirmReceipt;
    private final RejectReceiptUseCase rejectReceipt;
    private final ReceiptImageUseCase receiptImage;

    public ReceiptResource(ExtractReceiptUseCase extractReceipt, ReviewReceiptUseCase reviewReceipt,
                           ConfirmReceiptUseCase confirmReceipt, RejectReceiptUseCase rejectReceipt,
                           ReceiptImageUseCase receiptImage) {
        this.extractReceipt = extractReceipt;
        this.reviewReceipt = reviewReceipt;
        this.confirmReceipt = confirmReceipt;
        this.rejectReceipt = rejectReceipt;
        this.receiptImage = receiptImage;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@RestForm("image") FileUpload upload) throws IOException {
        if (upload == null) {
            throw new InvalidUploadException("Choose a photo of the receipt.");
        }
        byte[] content = Files.readAllBytes(upload.uploadedFile());
        var imageType = ImageType.detect(content)
                .orElseThrow(() -> new InvalidUploadException("Only JPG and PNG images are accepted."));

        URI next = switch (extractReceipt.extract(content, imageType.mediaType())) {
            case UploadResult.Extracted(var receipt) -> reviewUri(receipt.id(), false);
            case UploadResult.AlreadyUploaded(var receipt) -> reviewUri(receipt.id(), true);
        };
        return Response.seeOther(next).build();
    }

    @GET
    @Path("{id}/review")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance review(@RestPath UUID id, @RestQuery boolean duplicate) {
        var review = reviewReceipt.review(id);
        return new review(review.receipt(), review.result(), duplicate);
    }

    @GET
    @Path("{id}/image")
    public Response image(@RestPath UUID id) {
        var image = receiptImage.image(id);
        return Response.ok(image.content(), image.contentType())
                .header("Cache-Control", "private, max-age=3600")
                .build();
    }

    @POST
    @Path("{id}/confirm")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response confirm(@RestPath UUID id, @Valid @BeanParam ReviewForm form) {
        return switch (confirmReceipt.confirm(id, form.toFields())) {
            case ConfirmResult.Confirmed _ -> Response.seeOther(URI.create("/")).build();
            // Show the form again with what the person typed and what is still wrong
            case ConfirmResult.Invalid(var result) -> Response.status(422)
                    .entity(new review(reviewReceipt.review(id).receipt(), result, false))
                    .build();
        };
    }

    @POST
    @Path("{id}/reject")
    public Response reject(@RestPath UUID id) {
        rejectReceipt.reject(id);
        return Response.seeOther(URI.create("/")).build();
    }

    private static URI reviewUri(UUID id, boolean duplicate) {
        return URI.create("/receipts/" + id + "/review" + (duplicate ? "?duplicate=true" : ""));
    }
}
