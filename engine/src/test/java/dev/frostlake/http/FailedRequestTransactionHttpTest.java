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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Over a real socket: a request of several statements that opens a transaction and then fails leaves that
 * transaction with ITS SESSION, as the account does — the session sees it through CURRENT_TRANSACTION() and its
 * own rows, COMMIT publishes it, and releasing the session rolls it back. The server used to lose it: the
 * session reported no transaction, and SHOW TRANSACTIONS listed it — one more per such request — for the
 * server's lifetime, under no session and past every release.
 */
public class FailedRequestTransactionHttpTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static DatabaseHttpServer server;
    private static String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /** One request's parsed answer, whatever it says; any statement count when {@code multi} is set. */
    private JsonNode send(final String sql, final String sessionId, final boolean multi) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        body.put("sessionId", sessionId);
        if (multi) {
            body.put("multiStatementCount", 0);
        }
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), sql);
        return MAPPER.readTree(response.body());
    }

    /** A statement that must succeed, answering its first cell as text. */
    private String cell(final String sql, final String sessionId) throws Exception {
        final JsonNode json = send(sql, sessionId, false);
        assertTrue(json.get("success").asBoolean(), sql + " -> " + json);
        return json.get("resultSets").get(0).get("rows").get(0).get(0).asText();
    }

    /** How many transactions SHOW TRANSACTIONS lists, read from {@code sessionId}. */
    private int listed(final String sessionId) throws Exception {
        final JsonNode json = send("SHOW TRANSACTIONS", sessionId, false);
        assertTrue(json.get("success").asBoolean(), json.toString());
        return json.get("resultSets").get(0).get("rows").size();
    }

    private String newSession() throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions"))
            .POST(HttpRequest.BodyPublishers.ofString("")).build(), HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body()).get("sessionId").asText();
    }

    private int release(final String sessionId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions/" + sessionId))
            .DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    /** Run the failing request in {@code sessionId}: BEGIN, an INSERT into {@code table}, then a failure. */
    private void failAfterBegin(final String table, final String sessionId) throws Exception {
        final JsonNode json = send("BEGIN; INSERT INTO " + table + " VALUES (1); INSERT INTO nowhere_xyz VALUES (2)",
            sessionId, true);
        assertFalse(json.get("success").asBoolean(), json.toString());
    }

    @Test
    public void theFailedRequestsTransactionStaysWithItsSessionUntilCommit() throws Exception {
        final String owner = newSession();
        final String watcher = newSession();
        cell("CREATE OR REPLACE DATABASE ftx_commit_db", owner);
        cell("CREATE TABLE ftx_commit_db.public.t (n INT)", owner);
        failAfterBegin("ftx_commit_db.public.t", owner);

        assertEquals("true", cell("SELECT CURRENT_TRANSACTION() IS NOT NULL", owner));
        assertEquals("1", cell("SELECT COUNT(*) FROM ftx_commit_db.public.t", owner));
        assertEquals("0", cell("SELECT COUNT(*) FROM ftx_commit_db.public.t", watcher));
        assertEquals(1, listed(watcher));

        cell("COMMIT", owner);
        assertEquals("1", cell("SELECT COUNT(*) FROM ftx_commit_db.public.t", watcher));
        assertEquals(0, listed(watcher), "COMMIT ended it; nothing is left behind");
        release(owner);
        release(watcher);
    }

    @Test
    public void releasingTheSessionRollsTheTransactionBack() throws Exception {
        final String owner = newSession();
        final String watcher = newSession();
        cell("CREATE OR REPLACE DATABASE ftx_release_db", owner);
        cell("CREATE TABLE ftx_release_db.public.t (n INT)", owner);
        failAfterBegin("ftx_release_db.public.t", owner);
        failAfterBegin("ftx_release_db.public.t", owner);
        assertEquals(1, listed(watcher), "a second failed request continues the one open transaction");

        assertEquals(200, release(owner));
        assertEquals(0, listed(watcher), "the release ended it");
        assertEquals("0", cell("SELECT COUNT(*) FROM ftx_release_db.public.t", watcher),
            "and never committed its rows");
        release(watcher);
    }
}
