package dev.mauricioandrade.kakeizu.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Ready only when Ollama answers and the configured model is pulled: without it no receipt can be read.
 * Lists the local models instead of running one, so polling it costs no GPU time.
 */
@Readiness
@ApplicationScoped
public class OllamaHealthCheck implements HealthCheck {

    private static final String NAME = "Ollama";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI tagsUri;
    private final String model;

    public OllamaHealthCheck(@ConfigProperty(name = "quarkus.langchain4j.ollama.base-url") String baseUrl,
                             @ConfigProperty(name = "kakeizu.model") String model) {
        this.tagsUri = URI.create(baseUrl.replaceAll("/+$", "") + "/api/tags");
        this.model = model;
    }

    @Override
    public HealthCheckResponse call() {
        var response = HealthCheckResponse.named(NAME).withData("model", model);
        try {
            var answer = client.send(HttpRequest.newBuilder(tagsUri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() != 200) {
                return response.down().withData("error", "Ollama answered " + answer.statusCode()).build();
            }
            return isPulled(JSON.readTree(answer.body()))
                    ? response.up().build()
                    : response.down().withData("error", "Model not pulled: run ollama pull " + model).build();
        } catch (IOException _) {
            return response.down().withData("error", "Ollama is not reachable").build();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return response.down().withData("error", "Interrupted").build();
        }
    }

    // Ollama lists "gemma4:e4b", and a model named without a tag as "name:latest"
    private boolean isPulled(JsonNode tags) {
        for (JsonNode pulled : tags.path("models")) {
            String name = pulled.path("name").asText();
            if (name.equals(model) || name.equals(model + ":latest")) {
                return true;
            }
        }
        return false;
    }
}
