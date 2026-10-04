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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SQLID, the Snowflake Scripting variable beside SQLROWCOUNT, SQLFOUND and SQLNOTFOUND that holds the query ID of the
 * last statement the script ran. It was not recognised: {@code RETURN SQLID} was "invalid identifier 'SQLID'". It
 * names what LAST_QUERY_ID() names once that statement completes, and the statement's QUERY_HISTORY row.
 */
public class SqlidTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE sqlid_db");
        engine.execute("USE SCHEMA sqlid_db.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
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

    /** The text of the history row a query ID names. */
    private String historyText(final String queryId) {
        return first("SELECT query_text FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 10000))"
            + " WHERE query_id = '" + queryId + "'");
    }

    @Test
    public void sqlidIsTheLastStatementsQueryId() {
        final String id = first("BEGIN SELECT 'one'; RETURN SQLID; END");
        assertEquals("one", first("SELECT * FROM TABLE(RESULT_SCAN('" + id + "'))"));
        assertEquals("SELECT 'one';", historyText(id), "the ID names the statement's own history row");
        assertEquals("one", first("EXECUTE IMMEDIATE $$ BEGIN SELECT 'one'; LET q := SQLID;"
            + " LET r RESULTSET := (SELECT * FROM TABLE(RESULT_SCAN(:q))); RETURN TABLE(r); END; $$"));
    }

    @Test
    public void sqlidIsNullBeforeAnyStatement() {
        assertEquals("null", first("BEGIN RETURN SQLID; END"));
        assertEquals("null", first("BEGIN LET x := 1; RETURN SQLID; END"), "a LET runs no statement");
    }

    @Test
    public void sqlidFollowsLastQueryIdThroughTheScript() {
        assertEquals("true", first("BEGIN SELECT 1; LET q := SQLID; INSERT INTO t VALUES (1);"
            + " RETURN q = LAST_QUERY_ID(-2) AND SQLID = LAST_QUERY_ID(); END"));
        assertEquals("true", first("BEGIN SELECT 1; LET x := 1; x := x + 1; RETURN SQLID = LAST_QUERY_ID(); END"),
            "scripting statements leave it");
        assertEquals("true", first("BEGIN CREATE TABLE t2 (a INT); RETURN SQLID = LAST_QUERY_ID(); END"),
            "a DDL statement takes a query ID too");
        assertEquals("true", first("BEGIN IF (TRUE) THEN SELECT 'in if'; RETURN SQLID = LAST_QUERY_ID(); END IF; END"));
        assertEquals("true", first("BEGIN FOR i IN 1 TO 2 DO SELECT :i; END FOR; RETURN SQLID = LAST_QUERY_ID(); END"));
        assertEquals("true", first("BEGIN BEGIN SELECT 'nested'; END; RETURN SQLID = LAST_QUERY_ID(); END"));
        assertEquals("true", first("BEGIN LET r RESULTSET := (SELECT 1); RETURN SQLID = LAST_QUERY_ID(); END"));
    }

    @Test
    public void aCallIsTheLastStatementOnceItReturns() {
        engine.execute("CREATE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 'in p'; RETURN SQLID; END $$");
        final String inside = first("CALL p()");
        assertEquals("SELECT 'in p';", historyText(inside), "inside the procedure, its own SELECT");
        assertEquals("CALL p();", historyText(first("BEGIN CALL p(); RETURN SQLID; END")));
    }

    @Test
    public void sqlidReadsAsAText() {
        assertEquals("VARCHAR[LOB]", first("BEGIN SELECT 1; RETURN SYSTEM$TYPEOF(SQLID); END"));
        assertEquals("36", first("BEGIN SELECT 1; RETURN LENGTH(:SQLID); END"), "bound, it is the query ID");
    }

    @Test
    public void aFailedStatementLeavesIt() {
        assertEquals("true", first("BEGIN SELECT 1; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN"
            + " RETURN SQLID = LAST_QUERY_ID(-2); END; END"));
    }
}
