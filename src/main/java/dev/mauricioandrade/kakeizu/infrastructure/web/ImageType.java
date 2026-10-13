package dev.mauricioandrade.kakeizu.infrastructure.web;

import java.util.Arrays;
import java.util.Optional;

/**
 * Detects the image type from the first bytes of the file. The Content-Type header is whatever the
 * client says it is, so it is never trusted.
 */
enum ImageType {

    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String mediaType;
    private final byte[] signature;

    ImageType(String mediaType, byte[] signature) {
        this.mediaType = mediaType;
        this.signature = signature;
    }

    String mediaType() {
        return mediaType;
    }

    static Optional<ImageType> detect(byte[] content) {
        return Arrays.stream(values())
                .filter(type -> content.length >= type.signature.length
                        && Arrays.equals(content, 0, type.signature.length, type.signature, 0, type.signature.length))
                .findFirst();
    }
}
