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
 * Over the HTTP wire SYSTEM$ABORT_SESSION ends the session whose CURRENT_SESSION() it is given: the session's open
 * transaction is rolled back, and its next request names no live session, so it is refused (404) like a request for
 * a released or expired one. A session that aborts itself still gets the function's answer, and is gone after it.
 */
public class AbortSessionWireTest {

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

    /** One request; a null session id asks for a new session. */
    private HttpResponse<String> send(final String sql, final String sessionId) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
            body.put("requireSession", Boolean.TRUE);
        }
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode ok(final String sql, final String sessionId) throws Exception {
        final HttpResponse<String> response = send(sql, sessionId);
        assertEquals(200, response.statusCode(), response.body());
        final JsonNode answer = MAPPER.readTree(response.body());
        assertTrue(answer.get("success").asBoolean(), response.body());
        return answer;
    }

    private static String firstCell(final JsonNode answer) {
        return answer.get("resultSets").get(0).get("rows").get(0).get(0).asText();
    }

    @Test
    public void anAbortedSessionIsGoneAndItsTransactionRolledBack() throws Exception {
        final String a = ok("CREATE OR REPLACE DATABASE abort_wire_db", null).get("sessionId").asText();
        ok("CREATE TABLE abort_wire_db.public.t (k INT, v INT)", a);
        ok("INSERT INTO abort_wire_db.public.t VALUES (1, 1)", a);
        final String b = ok("BEGIN", null).get("sessionId").asText();
        ok("UPDATE abort_wire_db.public.t SET v = 2 WHERE k = 1", b);
        final String number = firstCell(ok("SELECT CURRENT_SESSION()", b));
        assertTrue(ok("SHOW TRANSACTIONS IN ACCOUNT", a).get("resultSets").get(0).get("rows").toString()
            .contains(number), "the session's transaction is open before the abort");

        final JsonNode aborted = ok("SELECT SYSTEM$ABORT_SESSION(" + number + ")", a);
        assertEquals("session [" + number + "] terminated.", firstCell(aborted));
        assertEquals("SYSTEM$ABORT_SESSION(" + number + ")",
            aborted.get("resultSets").get(0).get("columns").get(0).get("name").asText());

        final HttpResponse<String> next = send("SELECT 1", b);
        assertEquals(404, next.statusCode(), next.body());
        assertFalse(MAPPER.readTree(next.body()).get("success").asBoolean(), next.body());
        assertFalse(ok("SHOW TRANSACTIONS IN ACCOUNT", a).get("resultSets").get(0).get("rows").toString()
            .contains(number), "the aborted session's transaction is rolled back");
        assertEquals("1", firstCell(ok("SELECT v FROM abort_wire_db.public.t", a)));
        // Aborting it again answers the same sentence and ends nothing.
        assertEquals("session [" + number + "] terminated.",
            firstCell(ok("SELECT SYSTEM$ABORT_SESSION(" + number + ")", a)));
        ok("DROP DATABASE abort_wire_db", a);
    }

    @Test
    public void aSessionThatAbortsItselfGetsItsAnswerAndIsGone() throws Exception {
        final JsonNode opened = ok("SELECT CURRENT_SESSION()", null);
        final String session = opened.get("sessionId").asText();
        final String number = firstCell(opened);
        assertEquals("session [" + number + "] terminated.",
            firstCell(ok("SELECT SYSTEM$ABORT_SESSION(CURRENT_SESSION())", session)));
        assertEquals(404, send("SELECT 1", session).statusCode());
    }

    @Test
    public void aNumberNoSessionHasEndsNothing() throws Exception {
        final JsonNode opened = ok("SELECT CURRENT_SESSION()", null);
        final String session = opened.get("sessionId").asText();
        assertEquals("session [1234567890123456] terminated.",
            firstCell(ok("SELECT SYSTEM$ABORT_SESSION(1234567890123456)", session)));
        assertEquals(firstCell(opened), firstCell(ok("SELECT CURRENT_SESSION()", session)));
    }
}
