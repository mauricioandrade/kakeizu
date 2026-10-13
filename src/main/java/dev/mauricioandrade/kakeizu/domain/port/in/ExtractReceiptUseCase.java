package dev.mauricioandrade.kakeizu.domain.port.in;

import dev.mauricioandrade.kakeizu.domain.model.UploadResult;

public interface ExtractReceiptUseCase {

    UploadResult extract(byte[] image, String contentType);
}
