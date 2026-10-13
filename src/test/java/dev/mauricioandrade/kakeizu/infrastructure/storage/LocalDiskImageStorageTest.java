package dev.mauricioandrade.kakeizu.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDiskImageStorageTest {

    static final String SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsByHashInAnOwnerOnlyDirectory() throws Exception {
        var directory = tempDir.resolve("images");
        var storage = new LocalDiskImageStorage(directory);

        storage.store(SHA, "abc".getBytes());

        assertArrayEquals("abc".getBytes(), storage.load(SHA));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count()); // no temporary file left behind
        }
    }

    @Test
    void storingTheSameHashAgainKeepsTheFirstImage() {
        var storage = new LocalDiskImageStorage(tempDir);

        storage.store(SHA, "abc".getBytes());
        storage.store(SHA, "other".getBytes());

        assertArrayEquals("abc".getBytes(), storage.load(SHA));
    }

    @Test
    void rejectsAnythingThatIsNotAHash() {
        var storage = new LocalDiskImageStorage(tempDir);

        assertThrows(IllegalArgumentException.class, () -> storage.load("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.store(SHA.toUpperCase(), new byte[0]));
    }
}
