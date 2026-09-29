package dev.rosewood.rosechat.moderation.ai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class OpenAiModerationClient {
    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/moderations");

    private final HttpClient http;
    private final Gson gson;
    private final AiModerationConfig config;
    private final String apiKey;

    public OpenAiModerationClient(HttpClient http, Gson gson, AiModerationConfig config, String apiKey) {
        this.http = Objects.requireNonNull(http, "http");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.config = Objects.requireNonNull(config, "config");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey").trim();
        if (this.apiKey.isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key must not be blank");
        }
    }

    public CompletableFuture<BatchResult> moderate(String targetMessage, String contextTranscript) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.add("input", gson.toJsonTree(List.of(targetMessage, contextTranscript)));
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(config.requestTimeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    BatchResult parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ModerationRequestException("OpenAI moderation returned HTTP " + response.statusCode());
        }
        return parseBody(response.body());
    }

    BatchResult parseBody(String body) {
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root == null || !root.has("results") || !root.get("results").isJsonArray()) {
                throw new ModerationRequestException("OpenAI moderation response is missing results");
            }
            List<ModerationScores> results = new ArrayList<>();
            root.getAsJsonArray("results").forEach(element -> results.add(parseResult(element.getAsJsonObject())));
            if (results.size() != 2) {
                throw new ModerationRequestException("OpenAI moderation response returned " + results.size() + " results; expected 2");
            }
            return new BatchResult(results.get(0), results.get(1));
        } catch (ModerationRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModerationRequestException("OpenAI moderation response could not be parsed", exception);
        }
    }

    private static ModerationScores parseResult(JsonObject result) {
        if (!result.has("flagged") || !result.has("categories") || !result.has("category_scores")) {
            throw new ModerationRequestException("OpenAI moderation result is incomplete");
        }
        Map<String, Boolean> categories = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : result.getAsJsonObject("categories").entrySet()) {
            if (!entry.getValue().isJsonNull()) {
                categories.put(entry.getKey(), entry.getValue().getAsBoolean());
            }
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : result.getAsJsonObject("category_scores").entrySet()) {
            if (!entry.getValue().isJsonNull()) {
                scores.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }
        return new ModerationScores(result.get("flagged").getAsBoolean(), categories, scores);
    }

    public record BatchResult(ModerationScores target, ModerationScores context) {
        public BatchResult {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(context, "context");
        }
    }

    public static final class ModerationRequestException extends RuntimeException {
        public ModerationRequestException(String message) {
            super(message);
        }

        public ModerationRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
