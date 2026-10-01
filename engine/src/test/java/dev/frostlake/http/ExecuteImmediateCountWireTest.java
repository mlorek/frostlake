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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Over the HTTP wire an EXECUTE IMMEDIATE's text is counted against the request's {@code multiStatementCount}
 * when the request sends one, and a scripting block's against the session's; a request that is one EXECUTE
 * IMMEDIATE of several statements answers each statement's result set, while the same EXECUTE IMMEDIATE among
 * other statements answers the multi-statement row in its place.
 */
public class ExecuteImmediateCountWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEXT_OF_TWO = "EXECUTE IMMEDIATE 'SELECT 1 AS a; SELECT 2 AS b'";
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

    /** The answer to one request; a null count declares none, leaving the session's to answer. */
    private JsonNode answer(final String sql, final Integer multiStatementCount) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (multiStatementCount != null) {
            body.put("multiStatementCount", multiStatementCount);
        }
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), sql);
        return MAPPER.readTree(response.body());
    }

    /** Each result set as its first column's name and first cell. */
    private static List<String> resultSets(final JsonNode answer) {
        final List<String> sets = new ArrayList<>();
        for (final JsonNode set : answer.get("resultSets")) {
            sets.add(set.get("columns").get(0).get("name").asText() + "=" + set.get("rows").get(0).get(0).asText());
        }
        return sets;
    }

    @Test
    public void theRequestsCountGatesTheText() throws Exception {
        assertEquals("Actual statement count 2 did not match the desired statement count 1.",
            answer(TEXT_OF_TWO, null).get("errorMessage").asText());
        assertEquals(List.of("A=1", "B=2"), resultSets(answer(TEXT_OF_TWO, Integer.valueOf(0))));
        assertEquals("Actual statement count 1 did not match the desired statement count 2.",
            answer(TEXT_OF_TWO, Integer.valueOf(2)).get("errorMessage").asText());
    }

    @Test
    public void aBlockIsHeldToTheSessionsCount() throws Exception {
        assertEquals("""
            Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Actual statement count 2 did not \
            match the desired statement count 1.""",
            answer("BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; RETURN 'ran'; END;", Integer.valueOf(0))
                .get("errorMessage").asText());
    }

    @Test
    public void oneStatementOfSeveralAnswersItsOwnRow() throws Exception {
        assertEquals(List.of("Z=0", "multiple statement execution=Multiple statements executed successfully."),
            resultSets(answer("SELECT 0 AS z; " + TEXT_OF_TWO, Integer.valueOf(0))));
    }
}
