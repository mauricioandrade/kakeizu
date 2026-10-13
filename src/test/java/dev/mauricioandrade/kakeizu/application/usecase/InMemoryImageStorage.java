package dev.mauricioandrade.kakeizu.application.usecase;

import dev.mauricioandrade.kakeizu.domain.port.out.ImageStorage;
import java.util.HashMap;
import java.util.Map;

class InMemoryImageStorage implements ImageStorage {

    final Map<String, byte[]> images = new HashMap<>();

    @Override
    public void store(String sha256, byte[] image) {
        images.putIfAbsent(sha256, image);
    }

    @Override
    public byte[] load(String sha256) {
        return images.get(sha256);
    }
}
