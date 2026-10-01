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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Over the HTTP wire every session answers its own CURRENT_SESSION(): two requests that each open a new session get
 * two numbers, and a later request naming the first session gets the first number again — where every session used
 * to answer the one number of the engine's own session context.
 */
public class SessionNumberWireTest {

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

    /** One request's answer; a null session id asks for a new session. */
    private JsonNode execute(final String sql, final String sessionId) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
            body.put("requireSession", Boolean.TRUE);
        }
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        final JsonNode answer = MAPPER.readTree(response.body());
        assertTrue(answer.get("success").asBoolean(), response.body());
        return answer;
    }

    private static String firstCell(final JsonNode answer) {
        return answer.get("resultSets").get(0).get("rows").get(0).get(0).asText();
    }

    @Test
    public void everySessionAnswersItsOwnNumber() throws Exception {
        final JsonNode first = execute("SELECT CURRENT_SESSION()", null);
        final JsonNode second = execute("SELECT CURRENT_SESSION()", null);
        final String firstNumber = firstCell(first);
        final String secondNumber = firstCell(second);
        assertTrue(firstNumber.matches("[1-9][0-9]{15}"), firstNumber);
        assertNotEquals(first.get("sessionId").asText(), second.get("sessionId").asText());
        assertNotEquals(firstNumber, secondNumber, "two new sessions, two numbers");
        assertEquals(firstNumber, firstCell(execute("SELECT CURRENT_SESSION()", first.get("sessionId").asText())),
            "a session keeps its number from one request to the next");
        assertEquals(secondNumber, firstCell(execute("SELECT CURRENT_SESSION()", second.get("sessionId").asText())));
    }

    @Test
    public void aSessionsVariablesAreListedUnderItsNumber() throws Exception {
        final JsonNode opened = execute("SET wire_var = 1", null);
        final String session = opened.get("sessionId").asText();
        final String number = firstCell(execute("SELECT CURRENT_SESSION()", session));
        assertEquals(number, firstCell(execute("SHOW VARIABLES", session)));
    }
}
