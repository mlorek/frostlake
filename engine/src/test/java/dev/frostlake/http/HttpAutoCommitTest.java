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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The execute request's {@code autoCommit} field, over a real socket (docs/http-api.md). The session's
 * AUTOCOMMIT follows the client's mode whenever that mode changes, so a client that switches it off has
 * its statements held in a transaction that a ROLLBACK undoes — even across a DDL statement, which ends
 * the transaction in progress. A request that omits the field, or repeats the mode it last sent, leaves
 * an {@code ALTER SESSION SET AUTOCOMMIT} in force, which is what a client that drives autocommit through
 * SQL relies on.
 */
public class HttpAutoCommitTest {

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

    /** One statement that must succeed; a null {@code autoCommit} leaves the field out of the body. */
    private JsonNode run(final String sql, final String sessionId, final Boolean autoCommit) throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", sql);
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }
        if (autoCommit != null) {
            body.put("autoCommit", autoCommit);
        }
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), sql);
        final JsonNode json = MAPPER.readTree(response.body());
        assertTrue(json.get("success").asBoolean(), sql + " -> " + response.body());
        return json;
    }

    private String newSession() throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/sessions"))
            .POST(HttpRequest.BodyPublishers.ofString("")).build(), HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body()).get("sessionId").asText();
    }

    /** The committed row count of {@code table}, read from a session of its own. */
    private long committedRows(final String table) throws Exception {
        final JsonNode json = run("SELECT COUNT(*) FROM " + table, newSession(), null);
        return json.get("resultSets").get(0).get("rows").get(0).get(0).asLong();
    }

    /** A database holding an empty table {@code t (i INT)}, made through {@code session}. */
    private void freshTable(final String session, final String database, final Boolean autoCommit)
            throws Exception {
        run("CREATE OR REPLACE DATABASE " + database, session, autoCommit);
        run("CREATE TABLE " + database + ".public.t (i INT)", session, autoCommit);
    }

    @Test
    public void autocommitOffHoldsTheWorkUntilCommitOrRollback() throws Exception {
        final String session = newSession();
        freshTable(session, "ac_hold_db", null);
        run("INSERT INTO ac_hold_db.public.t VALUES (1)", session, Boolean.FALSE);
        assertEquals(0, committedRows("ac_hold_db.public.t"), "the INSERT is not committed yet");
        run("ROLLBACK", session, Boolean.FALSE);
        assertEquals(0, committedRows("ac_hold_db.public.t"), "the ROLLBACK undid it");
        run("INSERT INTO ac_hold_db.public.t VALUES (2)", session, Boolean.FALSE);
        run("COMMIT", session, Boolean.FALSE);
        assertEquals(1, committedRows("ac_hold_db.public.t"), "the COMMIT published it");
        run("DROP DATABASE ac_hold_db", session, Boolean.TRUE);
    }

    @Test
    public void switchingAutocommitBackOnCommitsEachStatementAgain() throws Exception {
        final String session = newSession();
        freshTable(session, "ac_back_db", Boolean.FALSE);
        run("INSERT INTO ac_back_db.public.t VALUES (1)", session, Boolean.FALSE);
        run("COMMIT", session, Boolean.FALSE);
        run("INSERT INTO ac_back_db.public.t VALUES (2)", session, Boolean.TRUE);
        assertEquals(2, committedRows("ac_back_db.public.t"), "the statement after the switch committed");
        run("ROLLBACK", session, Boolean.TRUE);
        assertEquals(2, committedRows("ac_back_db.public.t"), "nothing was left for the ROLLBACK to undo");
        run("DROP DATABASE ac_back_db", session, Boolean.TRUE);
    }

    @Test
    public void workAfterADdlStatementStaysInsideTheClientsTransaction() throws Exception {
        // The shape of a driver that opens its transactions with BEGIN and sends autoCommit:false until
        // the COMMIT or ROLLBACK: the CREATE ends the transaction BEGIN opened, and the INSERT after it
        // must still land in one the ROLLBACK undoes.
        final String session = newSession();
        freshTable(session, "ac_ddl_db", Boolean.TRUE);
        run("BEGIN", session, Boolean.FALSE);
        run("CREATE TABLE ac_ddl_db.public.side (b INT)", session, Boolean.FALSE);
        run("INSERT INTO ac_ddl_db.public.t VALUES (9)", session, Boolean.FALSE);
        run("ROLLBACK", session, Boolean.FALSE);
        assertEquals(0, committedRows("ac_ddl_db.public.t"));
        run("DROP DATABASE ac_ddl_db", session, Boolean.TRUE);
    }

    @Test
    public void aRequestWithoutTheFieldLeavesAlterSessionInForce() throws Exception {
        final String session = newSession();
        freshTable(session, "ac_alter_db", null);
        run("ALTER SESSION SET AUTOCOMMIT = FALSE", session, null);
        run("INSERT INTO ac_alter_db.public.t VALUES (1)", session, null);
        assertEquals(0, committedRows("ac_alter_db.public.t"));
        run("ROLLBACK", session, null);
        run("ALTER SESSION SET AUTOCOMMIT = TRUE", session, null);
        assertEquals(0, committedRows("ac_alter_db.public.t"));
        run("DROP DATABASE ac_alter_db", session, null);
    }

    @Test
    public void repeatingTheSameModeLeavesAlterSessionInForce() throws Exception {
        // A client that sends its unchanged mode on every request while it drives autocommit through
        // SQL: the repeated true is not re-applied over the ALTER SESSION.
        final String session = newSession();
        freshTable(session, "ac_repeat_db", Boolean.TRUE);
        run("ALTER SESSION SET AUTOCOMMIT = FALSE", session, Boolean.TRUE);
        run("INSERT INTO ac_repeat_db.public.t VALUES (1)", session, Boolean.TRUE);
        assertEquals(0, committedRows("ac_repeat_db.public.t"));
        run("ROLLBACK", session, Boolean.TRUE);
        assertEquals(0, committedRows("ac_repeat_db.public.t"));
        run("ALTER SESSION SET AUTOCOMMIT = TRUE", session, Boolean.TRUE);
        run("DROP DATABASE ac_repeat_db", session, Boolean.TRUE);
    }

    @Test
    public void aRequestThatDeclaresNoModeCarriesNoField() {
        final String undeclared = MAPPER.writeValueAsString(new SqlRequest("SELECT 1", null));
        assertFalse(undeclared.contains("autoCommit"), undeclared);
        final SqlRequest declared = new SqlRequest("SELECT 1", null);
        declared.setAutoCommit(Boolean.FALSE);
        final String sent = MAPPER.writeValueAsString(declared);
        assertTrue(sent.contains("\"autoCommit\":false"), sent);
        assertNull(MAPPER.readValue("{\"sql\":\"SELECT 1\"}", SqlRequest.class).getAutoCommit());
        assertEquals(Boolean.FALSE,
            MAPPER.readValue("{\"sql\":\"SELECT 1\",\"autoCommit\":false}", SqlRequest.class).getAutoCommit());
    }
}
