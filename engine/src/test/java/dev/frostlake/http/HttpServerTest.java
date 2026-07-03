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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTTP server
 */
public class HttpServerTest {

    private static DatabaseHttpServer server;
    private static final int TEST_PORT = 18080;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    private HttpClient httpClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    public static void startServer() throws IOException {
        server = new DatabaseHttpServer(TEST_PORT);
        server.start();
        // Give server time to start
        try {
            Thread.sleep(1000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void setup() {
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    public void testHealthCheck() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/health"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"healthy\""));
    }

    @Test
    public void testCreateTable() throws Exception {
        SqlRequest sqlRequest = new SqlRequest();
        sqlRequest.setSql("CREATE DATABASE test_db");

        String requestJson = MAPPER.writeValueAsString(sqlRequest);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        SqlResponse sqlResponse = MAPPER.readValue(response.body(), SqlResponse.class);
        assertTrue(sqlResponse.isSuccess());
        assertNotNull(sqlResponse.getSessionId());
    }

    @Test
    public void testSessionPersistence() throws Exception {
        // First request: Create database
        SqlRequest req1 = new SqlRequest();
        req1.setSql("CREATE DATABASE session_test_db");

        HttpRequest request1 = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(req1)))
            .build();

        HttpResponse<String> response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
        SqlResponse sqlResponse1 = MAPPER.readValue(response1.body(), SqlResponse.class);

        assertTrue(sqlResponse1.isSuccess());
        String sessionId = sqlResponse1.getSessionId();
        assertNotNull(sessionId);

        // Second request: Use the database (should work with same session)
        SqlRequest req2 = new SqlRequest();
        req2.setSql("USE DATABASE session_test_db");
        req2.setSessionId(sessionId);

        HttpRequest request2 = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(req2)))
            .build();

        HttpResponse<String> response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
        SqlResponse sqlResponse2 = MAPPER.readValue(response2.body(), SqlResponse.class);

        assertTrue(sqlResponse2.isSuccess());
        assertEquals(sessionId, sqlResponse2.getSessionId());
    }

    @Test
    public void testQueryExecution() throws Exception {
        String sessionId = null;

        // Create database and table
        sessionId = executeSql("CREATE DATABASE query_test_db", sessionId);
        sessionId = executeSql("USE DATABASE query_test_db", sessionId);
        sessionId = executeSql("CREATE TABLE users (id INT, name VARCHAR)", sessionId);
        sessionId = executeSql("INSERT INTO users VALUES (1, 'Alice')", sessionId);
        sessionId = executeSql("INSERT INTO users VALUES (2, 'Bob')", sessionId);

        // Execute query
        SqlRequest queryReq = new SqlRequest();
        queryReq.setSql("SELECT * FROM users");
        queryReq.setSessionId(sessionId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(queryReq)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        SqlResponse sqlResponse = MAPPER.readValue(response.body(), SqlResponse.class);

        assertTrue(sqlResponse.isSuccess());
        assertEquals(1, sqlResponse.getResultSets().size());

        SqlResponse.ResultSetData resultSet = sqlResponse.getResultSets().get(0);
        assertEquals(2, resultSet.getRowCount());
        assertEquals(2, resultSet.getColumns().size());
        assertEquals(2, resultSet.getRows().size());
    }

    @Test
    public void testErrorHandling() throws Exception {
        SqlRequest badRequest = new SqlRequest();
        badRequest.setSql("SELECT * FROM nonexistent_table");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(badRequest)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        SqlResponse sqlResponse = MAPPER.readValue(response.body(), SqlResponse.class);

        assertFalse(sqlResponse.isSuccess());
        assertNotNull(sqlResponse.getErrorMessage());
    }

    @Test
    public void testSessionInfo() throws Exception {
        // Create a couple of sessions with simple commands
        executeSql("CREATE DATABASE test_session_db1", null);
        executeSql("CREATE DATABASE test_session_db2", null);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/sessions"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"activeSessions\""));
    }

    @Test
    public void testMethodNotAllowed() throws Exception {
        // Try GET on /api/execute (should only accept POST)
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    @Test
    public void testEmptySql() throws Exception {
        SqlRequest emptyRequest = new SqlRequest();
        emptyRequest.setSql("");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(emptyRequest)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    // Helper method
    private String executeSql(final String sql, final String sessionId) throws Exception {
        SqlRequest request = new SqlRequest();
        request.setSql(sql);
        request.setSessionId(sessionId);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        SqlResponse sqlResponse = MAPPER.readValue(response.body(), SqlResponse.class);

        assertTrue(sqlResponse.isSuccess(), "SQL execution failed: " + sql);
        return sqlResponse.getSessionId();
    }
}
