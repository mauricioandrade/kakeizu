package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.BatchItem;
import dev.mauricioandrade.kakeizu.domain.model.UploadedImage;
import java.util.List;

public interface BatchExtractReceiptUseCase {

    int MAX_IMAGES = 5;

    /**
     * One result per image, in the same order.
     */
    List<BatchItem> extractAll(List<UploadedImage> images);
}
