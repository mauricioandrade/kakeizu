package dev.mauricioandrade.kakeizu.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OllamaHealthCheckTest {

    private HttpServer fakeOllama;

    @AfterEach
    void stopFakeOllama() {
        if (fakeOllama != null) {
            fakeOllama.stop(0);
        }
    }

    @Test
    void upWhenTheModelIsPulled() throws IOException {
        answer(200, "{\"models\":[{\"name\":\"gemma3:4b\"},{\"name\":\"gemma4:e4b\"}]}");

        assertEquals(Status.UP, check("gemma4:e4b").getStatus());
    }

    @Test
    void aModelWithoutTagMatchesLatest() throws IOException {
        answer(200, "{\"models\":[{\"name\":\"llava:latest\"}]}");

        assertEquals(Status.UP, check("llava").getStatus());
    }

    @Test
    void downWhenTheModelIsMissing() throws IOException {
        answer(200, "{\"models\":[{\"name\":\"gemma3:4b\"}]}");

        var response = check("gemma4:e4b");

        assertEquals(Status.DOWN, response.getStatus());
        assertEquals("Model not pulled: run ollama pull gemma4:e4b", error(response));
    }

    @Test
    void downWhenOllamaFails() throws IOException {
        answer(500, "boom");

        assertEquals("Ollama answered 500", error(check("gemma4:e4b")));
    }

    @Test
    void downWhenOllamaIsNotRunning() throws IOException {
        answer(200, "{}");
        String url = baseUrl();
        fakeOllama.stop(0);
        fakeOllama = null;

        var response = new OllamaHealthCheck(url, "gemma4:e4b").call();

        assertEquals(Status.DOWN, response.getStatus());
        assertEquals("Ollama is not reachable", error(response));
    }

    private void answer(int status, String body) throws IOException {
        fakeOllama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeOllama.createContext("/api/tags", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeOllama.start();
    }

    private HealthCheckResponse check(String model) {
        return new OllamaHealthCheck(baseUrl() + "/", model).call();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + fakeOllama.getAddress().getPort();
    }

    private static String error(HealthCheckResponse response) {
        return response.getData().flatMap(data -> Optional.ofNullable((String) data.get("error"))).orElse(null);
    }
}
