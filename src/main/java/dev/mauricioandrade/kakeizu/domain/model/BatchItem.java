package dev.mauricioandrade.kakeizu.domain.model;

/**
 * What happened to one photo of a batch: read (or recognized as already uploaded), or failed on its own
 * without stopping the others.
 */
public sealed interface BatchItem {

    String fileName();

    record Done(String fileName, UploadResult result) implements BatchItem {
    }

    record Failed(String fileName, String reason) implements BatchItem {
    }
}
