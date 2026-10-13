package dev.mauricioandrade.kakeizu.infrastructure.ai;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Ollama unloads an idle model after a few minutes, and loading it back costs more than the reading itself.
 * A request without a prompt only loads the model, so we send one when the app starts and whenever someone
 * opens the upload page: the model loads while they take the photo.
 */
@ApplicationScoped
public class ModelWarmUp {

    private static final Logger LOG = Logger.getLogger(ModelWarmUp.class);
    private static final long MIN_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AtomicLong lastWarmUp = new AtomicLong();
    private final boolean enabled;
    private final URI generateUri;
    private final String model;

    public ModelWarmUp(@ConfigProperty(name = "kakeizu.ai.warm-up") boolean enabled,
                       @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url") String baseUrl,
                       @ConfigProperty(name = "kakeizu.model") String model) {
        this.enabled = enabled;
        this.generateUri = URI.create(baseUrl.replaceAll("/+$", "") + "/api/generate");
        this.model = model;
    }

    @Startup
    void onStart() {
        warmUp();
    }

    /**
     * Fire and forget: never blocks the caller, and does nothing if it ran less than a minute ago.
     */
    public void warmUp() {
        long now = System.currentTimeMillis();
        long last = lastWarmUp.get();
        if (!enabled || now - last < MIN_INTERVAL_MILLIS || !lastWarmUp.compareAndSet(last, now)) {
            return;
        }
        var request = HttpRequest.newBuilder(generateUri)
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + model + "\"}"))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        LOG.debugf("Model warm-up failed: %s", error.toString());
                    } else {
                        LOG.debugf("Model warm-up answered %d", response.statusCode());
                    }
                });
    }
}
