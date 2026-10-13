package dev.mauricioandrade.kakeizu.infrastructure.storage;

import dev.mauricioandrade.kakeizu.domain.port.out.ImageStorage;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * One file per image, named by its SHA-256. The directory is readable by the owner only.
 */
@ApplicationScoped
public class LocalDiskImageStorage implements ImageStorage {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final Path directory;

    public LocalDiskImageStorage(@ConfigProperty(name = "kakeizu.storage.dir") Path directory) {
        this.directory = directory;
    }

    @Override
    public void store(String sha256, byte[] image) {
        Path target = pathOf(sha256);
        if (Files.exists(target)) {
            return;
        }
        try {
            createDirectory();
            // Write to a temporary file first so a crash never leaves a half-written image behind
            Path temporary = Files.createTempFile(directory, sha256, ".tmp");
            Files.write(temporary, image);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException _) {
            // Stored concurrently by another request: same hash, same content
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] load(String sha256) {
        try {
            return Files.readAllBytes(pathOf(sha256));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The hash comes from our own code, but checking it rules out path traversal for good
    private Path pathOf(String sha256) {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Not a SHA-256 hex string");
        }
        return directory.resolve(sha256);
    }

    private void createDirectory() throws IOException {
        if (Files.isDirectory(directory)) {
            return;
        }
        Files.createDirectories(directory);
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
