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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sessions idle out as Snowflake's do: after four hours without activity unless {@code http.session.idleTimeoutMinutes}
 * says otherwise (0 never), and a client's heartbeat on {@code POST /api/sessions/{id}/heartbeat} counts as activity.
 * An answer reports the session's own CLIENT_SESSION_KEEP_ALIVE, which the driver keeps the session alive by.
 */
public class SessionIdleTimeoutTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long FOUR_HOURS_MS = 4L * 60 * 60 * 1000;

    private final HttpClient client = HttpClient.newHttpClient();
    private DatabaseHttpServer server;
    private String baseUrl;

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static EngineConfig configWithTimeout(final String minutes) {
        final EngineConfig config = new EngineConfig();
        if (minutes != null) {
            config.setProperty(EngineConfig.PROP_HTTP_SESSION_IDLE_TIMEOUT_MINUTES, minutes);
        }
        return config;
    }

    private void startServer(final EngineConfig config) throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        server = new DatabaseHttpServer(config);
        server.start();
        baseUrl = "http://localhost:" + port;
    }

    private HttpResponse<String> post(final String path, final String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String newSession() throws Exception {
        final HttpResponse<String> created = post("/api/sessions", "");
        assertEquals(200, created.statusCode());
        return MAPPER.readTree(created.body()).get("sessionId").asText();
    }

    private JsonNode execute(final String sessionId, final String sql) throws Exception {
        final HttpResponse<String> answer = post("/api/execute",
            MAPPER.writeValueAsString(MAPPER.createObjectNode().put("sql", sql).put("sessionId", sessionId)));
        assertEquals(200, answer.statusCode());
        return MAPPER.readTree(answer.body());
    }

    /** The idle timeout an engine built on the configuration runs with. */
    private static long timeoutOf(final EngineConfig config) {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(config);
        try {
            return engine.getSessionIdleTimeoutMs();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void theDefaultIsSnowflakesFourHoursAndTheConfigSetsMinutes() {
        assertEquals(240, configWithTimeout(null).getSessionIdleTimeoutMinutes());
        assertEquals(FOUR_HOURS_MS, timeoutOf(configWithTimeout(null)));
        assertEquals(15L * 60 * 1000, timeoutOf(configWithTimeout("15")));
        assertEquals(0L, timeoutOf(configWithTimeout("0")), "0 keeps every session until it is released");
        assertEquals(240, configWithTimeout("-5").getSessionIdleTimeoutMinutes(), "a negative value keeps the default");
        assertEquals(240, configWithTimeout("soon").getSessionIdleTimeoutMinutes(), "so does an unreadable one");
    }

    @Test
    public void theIdleSweepRunsAQuarterOfTheTimeoutApartBetweenASecondAndFiveMinutes() {
        assertEquals(5L * 60 * 1000, SessionManager.sweepPeriodMs(FOUR_HOURS_MS));
        assertEquals(15L * 1000, SessionManager.sweepPeriodMs(60L * 1000));
        assertEquals(1000L, SessionManager.sweepPeriodMs(2000L));
    }

    @Test
    public void aServerRunsItsEngineOnItsOwnConfiguration() throws Exception {
        startServer(configWithTimeout("7"));
        assertEquals(7L * 60 * 1000, server.getEngine().getSessionIdleTimeoutMs());
    }

    @Test
    public void aHeartbeatCountsAsActivityAndALostSessionIsA404() throws Exception {
        startServer(configWithTimeout(null));
        final String kept = newSession();
        final String idle = newSession();
        Thread.sleep(120);
        final HttpResponse<String> beat = post("/api/sessions/" + kept + "/heartbeat", "");
        assertEquals(200, beat.statusCode());
        final JsonNode answer = MAPPER.readTree(beat.body());
        assertTrue(answer.get("success").asBoolean());
        assertEquals(kept, answer.get("sessionId").asText());

        assertEquals(1, server.getEngine().expireIdleSessions(100), "only the session that sent no heartbeat idled out");
        assertNotNull(server.getEngine().getSession(kept));
        assertNull(server.getEngine().getSession(idle));
        assertEquals(404, post("/api/sessions/" + idle + "/heartbeat", "").statusCode());
        assertEquals(405, client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions/" + kept + "/heartbeat")).GET().build(),
            HttpResponse.BodyHandlers.ofString()).statusCode(), "a heartbeat is a POST");
    }

    @Test
    public void anAnswerReportsTheSessionsOwnKeepAliveSettings() throws Exception {
        startServer(configWithTimeout(null));
        final String session = newSession();
        final JsonNode plain = execute(session, "SELECT 1");
        assertFalse(plain.has("clientSessionKeepAlive"), "a session that sets nothing reports nothing");
        assertFalse(plain.has("clientSessionKeepAliveHeartbeatFrequency"));

        execute(session, "ALTER SESSION SET CLIENT_SESSION_KEEP_ALIVE = TRUE");
        final JsonNode set = execute(session, "ALTER SESSION SET CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY = 1200");
        assertTrue(set.get("clientSessionKeepAlive").asBoolean());
        assertEquals(1200, set.get("clientSessionKeepAliveHeartbeatFrequency").asInt());

        final JsonNode unset = execute(session, "ALTER SESSION UNSET CLIENT_SESSION_KEEP_ALIVE");
        assertFalse(unset.has("clientSessionKeepAlive"));
        assertEquals(1200, unset.get("clientSessionKeepAliveHeartbeatFrequency").asInt());
    }
}
