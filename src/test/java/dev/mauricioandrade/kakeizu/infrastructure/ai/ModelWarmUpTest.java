package dev.mauricioandrade.kakeizu.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelWarmUpTest {

    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final CountDownLatch firstRequest = new CountDownLatch(1);
    private HttpServer fakeOllama;

    @BeforeEach
    void startFakeOllama() throws IOException {
        fakeOllama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeOllama.createContext("/api/generate", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            firstRequest.countDown();
        });
        fakeOllama.start();
    }

    @AfterEach
    void stopFakeOllama() {
        fakeOllama.stop(0);
    }

    @Test
    void loadsTheModelWithoutAPromptAndAtMostOncePerMinute() throws InterruptedException {
        var warmUp = new ModelWarmUp(true, baseUrl() + "/", "gemma4:e4b");

        warmUp.warmUp();
        warmUp.warmUp();

        assertTrue(firstRequest.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals(1, requests.get());
        assertEquals("{\"model\":\"gemma4:e4b\"}", lastBody.get());
    }

    @Test
    void doesNothingWhenDisabled() throws InterruptedException {
        new ModelWarmUp(false, baseUrl(), "gemma4:e4b").warmUp();

        Thread.sleep(300);
        assertEquals(0, requests.get());
    }

    @Test
    void ollamaBeingDownIsNotTheCallersProblem() {
        fakeOllama.stop(0);

        new ModelWarmUp(true, baseUrl(), "gemma4:e4b").warmUp();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + fakeOllama.getAddress().getPort();
    }
}
