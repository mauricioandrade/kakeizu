package dev.mauricioandrade.kakeizu.infrastructure.web;

import dev.mauricioandrade.kakeizu.application.logging.Correlation;
import dev.mauricioandrade.kakeizu.domain.model.BatchItem;
import dev.mauricioandrade.kakeizu.domain.model.CardBrand;
import dev.mauricioandrade.kakeizu.domain.model.Category;
import dev.mauricioandrade.kakeizu.domain.model.ConfirmResult;
import dev.mauricioandrade.kakeizu.domain.model.Direction;
import dev.mauricioandrade.kakeizu.domain.model.ExtractionResult;
import dev.mauricioandrade.kakeizu.domain.model.PaymentMethod;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptRecord;
import dev.mauricioandrade.kakeizu.domain.model.ReceiptType;
import dev.mauricioandrade.kakeizu.domain.model.Shop;
import dev.mauricioandrade.kakeizu.domain.model.UploadResult;
import dev.mauricioandrade.kakeizu.domain.model.UploadedImage;
import dev.mauricioandrade.kakeizu.domain.port.in.BatchExtractReceiptUseCase;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/receipts")
public class ReceiptResource {

    // Anything far bigger than a phone photo is not a receipt
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final List<Category> CATEGORIES = Arrays.asList(Category.values());
    private static final List<CardBrand> BRANDS = Arrays.asList(CardBrand.values());
    private static final List<Direction> DIRECTIONS = Arrays.asList(Direction.values());

    record review(ReceiptRecord receipt, ExtractionResult result, boolean duplicate, List<ReceiptType> types,
                  List<Category> categories, List<PaymentMethod> methods, List<CardBrand> brands,
                  List<Direction> directions) implements TemplateInstance {

        // Cheque is only offered to shops that take cheques
        review(ReceiptRecord receipt, ExtractionResult result, boolean duplicate, Shop shop) {
            this(receipt, result, duplicate,
                    Arrays.stream(ReceiptType.values())
                            .filter(t -> shop.acceptsCheques() || t != ReceiptType.CHEQUE).toList(),
                    CATEGORIES,
                    Arrays.stream(PaymentMethod.values())
                            .filter(m -> shop.acceptsCheques() || m != PaymentMethod.CHEQUE).toList(),
                    BRANDS, DIRECTIONS);
        }
    }

    record batch(List<BatchRow> rows) implements TemplateInstance {
    }

    // One line of the batch page: a link to the review, or why the photo was not read
    record BatchRow(String fileName, UUID receiptId, boolean duplicate, String error) {

        static BatchRow of(BatchItem item) {
            return switch (item) {
                case BatchItem.Done(var name, UploadResult.Extracted(var receipt)) ->
                        new BatchRow(name, receipt.id(), false, null);
                case BatchItem.Done(var name, UploadResult.AlreadyUploaded(var receipt)) ->
                        new BatchRow(name, receipt.id(), true, null);
                case BatchItem.Failed(var name, var reason) -> new BatchRow(name, null, false, reason);
            };
        }
    }

    private final ExtractReceiptUseCase extractReceipt;
    private final BatchExtractReceiptUseCase batchExtractReceipt;
    private final ReviewReceiptUseCase reviewReceipt;
    private final ConfirmReceiptUseCase confirmReceipt;
    private final RejectReceiptUseCase rejectReceipt;
    private final ReceiptImageUseCase receiptImage;
    private final Shop shop;

    public ReceiptResource(ExtractReceiptUseCase extractReceipt, BatchExtractReceiptUseCase batchExtractReceipt,
                           ReviewReceiptUseCase reviewReceipt,
                           ConfirmReceiptUseCase confirmReceipt, RejectReceiptUseCase rejectReceipt,
                           ReceiptImageUseCase receiptImage, Shop shop) {
        this.extractReceipt = extractReceipt;
        this.batchExtractReceipt = batchExtractReceipt;
        this.reviewReceipt = reviewReceipt;
        this.confirmReceipt = confirmReceipt;
        this.rejectReceipt = rejectReceipt;
        this.receiptImage = receiptImage;
        this.shop = shop;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@RestForm("image") FileUpload upload) throws IOException {
        if (upload == null) {
            throw new InvalidInputException("Choose a photo of the receipt.");
        }
        var image = toImage(upload);
        var result = Correlation.with(Correlation.newId(), () -> extractReceipt.extract(image.content(), image.contentType()));

        URI next = switch (result) {
            case UploadResult.Extracted(var receipt) -> reviewUri(receipt.id(), false);
            case UploadResult.AlreadyUploaded(var receipt) -> reviewUri(receipt.id(), true);
        };
        return Response.seeOther(next).build();
    }

    @POST
    @Path("batch")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance uploadBatch(@RestForm("images") List<FileUpload> uploads) throws IOException {
        if (uploads == null || uploads.isEmpty()) {
            throw new InvalidInputException("Choose the photos of the receipts.");
        }
        if (uploads.size() > BatchExtractReceiptUseCase.MAX_IMAGES) {
            throw new InvalidInputException("Up to " + BatchExtractReceiptUseCase.MAX_IMAGES + " photos at a time.");
        }
        var images = new ArrayList<UploadedImage>();
        for (var upload : uploads) {
            images.add(toImage(upload));
        }
        return new batch(batchExtractReceipt.extractAll(images).stream().map(BatchRow::of).toList());
    }

    // The type comes from the magic bytes, never from the Content-Type the browser sent
    private static UploadedImage toImage(FileUpload upload) throws IOException {
        if (upload.size() > MAX_IMAGE_BYTES) {
            throw new InvalidInputException("A photo can have up to 10 MB.");
        }
        byte[] content = Files.readAllBytes(upload.uploadedFile());
        var imageType = ImageType.detect(content)
                .orElseThrow(() -> new InvalidInputException("Only JPG and PNG images are accepted."));
        return new UploadedImage(upload.fileName(), content, imageType.mediaType());
    }

    @GET
    @Path("{id}/review")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance review(@RestPath UUID id, @RestQuery boolean duplicate) {
        var review = reviewReceipt.review(id);
        return new review(review.receipt(), review.result(), duplicate, shop);
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
                    .entity(new review(reviewReceipt.review(id).receipt(), result, false, shop))
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
