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

package dev.frostlake.udf;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF or UDTF whose body is a query is part of the calling query, not a statement of its own: Snowflake
 * inlines the body, which takes no query ID. The engine ran each call's body as a recorded statement, so
 * {@code SELECT f()} moved LAST_QUERY_ID onto the body's result, and a UDF called per row added a QUERY_HISTORY row
 * per row and crowded real results out of the result cache.
 */
public class SqlUdfBodyIsNoStatementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE udf_db");
        engine.execute("USE SCHEMA udf_db.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        engine.execute("CREATE FUNCTION fq() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t'");
        engine.execute("CREATE FUNCTION fqp(m NUMBER) RETURNS NUMBER AS 'SELECT m + 1'");
        engine.execute("CREATE FUNCTION tf(k NUMBER) RETURNS TABLE (x NUMBER) AS 'SELECT a * k FROM t'");
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

    /** What the statement before the last one returned. */
    private String beforeLast() {
        return first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))");
    }

    @Test
    public void aCallLeavesLastQueryIdOnTheCallersStatements() {
        engine.executeQuery("SELECT 'mark' AS c");
        assertEquals("3", first("SELECT fq() AS n"));
        assertEquals("mark", beforeLast(), "a scalar UDF's query body");

        engine.executeQuery("SELECT 'mark' AS c");
        assertEquals("10", first("SELECT * FROM TABLE(tf(10)) ORDER BY x"));
        assertEquals("mark", beforeLast(), "a UDTF's query body");

        // Read inside the block: EXECUTE IMMEDIATE takes query IDs of its own, so the statement before it is
        // further back than LAST_QUERY_ID(-2) outside.
        assertEquals("mark-if", first("EXECUTE IMMEDIATE $$ DECLARE r VARCHAR DEFAULT ''; BEGIN"
            + " SELECT 'mark-if' AS c; IF (fq() > 0) THEN r := 'x'; END IF;"
            + " LET res RESULTSET := (SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))); RETURN TABLE(res); END; $$"),
            "a scripting condition calling one");
    }

    @Test
    public void aCallPerRowAddsNoHistoryRows() {
        final long before = Long.parseLong(first(
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())"));
        assertEquals("9", first("SELECT SUM(fqp(a)) FROM t WHERE a < fq() + 10"));
        final long after = Long.parseLong(first(
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())"));
        assertEquals(before + 2, after, "the first count and the SELECT, and nothing per row");
    }
}
