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
 * The HTTP wire's contract, pinned end to end over a real socket (docs/http-api.md): a statement that
 * fails is an ANSWER — HTTP 200 with success:false, the error and the session it ran in — while only a
 * request the server cannot handle is a 5xx; every result says whether it is a DML statement's count grid
 * (updateCount, -1 for anything else), so a query naming its columns like one is never taken for DML; a
 * temporal cell crosses with its whole fraction of a second, and a time or timestamp column with its
 * fractional-second precision as the scale; and a session can be created in a chosen
 * scope, resumed and released, while a request naming a session the server no longer holds is refused
 * unless it asks for a fresh one, and then says so (newSession).
 */
public class HttpWireContractTest {

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

    private HttpResponse<String> get(final String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(final String path, final String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(final String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).DELETE().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** One request; a null requireSession leaves the field out, as a client that never sets it does. */
    private HttpResponse<String> execute(final String sql, final String sessionId, final Boolean requireSession)
            throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }
        if (requireSession != null) {
            body.put("requireSession", requireSession);
        }
        return post("/api/execute", MAPPER.writeValueAsString(body));
    }

    private static JsonNode json(final HttpResponse<String> response) {
        return MAPPER.readTree(response.body());
    }

    private String newSession() throws Exception {
        return json(post("/api/sessions", "")).get("sessionId").asText();
    }

    /** One statement that must succeed. */
    private JsonNode ok(final String sql, final String sessionId) throws Exception {
        final HttpResponse<String> response = execute(sql, sessionId, null);
        assertEquals(200, response.statusCode(), sql);
        final JsonNode body = json(response);
        assertTrue(body.get("success").asBoolean(), sql + " -> " + response.body());
        return body;
    }

    private static JsonNode firstRow(final JsonNode response) {
        return response.get("resultSets").get(0).get("rows").get(0);
    }

    /**
     * A container crosses laid out at the session's JSON_INDENT, as the in-process driver and live's REST
     * answer lay it out, while a string keeps its quotes at any width.
     */
    @Test
    public void aContainerCrossesAtTheSessionsJsonIndent() throws Exception {
        final String id = newSession();
        try {
            ok("ALTER SESSION SET JSON_INDENT = 2", id);
            assertEquals("[\n  1,\n  2\n]", firstRow(ok("SELECT ARRAY_CONSTRUCT(1, 2)", id)).get(0).asText());
            assertEquals("{\n  \"k\": \"v\"\n}",
                firstRow(ok("SELECT OBJECT_CONSTRUCT('k', 'v')", id)).get(0).asText());
            assertEquals("\"abc\"", firstRow(ok("SELECT TO_VARIANT('abc')", id)).get(0).asText());
        } finally {
            ok("ALTER SESSION SET JSON_INDENT = 0", id);
        }
        assertEquals("[1,2]", firstRow(ok("SELECT ARRAY_CONSTRUCT(1, 2)", id)).get(0).asText());
    }

    private static long updateCount(final JsonNode response, final int index) {
        return response.get("resultSets").get(index).get("updateCount").asLong();
    }

    @Test
    public void aFailingStatementIsAnAnswerAndKeepsItsSession() throws Exception {
        final String id = newSession();
        ok("CREATE OR REPLACE DATABASE wire_fail_db", id);
        final HttpResponse<String> failed = execute("SELECT * FROM no_such_table_at_all", id, null);
        assertEquals(200, failed.statusCode());
        final JsonNode body = json(failed);
        assertFalse(body.get("success").asBoolean());
        assertTrue(body.get("errorMessage").asText().contains("does not exist or not authorized"), failed.body());
        assertEquals(id, body.get("sessionId").asText());
        assertFalse(body.get("newSession").asBoolean());
        // A syntax error is an answer too, and the session keeps the database its CREATE activated.
        assertEquals(200, execute("SELECT FROM", id, null).statusCode());
        assertEquals("WIRE_FAIL_DB", firstRow(ok("SELECT CURRENT_DATABASE()", id)).get(0).asText());
        ok("DROP DATABASE wire_fail_db", id);
    }

    @Test
    public void aFirstStatementThatFailsStillHandsBackItsSession() throws Exception {
        final HttpResponse<String> failed = execute("SELECT 1/0", null, null);
        assertEquals(200, failed.statusCode());
        final JsonNode body = json(failed);
        assertFalse(body.get("success").asBoolean());
        assertTrue(body.get("newSession").asBoolean());
        assertTrue(body.get("sessionId").isTextual(), failed.body());
        assertFalse(ok("SELECT 1", body.get("sessionId").asText()).get("newSession").asBoolean());
    }

    /**
     * A statement that fails inside a pack is NAMED on the wire, so a client can tell which one failed and
     * where, and no partial result sets come back with it — the account hands back none either.
     */
    @Test
    public void aFailureInsideAPackNamesTheStatementAndCarriesNoPartials() throws Exception {
        final String id = newSession();
        ok("CREATE OR REPLACE DATABASE wire_pack_db", id);
        final HttpResponse<String> failed =
            executeCounted("SELECT 1; SELECT * FROM no_such_table_at_all", id, Integer.valueOf(0));
        assertEquals(200, failed.statusCode());
        final JsonNode body = json(failed);
        assertFalse(body.get("success").asBoolean());
        assertTrue(body.get("errorMessage").asText().startsWith("JavaScript execution error: Uncaught"
            + " Execution of multiple statements failed on statement \"SELECT * FROM no_such_table_at_all\""
            + " (at line 1, position 10)."), failed.body());
        assertTrue(body.get("errorMessage").asText().contains("does not exist or not authorized"), failed.body());
        assertTrue(body.get("resultSets") == null || body.get("resultSets").size() == 0, failed.body());
        assertEquals(id, body.get("sessionId").asText());
        ok("DROP DATABASE wire_pack_db", id);
    }

    @Test
    public void everyResultSaysWhetherItIsADmlCount() throws Exception {
        final String id = newSession();
        ok("CREATE OR REPLACE DATABASE wire_count_db", id);
        assertEquals(-1, updateCount(ok("CREATE TABLE t (i INT)", id), 0));
        assertEquals(2, updateCount(ok("INSERT INTO t VALUES (1), (2)", id), 0));
        assertEquals(1, updateCount(ok("UPDATE t SET i = 10 WHERE i = 1", id), 0));
        assertEquals(1, updateCount(ok("DELETE FROM t WHERE i = 2", id), 0));
        assertEquals(2, updateCount(ok("MERGE INTO t USING (SELECT 10 AS i UNION ALL SELECT 5) s ON t.i = s.i "
            + "WHEN MATCHED THEN UPDATE SET i = s.i + 1 WHEN NOT MATCHED THEN INSERT (i) VALUES (s.i)", id), 0));
        // A query naming its column like a count grid is still a query.
        final JsonNode spoof = ok("SELECT 9 AS \"number of rows inserted\"", id);
        assertEquals(-1, updateCount(spoof, 0));
        assertEquals(9, firstRow(spoof).get(0).asInt());
        // The grid itself is unchanged, and each statement of a script is marked on its own. A pack has
        // to be asked for, so this one declares the two statements it holds.
        final JsonNode both = json(executeCounted("INSERT INTO t VALUES (3); SELECT COUNT(*) FROM t", id,
            Integer.valueOf(2)));
        assertTrue(both.get("success").asBoolean(), both.toString());
        assertEquals("number of rows inserted",
            both.get("resultSets").get(0).get("columns").get(0).get("name").asText());
        assertEquals(1, updateCount(both, 0));
        assertEquals(-1, updateCount(both, 1));
        ok("DROP DATABASE wire_count_db", id);
    }

    @Test
    public void aTemporalCellCrossesWithItsWholeFractionOfASecond() throws Exception {
        final JsonNode row = firstRow(ok("SELECT '2024-01-01 00:00:00.123456789'::TIMESTAMP_NTZ, "
            + "'2024-01-01 00:00:00.123'::TIMESTAMP_NTZ, '2024-01-01 00:00:00.1234'::TIMESTAMP_NTZ, "
            + "'2024-01-01 00:00:00'::TIMESTAMP_NTZ, '12:34:56.5'::TIME, '12:34:56'::TIME, "
            + "'2024-01-01 00:00:00.123456789 +0200'::TIMESTAMP_TZ, '2024-01-01'::DATE", null));
        final String[] expected = {"2024-01-01 00:00:00.123456789", "2024-01-01 00:00:00.123",
            "2024-01-01 00:00:00.123400", "2024-01-01 00:00:00.000", "12:34:56.500", "12:34:56",
            "2024-01-01 00:00:00.123456789 +0200", "2024-01-01"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], row.get(i).asText(), "column " + i);
        }
    }

    /**
     * A VECTOR cell crosses as its TEXT, the way live's REST and JDBC render it - FLOAT elements with six
     * decimals, INT elements plain. Serialized as an object it arrived as its element type alone.
     */
    @Test
    public void aTextOrBinaryColumnCarriesItsLength() throws Exception {
        final String id = newSession();
        ok("CREATE OR REPLACE DATABASE wire_len_db", id);
        ok("CREATE TABLE wire_len_db.public.wire_len (u VARCHAR, b BINARY, n NUMBER(5,2), d DATE)", id);
        final JsonNode columns = ok("SELECT u, b, n, d, 'x'::VARCHAR(9) AS v9, TO_BINARY('AB', 'HEX') AS tb"
            + " FROM wire_len_db.public.wire_len", id).get("resultSets").get(0).get("columns");
        assertEquals(16777216, columns.get(0).get("length").asInt());
        assertEquals(8388608, columns.get(1).get("length").asInt());
        // Only text and binary carry one: a NUMBER keeps its precision and scale, and no other type
        // sends the field at all.
        assertFalse(columns.get(2).has("length"), columns.toString());
        assertEquals(5, columns.get(2).get("precision").asInt());
        assertFalse(columns.get(3).has("length"), columns.toString());
        assertEquals(9, columns.get(4).get("length").asInt());
        assertEquals(67108864, columns.get(5).get("length").asInt());
        ok("DROP DATABASE wire_len_db", id);
    }

    /**
     * A TIME or TIMESTAMP column carries precision 0 and its fractional-second precision as the scale, the
     * pair the account's own result metadata carries; a DATE carries 0 for both, and a NUMBER keeps its own.
     */
    @Test
    public void aTimeOrTimestampColumnCarriesItsFractionalDigitsAsTheScale() throws Exception {
        final String id = newSession();
        ok("CREATE OR REPLACE DATABASE wire_scale_db", id);
        final JsonNode columns = ok("SELECT '10:00:00'::TIME(3) AS t3, '2024-01-01'::TIMESTAMP_NTZ(0) AS ntz0,"
            + " '2024-01-01'::TIMESTAMP_LTZ AS ltz, '2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ(5) AS tz5,"
            + " CURRENT_TIMESTAMP(3) AS now3, '2024-01-01'::DATE AS d, 1.5::NUMBER(5,2) AS n", id)
            .get("resultSets").get(0).get("columns");
        final int[][] expected = {{0, 3}, {0, 0}, {0, 9}, {0, 5}, {0, 3}, {0, 0}, {5, 2}};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], columns.get(i).get("precision").asInt(), columns.get(i).toString());
            assertEquals(expected[i][1], columns.get(i).get("scale").asInt(), columns.get(i).toString());
            assertFalse(columns.get(i).has("length"), columns.get(i).toString());
        }
        final JsonNode createdOn = ok("SHOW SCHEMAS IN DATABASE wire_scale_db", id).get("resultSets").get(0)
            .get("columns").get(0);
        assertEquals("created_on", createdOn.get("name").asText());
        assertEquals(0, createdOn.get("precision").asInt());
        assertEquals(3, createdOn.get("scale").asInt());
        ok("DROP DATABASE wire_scale_db", id);
    }

    @Test
    public void aBlankStatementFailsAsTheAccountFailsIt() throws Exception {
        for (final String blank : new String[] {"", "   ", ";", "-- nothing to run", "/* nor here */"}) {
            final HttpResponse<String> response = execute(blank, null, null);
            assertEquals(200, response.statusCode(), "[" + blank + "]");
            final JsonNode body = json(response);
            assertFalse(body.get("success").asBoolean(), "[" + blank + "] " + response.body());
            assertEquals("SQL compilation error:\nEmpty SQL statement.", body.get("errorMessage").asText(),
                "[" + blank + "]");
            assertTrue(body.hasNonNull("sessionId"), "[" + blank + "] " + response.body());
        }
    }

    @Test
    public void aRequestWithoutSqlIsRefusedInTheUsualShape() throws Exception {
        for (final String request : new String[] {"{}", "{\"sql\":null}", "{\"sessionId\":null}"}) {
            final HttpResponse<String> response = post("/api/execute", request);
            assertEquals(400, response.statusCode(), request);
            final JsonNode body = json(response);
            assertFalse(body.get("success").asBoolean(), request + " -> " + response.body());
            assertEquals("SQL is required", body.get("errorMessage").asText(), request);
            // Nothing ran, so no session holds it; and there is no second error vocabulary to learn.
            assertTrue(body.get("sessionId").isNull(), request + " -> " + response.body());
            assertFalse(body.has("error"), request + " -> " + response.body());
        }
    }

    /** One request declaring how many statements it holds; a null declares none. */
    private HttpResponse<String> executeCounted(final String sql, final String sessionId,
                                                final Integer multiStatementCount) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }
        if (multiStatementCount != null) {
            body.put("multiStatementCount", multiStatementCount);
        }
        return post("/api/execute", MAPPER.writeValueAsString(body));
    }

    @Test
    public void aPackTheCallerDidNotAskForIsRefused() throws Exception {
        final String id = newSession();
        final JsonNode refused = json(executeCounted("SELECT 1 AS one; SELECT 2 AS two", id, null));
        assertFalse(refused.get("success").asBoolean(), refused.toString());
        assertEquals("Actual statement count 2 did not match the desired statement count 1.",
            refused.get("errorMessage").asText());
        assertEquals(id, refused.get("sessionId").asText());
        // Nothing in the pack ran.
        assertEquals(0, refused.get("resultSets").size());
    }

    @Test
    public void aRequestMayAskForThePackItSends() throws Exception {
        final String id = newSession();
        assertEquals(2, json(executeCounted("SELECT 1 AS one; SELECT 2 AS two", id, Integer.valueOf(2)))
            .get("resultSets").size());
        // The count is matched in both directions, and 0 means any number.
        assertEquals("Actual statement count 1 did not match the desired statement count 2.",
            json(executeCounted("SELECT 1 AS one", id, Integer.valueOf(2))).get("errorMessage").asText());
        assertEquals(3, json(executeCounted("SELECT 1 AS a; SELECT 2 AS b; SELECT 3 AS c", id,
            Integer.valueOf(0))).get("resultSets").size());
    }

    @Test
    public void theSessionsCountAnswersForARequestThatDeclaresNone() throws Exception {
        final String id = newSession();
        ok("ALTER SESSION SET MULTI_STATEMENT_COUNT = 2", id);
        try {
            assertEquals(2, json(executeCounted("SELECT 1 AS one; SELECT 2 AS two", id, null))
                .get("resultSets").size());
            // Under a session count of 2 even a single statement is refused, as the account refuses it,
            // and a request declaring its own count is the way back.
            assertEquals("Actual statement count 1 did not match the desired statement count 2.",
                json(executeCounted("SELECT 1 AS one", id, null)).get("errorMessage").asText());
        } finally {
            assertTrue(json(executeCounted("ALTER SESSION SET MULTI_STATEMENT_COUNT = 1", id,
                Integer.valueOf(1))).get("success").asBoolean());
        }
    }

    @Test
    public void whatTheAccountDoesNotCountPassesTheGate() throws Exception {
        final String id = newSession();
        assertEquals(1, ok("SELECT 1 AS one;", id).get("resultSets").size());
        assertEquals(1, ok("SELECT 1 AS one; ;", id).get("resultSets").size());
        assertEquals(1, ok("SELECT ';' AS semi", id).get("resultSets").size());
        assertEquals(1, ok("BEGIN LET x := 1; RETURN :x + 1; END", id).get("resultSets").size());
        // A script holding no statement is not a count mismatch; it fails as an empty statement.
        assertEquals("SQL compilation error:\nEmpty SQL statement.",
            json(executeCounted("   ", id, null)).get("errorMessage").asText());
    }

    @Test
    public void aVectorCellCrossesAsItsText() throws Exception {
        final JsonNode row = firstRow(ok("SELECT [1,2,3]::VECTOR(FLOAT,3), [4,5,6]::VECTOR(INT,3),"
            + " VECTOR_TRUNC([1,2,3]::VECTOR(FLOAT,3), 0)", null));
        assertEquals("[1.000000,2.000000,3.000000]", row.get(0).asText());
        assertEquals("[4,5,6]", row.get(1).asText());
        assertEquals("[]", row.get(2).asText());
    }

    /**
     * A higher-order function's ARRAY crosses as its TEXT, like every other ARRAY cell. Written as a raw tree
     * it arrived as a nested JSON array, and an undefined element as a bare token that made the whole body
     * unreadable, which readTree in {@code ok} would refuse.
     */
    @Test
    public void aHigherOrderArrayCellCrossesAsItsText() throws Exception {
        final JsonNode row = firstRow(ok("SELECT FILTER(ARRAY_CONSTRUCT(1, NULL, 2), x -> x IS NOT NULL),"
            + " TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> NULL), ARRAY_CONSTRUCT(1, NULL, 2)", null));
        assertEquals("[1,2]", row.get(0).asText());
        assertEquals("[undefined,undefined]", row.get(1).asText());
        assertEquals("[1,undefined,2]", row.get(2).asText());
    }

    /**
     * A semi-structured DOUBLE crosses in the account's fifteen-decimal form: a whole value, a member, and
     * an element read out of a VARIANT alike. The last is a text cell like every VARIANT cell, where it
     * crossed as a bare JSON number. A DECIMAL keeps its digits and a non-finite double its bare word.
     */
    @Test
    public void aVariantDoubleCrossesInTheAccountsForm() throws Exception {
        final JsonNode row = firstRow(ok("SELECT TO_VARIANT(1.5::FLOAT), ARRAY_CONSTRUCT(1.0::FLOAT, 2.5::FLOAT),"
            + " PARSE_JSON('[1.5e0]')[0], PARSE_JSON('1.5'), TO_VARIANT('NaN'::FLOAT)", null));
        assertEquals("1.500000000000000e+00", row.get(0).asText());
        assertEquals("[1.000000000000000e+00,2.500000000000000e+00]", row.get(1).asText());
        assertTrue(row.get(2).isTextual(), "an element read out of a VARIANT is a text cell like any other");
        assertEquals("1.500000000000000e+00", row.get(2).asText());
        assertEquals("1.5", row.get(3).asText());
        assertEquals("NaN", row.get(4).asText());
    }

    @Test
    public void aSessionIsCreatedResumedStrictlyAndReleased() throws Exception {
        final HttpResponse<String> created = post("/api/sessions", "");
        assertEquals(200, created.statusCode());
        final JsonNode createdBody = json(created);
        assertTrue(createdBody.get("success").asBoolean());
        assertTrue(createdBody.get("newSession").asBoolean());
        final String id = createdBody.get("sessionId").asText();
        final JsonNode resumed = json(execute("SELECT 1", id, Boolean.TRUE));
        assertTrue(resumed.get("success").asBoolean());
        assertFalse(resumed.get("newSession").asBoolean());
        assertEquals(id, resumed.get("sessionId").asText());

        final HttpResponse<String> released = delete("/api/sessions/" + id);
        assertEquals(200, released.statusCode());
        assertTrue(json(released).get("success").asBoolean());
        assertEquals(404, delete("/api/sessions/" + id).statusCode());

        // Resuming the released id is refused, whether the request asks for that or says nothing, and the
        // statement never runs.
        for (final Boolean require : new Boolean[] {Boolean.TRUE, null}) {
            final HttpResponse<String> strict = execute("CREATE DATABASE wire_never_db", id, require);
            assertEquals(404, strict.statusCode(), "requireSession " + require);
            final JsonNode strictBody = json(strict);
            assertFalse(strictBody.get("success").asBoolean());
            assertTrue(strictBody.get("sessionId").isNull());
            assertTrue(strictBody.get("errorMessage").asText().contains(id), strict.body());
        }
        // Only an explicit requireSession:false starts a fresh session under the id — and says so.
        final HttpResponse<String> lenientResponse = execute("SHOW DATABASES LIKE 'WIRE_NEVER_DB'", id,
            Boolean.FALSE);
        assertEquals(200, lenientResponse.statusCode());
        final JsonNode lenient = json(lenientResponse);
        assertTrue(lenient.get("success").asBoolean(), lenientResponse.body());
        assertTrue(lenient.get("newSession").asBoolean());
        assertEquals(id, lenient.get("sessionId").asText());
        assertEquals(0, lenient.get("resultSets").get(0).get("rowCount").asInt());
        delete("/api/sessions/" + id);
    }

    @Test
    public void aCreatedSessionStartsInTheScopeItAskedFor() throws Exception {
        final String admin = newSession();
        ok("CREATE OR REPLACE DATABASE \"wire dsn db\"", admin);
        ok("CREATE SCHEMA \"low sch\"", admin);
        final Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("database", "\"wire dsn db\"");
        scope.put("schema", "\"low sch\"");
        final JsonNode created = json(post("/api/sessions", MAPPER.writeValueAsString(scope)));
        assertTrue(created.get("success").asBoolean(), created.toString());
        final JsonNode row = firstRow(ok("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()",
            created.get("sessionId").asText()));
        assertEquals("wire dsn db", row.get(0).asText());
        assertEquals("low sch", row.get(1).asText());

        // A scope that selects nothing fails up front and leaves no session behind.
        final int before = json(get("/api/sessions")).get("activeSessions").asInt();
        final Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("database", "no_such_wire_db");
        final HttpResponse<String> refused = post("/api/sessions", MAPPER.writeValueAsString(missing));
        assertEquals(200, refused.statusCode());
        final JsonNode refusedBody = json(refused);
        assertFalse(refusedBody.get("success").asBoolean());
        assertTrue(refusedBody.get("sessionId").isNull());
        assertTrue(refusedBody.get("errorMessage").asText().contains("Object does not exist"), refused.body());
        assertEquals(before, json(get("/api/sessions")).get("activeSessions").asInt());
        ok("DROP DATABASE \"wire dsn db\"", admin);
    }

    @Test
    public void releasingASessionRollsBackItsOpenTransaction() throws Exception {
        final String admin = newSession();
        ok("CREATE OR REPLACE DATABASE wire_txn_db", admin);
        ok("CREATE TABLE t (i INT)", admin);
        final String worker = newSession();
        ok("USE DATABASE wire_txn_db", worker);
        ok("BEGIN", worker);
        ok("INSERT INTO t VALUES (1)", worker);
        assertEquals(200, delete("/api/sessions/" + worker).statusCode());
        assertEquals(0, firstRow(ok("SELECT COUNT(*) FROM t", admin)).get(0).asInt());
        assertEquals(1, updateCount(ok("INSERT INTO t VALUES (2)", admin), 0));
        ok("DROP DATABASE wire_txn_db", admin);
    }

    @Test
    public void theSessionCollectionAnswersOnlyItsVerbs() throws Exception {
        assertEquals(200, get("/api/sessions").statusCode());
        final HttpResponse<String> put = client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/sessions"))
            .PUT(HttpRequest.BodyPublishers.ofString("")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, put.statusCode());
        assertEquals(405, delete("/api/sessions").statusCode());
    }
}
