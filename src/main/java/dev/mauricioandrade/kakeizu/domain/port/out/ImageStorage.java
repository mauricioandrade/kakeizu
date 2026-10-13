package dev.mauricioandrade.kakeizu.domain.port.out;

public interface ImageStorage {

    /**
     * Stores the image under its SHA-256. Storing the same hash again is a no-op.
     */
    void store(String sha256, byte[] image);

    byte[] load(String sha256);
}
