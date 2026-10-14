package dev.mauricioandrade.kakeizu.domain.model;

/**
 * A photo just uploaded, already checked to be JPG or PNG.
 */
public record UploadedImage(String fileName, byte[] content, String contentType) {
}
