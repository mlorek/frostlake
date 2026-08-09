/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The Ollama transport: one POST for a completion, one for an embedding. Streaming is turned off so
 * a response is a single JSON object rather than a chunked stream, which is what a scalar SQL
 * function wants.
 *
 * <p>A server that is not running, not reachable, or missing the requested model surfaces as a plain
 * engine error naming what to do about it — the pack loads regardless, so a classpath carrying it
 * costs nothing until one of its functions is actually called.
 */
public final class OllamaClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private OllamaClient() {
    }

    /** A single-turn completion: the model's reply text, with no surrounding JSON. */
    public static String generate(final String model, final String prompt, final Double temperature,
                                  final Integer maxTokens) {
        final ObjectNode request = JSON.createObjectNode();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("stream", false);
        final ObjectNode options = JSON.createObjectNode();
        if (temperature != null) {
            options.put("temperature", temperature.doubleValue());
        }
        if (maxTokens != null) {
            options.put("num_predict", maxTokens.intValue());
        }
        if (!options.isEmpty()) {
            request.set("options", options);
        }
        final JsonNode response = post("/api/generate", request);
        final JsonNode text = response.get("response");
        return text == null ? "" : text.asString();
    }

    /** The embedding of one text, as a list of doubles. */
    public static List<Double> embed(final String model, final String text) {
        final ObjectNode request = JSON.createObjectNode();
        request.put("model", model);
        request.put("input", text);
        final JsonNode response = post("/api/embed", request);
        final JsonNode embeddings = response.get("embeddings");
        final List<Double> vector = new ArrayList<>();
        if (embeddings != null && embeddings.isArray() && !embeddings.isEmpty()) {
            final JsonNode first = embeddings.get(0);
            for (int i = 0; i < first.size(); i++) {
                vector.add(Double.valueOf(first.get(i).asDouble()));
            }
        }
        return vector;
    }

    /** Whether a server is answering — used by tests to skip rather than fail when none is running. */
    public static boolean isAvailable() {
        try {
            final HttpResponse<String> response = client().send(
                HttpRequest.newBuilder(URI.create(OllamaConfig.url() + "/api/tags"))
                    .timeout(Duration.ofMillis(Math.min(2000, OllamaConfig.timeoutMs())))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (final IOException | InterruptedException | RuntimeException unreachable) {
            return false;
        }
    }

    private static JsonNode post(final String path, final ObjectNode body) {
        final String url = OllamaConfig.url() + path;
        final HttpResponse<String> response;
        try {
            response = client().send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(OllamaConfig.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Cortex request to " + url + " was interrupted.");
        } catch (final IOException unreachable) {
            throw new RuntimeException("Cannot reach the model server at " + OllamaConfig.url()
                + " — start Ollama, or point " + OllamaConfig.URL
                + " in frostlake.properties somewhere else. (" + unreachable.getMessage() + ")");
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("The model server at " + url + " answered "
                + response.statusCode() + ": " + response.body());
        }
        return JSON.readTree(response.body());
    }

    /** Built per call rather than held: a client is cheap, and a held one outlives a config change. */
    private static HttpClient client() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.min(5000, OllamaConfig.timeoutMs())))
            .build();
    }

    /** A JSON array node of the given strings, for the functions that answer with one. */
    static ArrayNode arrayOf(final List<String> values) {
        final ArrayNode array = JSON.createArrayNode();
        for (final String value : values) {
            array.add(value);
        }
        return array;
    }

    static ObjectMapper json() {
        return JSON;
    }
}
