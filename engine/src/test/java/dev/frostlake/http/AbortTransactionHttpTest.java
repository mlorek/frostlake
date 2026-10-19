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
 * Over a real socket: SYSTEM$ABORT_TRANSACTION ends a transaction another session holds, and that session keeps
 * the aborted transaction between its requests — each statement it sends is refused until it sends a COMMIT, which
 * fails, or a ROLLBACK. A session released while it still holds one leaves nothing behind.
 */
public class AbortTransactionHttpTest {

    private static final String IGNORED = "Your transaction was aborted. All statements will be ignored until you"
        + " issue a COMMIT or a ROLLBACK.";
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

    /** One request's parsed answer, whatever it says. */
    private JsonNode send(final String sql, final String sessionId) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        body.put("sessionId", sessionId);
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
        final JsonNode json = send(sql, sessionId);
        assertTrue(json.get("success").asBoolean(), sql + " -> " + json);
        return json.get("resultSets").get(0).get("rows").get(0).get(0).asText();
    }

    /** A statement that must fail, answering the whole response as text. */
    private String refusal(final String sql, final String sessionId) throws Exception {
        final JsonNode json = send(sql, sessionId);
        assertFalse(json.get("success").asBoolean(), sql + " -> " + json);
        return json.toString();
    }

    /** How many transactions SHOW TRANSACTIONS IN ACCOUNT lists, read from {@code sessionId}. */
    private int listed(final String sessionId) throws Exception {
        final JsonNode json = send("SHOW TRANSACTIONS IN ACCOUNT", sessionId);
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

    @Test
    public void theHoldingSessionIsRefusedRequestAfterRequestUntilItsCommitFails() throws Exception {
        final String holder = newSession();
        final String aborter = newSession();
        cell("CREATE OR REPLACE DATABASE atx_commit_db", holder);
        cell("CREATE TABLE atx_commit_db.public.t (n INT)", holder);
        send("BEGIN", holder);
        cell("INSERT INTO atx_commit_db.public.t VALUES (1)", holder);
        final String id = cell("SELECT CURRENT_TRANSACTION()", holder);
        assertEquals(1, listed(aborter));

        assertEquals("Aborted transaction id: " + id, cell("SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")", aborter));
        assertEquals(0, listed(aborter));
        assertEquals("0", cell("SELECT COUNT(*) FROM atx_commit_db.public.t", aborter));

        assertTrue(refusal("SELECT 1", holder).contains(IGNORED));
        assertTrue(refusal("SELECT COUNT(*) FROM atx_commit_db.public.t", holder).contains(IGNORED));
        final String commit = refusal("COMMIT", holder);
        assertTrue(commit.contains("COMMIT failed. Your transaction '"), commit);
        assertTrue(commit.contains("id '" + id + "', was already aborted."), commit);
        assertEquals("1", cell("SELECT 1", holder));
        assertEquals(id, cell("SELECT LAST_TRANSACTION()", holder));
        assertEquals("0", cell("SELECT COUNT(*) FROM atx_commit_db.public.t", holder));
        release(holder);
        release(aborter);
    }

    @Test
    public void aSessionThatAbortsItsOwnTransactionAcknowledgesWithARollback() throws Exception {
        final String session = newSession();
        cell("CREATE OR REPLACE DATABASE atx_own_db", session);
        cell("CREATE TABLE atx_own_db.public.t (n INT)", session);
        send("BEGIN", session);
        cell("INSERT INTO atx_own_db.public.t VALUES (1)", session);
        final String id = cell("SELECT CURRENT_TRANSACTION()", session);
        assertEquals("Aborted transaction id: " + id, cell("SELECT SYSTEM$ABORT_TRANSACTION('" + id + "')", session));
        assertTrue(refusal("SELECT 1", session).contains(IGNORED));
        assertTrue(send("ROLLBACK", session).get("success").asBoolean());
        assertEquals("0", cell("SELECT COUNT(*) FROM atx_own_db.public.t", session));
        assertEquals("Could not abort txn: " + id, cell("SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")", session));
        release(session);
    }

    @Test
    public void releasingASessionThatHoldsAnAbortedTransactionLeavesNothingBehind() throws Exception {
        final String holder = newSession();
        final String aborter = newSession();
        cell("CREATE OR REPLACE DATABASE atx_release_db", holder);
        cell("CREATE TABLE atx_release_db.public.t (n INT)", holder);
        send("BEGIN", holder);
        cell("INSERT INTO atx_release_db.public.t VALUES (1)", holder);
        final String id = cell("SELECT CURRENT_TRANSACTION()", holder);
        assertEquals("Aborted transaction id: " + id, cell("SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")", aborter));
        assertTrue(refusal("SELECT 1", holder).contains(IGNORED));

        assertEquals(200, release(holder));
        assertEquals(0, listed(aborter));
        assertEquals("Could not abort txn: " + id, cell("SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")", aborter));
        assertEquals("0", cell("SELECT COUNT(*) FROM atx_release_db.public.t", aborter));
        release(aborter);
    }
}
