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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request may ask to be run read-only, and a session may be opened so: such a request runs only when every
 * statement of it is a query, a SHOW, a DESCRIBE or an EXPLAIN that reaches no write, and is refused before
 * anything runs otherwise; what its statements reach while they run — a procedure called in its FROM clause, a
 * sequence a function's body draws — is held to the same rule.
 */
public class ReadOnlyRequestWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ONLY = " Only a query, SHOW, DESCRIBE and EXPLAIN run.";
    private static DatabaseHttpServer server;
    private static String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private String sessionId;

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

    @BeforeEach
    public void aSessionWithThreeRows() throws Exception {
        sessionId = post("/api/sessions", new LinkedHashMap<>()).get("sessionId").asText();
        for (final String setup : new String[] {"CREATE OR REPLACE DATABASE ro_db", "USE SCHEMA ro_db.public",
            "CREATE OR REPLACE TABLE t (a INT)", "INSERT INTO t VALUES (1), (2), (3)", "CREATE OR REPLACE SEQUENCE sq",
            """
            CREATE OR REPLACE PROCEDURE p_reads() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET \
            DEFAULT (SELECT a FROM t ORDER BY a); BEGIN RETURN TABLE(r); END $$""",
            """
            CREATE OR REPLACE PROCEDURE p_writes() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET \
            DEFAULT (SELECT a FROM t ORDER BY a); BEGIN DELETE FROM t; RETURN TABLE(r); END $$"""}) {
            assertNull(error(run(setup, null)), setup);
        }
    }

    private JsonNode post(final String path, final Map<String, Object> body) throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body());
    }

    /** The answer to one statement in the test's session; a null flag declares nothing. */
    private JsonNode run(final String sql, final Boolean readOnly) throws Exception {
        return runIn(sessionId, sql, readOnly);
    }

    private JsonNode runIn(final String session, final String sql, final Boolean readOnly) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        body.put("sessionId", session);
        body.put("multiStatementCount", Integer.valueOf(0));
        if (readOnly != null) {
            body.put("readOnly", readOnly);
        }
        return post("/api/execute", body);
    }

    private static String error(final JsonNode answer) {
        final JsonNode message = answer.get("errorMessage");
        return message == null || message.isNull() ? null : message.asText();
    }

    private String firstCell(final String sql) throws Exception {
        final JsonNode answer = run(sql, null);
        assertNull(error(answer), sql);
        return answer.get("resultSets").get(0).get("rows").get(0).get(0).asText();
    }

    private void assertRefused(final String expected, final String sql) throws Exception {
        final JsonNode answer = run(sql, Boolean.TRUE);
        assertFalse(answer.get("success").asBoolean(), sql);
        assertEquals(expected, error(answer), sql);
        assertEquals("3", firstCell("SELECT COUNT(*) FROM t"), sql);
    }

    @Test
    public void whatOnlyReadsRuns() throws Exception {
        for (final String reading : new String[] {"SELECT a FROM t ORDER BY a", "WITH c AS (SELECT 1 AS x) SELECT x FROM c",
            "SHOW TABLES", "DESCRIBE TABLE t", "EXPLAIN SELECT a FROM t", "EXPLAIN DELETE FROM t",
            "SELECT 1; SELECT 2", "SELECT SYSTEM$TYPEOF(1)", "SELECT * FROM TABLE(p_reads()) ORDER BY a",
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"}) {
            final JsonNode answer = run(reading, Boolean.TRUE);
            assertTrue(answer.get("success").asBoolean(), reading + ": " + error(answer));
        }
        assertEquals("3", firstCell("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aStatementThatMayWriteIsRefusedBeforeAnythingRuns() throws Exception {
        assertRefused("Read-only request: a statement that begins with DELETE may write." + ONLY, "DELETE FROM t");
        assertRefused("Read-only request: a statement that begins with DELETE may write." + ONLY,
            "SELECT 1; DELETE FROM t");
        assertRefused("Read-only request: a statement that begins with INSERT may write." + ONLY,
            "INSERT INTO t VALUES (9)");
        assertRefused("Read-only request: a statement that begins with CREATE may write." + ONLY,
            "CREATE TABLE made (a INT)");
        assertRefused("Read-only request: a statement that begins with CALL may write." + ONLY, "CALL p_reads()");
        assertRefused("Read-only request: a statement that begins with BEGIN may write." + ONLY,
            "BEGIN DELETE FROM t; RETURN 1; END;");
        assertRefused("Read-only request: a statement that begins with USE may write." + ONLY, "USE SCHEMA ro_db.public");
        assertRefused("Read-only request: a statement that begins with SET may write." + ONLY, "SET v = 1");
        assertRefused("Read-only request: a statement that begins with DELETE may write." + ONLY,
            "SELECT 1 ->> DELETE FROM t");
    }

    @Test
    public void aQueryThatReachesAWriteIsRefused() throws Exception {
        assertRefused("Read-only request: NEXTVAL moves a sequence.", "SELECT sq.NEXTVAL");
        assertRefused("Read-only request: NEXTVAL moves a sequence.", "SELECT sq.\"NEXTVAL\"");
        assertRefused("Read-only request: NEXTVAL moves a sequence.", "EXPLAIN SELECT sq.NEXTVAL");
        assertRefused("Read-only request: TO_QUERY may run a procedure.",
            "SELECT * FROM TABLE(TO_QUERY('CALL p_writes()'))");
        assertRefused("Read-only request: TO_QUERY may run a procedure.",
            "SELECT * FROM TABLE(\"TO_QUERY\"('SELECT 1'))");
        assertRefused("Read-only request: SYSTEM$ABORT_SESSION may act on the server.",
            "SELECT SYSTEM$ABORT_SESSION(1)");
        assertRefused("Read-only request: SYSTEM$TASK_DEPENDENTS_ENABLE may act on the server.",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('nope')");
    }

    /** A function's body that draws a sequence is refused as the request's own NEXTVAL is, the sequence unmoved. */
    @Test
    public void aFunctionsBodyMovesNoSequence() throws Exception {
        assertNull(error(run("CREATE OR REPLACE FUNCTION f_next() RETURNS NUMBER AS 'sq.NEXTVAL'", null)));
        assertRefused("Read-only request: NEXTVAL moves a sequence.", "SELECT f_next()");
        assertEquals("1", firstCell("SELECT sq.NEXTVAL"));
    }

    /** A procedure in the FROM clause is called, and what it runs may read and may not write. */
    @Test
    public void aProcedureInFromIsHeldToTheRuleAsItRuns() throws Exception {
        final JsonNode refused = run("SELECT * FROM TABLE(p_writes()) ORDER BY a", Boolean.TRUE);
        assertEquals("""
            Stored procedure execution error on line 1 position 20: Uncaught exception of type 'STATEMENT_ERROR' on \
            line 1 at position 65 : SQL compilation error: Invalid statement type 'DELETE' for child job from stored \
            procedure in FROM clause on TABLE 'T'.""", error(refused));
        assertEquals("3", firstCell("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aTextThatDoesNotParseIsRefusedForItsSyntax() throws Exception {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 0 unexpected 'SELEC'.",
            error(run("SELEC 1", Boolean.TRUE)));
    }

    @Test
    public void aRequestThatDoesNotAskRunsAsBefore() throws Exception {
        assertNull(error(run("DELETE FROM t WHERE a = 3", null)));
        assertNull(error(run("DELETE FROM t WHERE a = 2", Boolean.FALSE)));
        assertEquals("1", firstCell("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aSessionOpenedReadOnlyRefusesEveryWritingRequest() throws Exception {
        final Map<String, Object> opening = new LinkedHashMap<>();
        opening.put("database", "ro_db");
        opening.put("schema", "public");
        opening.put("readOnly", Boolean.TRUE);
        final String readOnlySession = post("/api/sessions", opening).get("sessionId").asText();
        assertEquals("3", runIn(readOnlySession, "SELECT COUNT(*) FROM t", null)
            .get("resultSets").get(0).get("rows").get(0).get(0).asText());
        assertEquals("Read-only request: a statement that begins with DELETE may write." + ONLY,
            error(runIn(readOnlySession, "DELETE FROM t", null)));
        assertEquals("Read-only request: a statement that begins with DELETE may write." + ONLY,
            error(runIn(readOnlySession, "DELETE FROM t", Boolean.FALSE)));
        assertEquals("Read-only request: a statement that begins with USE may write." + ONLY,
            error(runIn(readOnlySession, "USE SCHEMA ro_db.public", null)));
        assertEquals("3", firstCell("SELECT COUNT(*) FROM t"));
    }
}
