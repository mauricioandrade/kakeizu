package dev.mauricioandrade.kakeizu.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void recognizesPngByItsSignature() throws IOException {
        var sample = Files.readAllBytes(Path.of("src/test/resources/samples/pix-inter.png"));

        assertEquals(Optional.of(ImageType.PNG), ImageType.detect(sample));
    }

    @Test
    void recognizesJpegByItsSignature() {
        assertEquals(Optional.of(ImageType.JPEG), ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x0E}));
    }

    @Test
    void anythingElseIsNotAnImage() {
        assertTrue(ImageType.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(ImageType.detect(new byte[]{(byte) 0x89, 'P'}).isEmpty());
        assertTrue(ImageType.detect(new byte[0]).isEmpty());
    }
}
