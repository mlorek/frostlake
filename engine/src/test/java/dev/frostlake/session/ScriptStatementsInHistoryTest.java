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

package dev.frostlake.session;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The statements a script runs are child statements of the session's: each takes a query ID, which LAST_QUERY_ID()
 * names, and a QUERY_HISTORY row under that ID. A block's own statements, an exception handler's, an EXECUTE
 * IMMEDIATE's text and a procedure's body took the IDs but no rows, so the ID LAST_QUERY_ID() handed out named
 * nothing in the history, and BEFORE(STATEMENT => id) refused it. The statements of an IF or a loop already had both.
 * A DDL statement of a block took no ID at all, where the same statement in an IF took both.
 *
 * <p>{@code SELECT * FROM TABLE(proc())} runs as the CALL of the procedure, which the engine writes for itself: that
 * CALL is the SELECT's own work and records nothing of its own, while the procedure's statements are its children.
 */
public class ScriptStatementsInHistoryTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ssh_db");
        engine.execute("USE SCHEMA ssh_db.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    /** The texts of the session's history rows in the order they completed, newlines folded to spaces. */
    private List<String> historyTexts() {
        final List<String> texts = new ArrayList<String>();
        for (final Row row : engine.executeQuery("SELECT query_text FROM TABLE("
                + "INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 10000))").getRows()) {
            texts.add(String.valueOf(row.getValue(0)).replace('\n', ' ').trim());
        }
        // QUERY_HISTORY_BY_SESSION answers the last to complete first.
        final List<String> oldestFirst = new ArrayList<String>();
        for (int i = texts.size() - 1; i >= 0; i--) {
            oldestFirst.add(texts.get(i));
        }
        return oldestFirst;
    }

    /** The history rows that completed after the last one whose text starts with {@code marker}, in order. */
    private List<String> rowsAfter(final String marker) {
        final List<String> texts = historyTexts();
        int from = -1;
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).startsWith(marker)) {
                from = i;
            }
        }
        final List<String> after = new ArrayList<String>();
        for (int i = from + 1; i < texts.size(); i++) {
            if (!texts.get(i).contains("QUERY_HISTORY_BY_SESSION")) {
                after.add(texts.get(i));
            }
        }
        return after;
    }

    private String historyColumn(final String column, final String queryId) {
        return first("SELECT " + column + " FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 10000))"
            + " WHERE query_id = '" + queryId + "'");
    }

    @Test
    public void aBlocksStatementsHaveRowsUnderTheirIds() {
        engine.executeQuery("SELECT 'mark'");
        final String[] ids = first("BEGIN SELECT 'one'; INSERT INTO t VALUES (3); CREATE TABLE t2 (a INT);"
            + " RETURN LAST_QUERY_ID(-3) || ',' || LAST_QUERY_ID(-2) || ',' || LAST_QUERY_ID(-1); END").split(",");
        assertEquals("SELECT 'one';", historyColumn("query_text", ids[0]));
        assertEquals("INSERT INTO t VALUES (3);", historyColumn("query_text", ids[1]));
        assertEquals("CREATE TABLE t2 (a INT);", historyColumn("query_text", ids[2]));
        assertEquals("INSERT", historyColumn("query_type", ids[1]));
        assertEquals("1", historyColumn("rows_produced", ids[1]));
        assertEquals("CREATE_TABLE", historyColumn("query_type", ids[2]));
        assertEquals("SUCCESS", historyColumn("execution_status", ids[2]));
        // Each completes, and is listed, before the block it ran in.
        assertEquals("[SELECT 'one';, INSERT INTO t VALUES (3);, CREATE TABLE t2 (a INT);, BEGIN SELECT 'one';"
            + " INSERT INTO t VALUES (3); CREATE TABLE t2 (a INT); RETURN LAST_QUERY_ID(-3) || ',' || LAST_QUERY_ID(-2)"
            + " || ',' || LAST_QUERY_ID(-1); END]", String.valueOf(rowsAfter("SELECT 'mark'")));
    }

    @Test
    public void executeImmediateOfABlockAndProcedureBodiesAlike() {
        engine.executeQuery("SELECT 'mark ei'");
        first("EXECUTE IMMEDIATE $$ BEGIN SELECT 'ei'; INSERT INTO t VALUES (4); END; $$");
        assertEquals("[SELECT 'ei';, INSERT INTO t VALUES (4);, EXECUTE IMMEDIATE $$ BEGIN SELECT 'ei';"
            + " INSERT INTO t VALUES (4); END; $$]", String.valueOf(rowsAfter("SELECT 'mark ei'")));

        engine.execute("CREATE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 'in p'; RETURN 'x'; END $$");
        engine.executeQuery("SELECT 'mark call'");
        first("CALL p()");
        assertEquals("[SELECT 'in p';, CALL p()]", String.valueOf(rowsAfter("SELECT 'mark call'")));

        engine.executeQuery("SELECT 'mark block call'");
        first("BEGIN CALL p(); END");
        assertEquals("[SELECT 'in p';, CALL p();, BEGIN CALL p(); END]",
            String.valueOf(rowsAfter("SELECT 'mark block call'")),
            "a CALL a block makes is a child statement too, completing after the statements the procedure ran");
    }

    @Test
    public void anExecuteImmediatesTextIsAChildStatement() {
        final String id = first("BEGIN EXECUTE IMMEDIATE 'SELECT ''dyn'''; RETURN LAST_QUERY_ID(-2); END");
        assertEquals("SELECT 'dyn'", historyColumn("query_text", id));
        assertEquals("dyn", first("SELECT * FROM TABLE(RESULT_SCAN('" + id + "'))"));
    }

    @Test
    public void aFailedStatementOfABlockIsAFailedRow() {
        final String id = first("BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN LAST_QUERY_ID(); END");
        assertEquals("FAILED_WITH_ERROR", historyColumn("execution_status", id));
        assertEquals("SELECT 1/0;", historyColumn("query_text", id));

        final String inHandler = first("BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN INSERT INTO t VALUES (5);"
            + " RETURN LAST_QUERY_ID(); END");
        assertEquals("INSERT INTO t VALUES (5);", historyColumn("query_text", inHandler),
            "a handler's statements are child statements too");
    }

    @Test
    public void aChildStatementsIdTimeTravels() {
        final String id = first("BEGIN INSERT INTO t VALUES (3); RETURN LAST_QUERY_ID(); END");
        assertEquals("2", first("SELECT COUNT(*) FROM t BEFORE(STATEMENT => '" + id + "')"));
        assertEquals("3", first("SELECT COUNT(*) FROM t AT(STATEMENT => '" + id + "')"));
    }

    @Test
    public void sqlTheScriptComposesStaysUnrecorded() {
        engine.execute("CREATE FUNCTION f() RETURNS NUMBER AS 'SELECT MAX(a) FROM t'");
        engine.executeQuery("SELECT 'mark'");
        first("BEGIN LET x := f(); SELECT f(); RETURN x; END");
        assertEquals("[SELECT f();, BEGIN LET x := f(); SELECT f(); RETURN x; END]",
            String.valueOf(rowsAfter("SELECT 'mark'")),
            "the UDF's body and the LET's evaluation are no statements of the session's");
    }

    @Test
    public void tableOfAProcedureRecordsNoCall() {
        engine.execute("CREATE PROCEDURE ptab() RETURNS TABLE (a INT) LANGUAGE SQL AS"
            + " $$ DECLARE r RESULTSET DEFAULT (SELECT a FROM t); BEGIN RETURN TABLE(r); END $$");
        engine.executeQuery("SELECT 'mark'");
        assertEquals("2", first("SELECT COUNT(*) FROM TABLE(ptab())"));
        assertEquals("2", first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"),
            "LAST_QUERY_ID names the SELECT the session sent");
        final List<String> rows = rowsAfter("SELECT 'mark'");
        assertEquals("[SELECT a FROM t, SELECT COUNT(*) FROM TABLE(ptab()), SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))]",
            String.valueOf(rows), "no CALL; the procedure's query is the SELECT's child, completing first");
    }
}
