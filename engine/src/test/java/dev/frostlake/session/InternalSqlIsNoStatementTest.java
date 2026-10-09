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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * SQL the engine composes to carry out a statement is that statement's own work, not one of the session's: it takes
 * no query ID and adds no history row. Each of these ran its internal SQL as a recorded statement, so LAST_QUERY_ID
 * moved onto it and QUERY_HISTORY listed it:
 * <ul>
 *   <li>CREATE VIEW, CREATE FUNCTION, EXPLAIN and DESCRIBE DYNAMIC TABLE compiling their bodies;</li>
 *   <li>a SET value, a CALL argument and CALL SYSTEM$..., each evaluated by a SELECT;</li>
 *   <li>TO_QUERY's text, a JOIN view's stream, and USING TEMPLATE's query and rewritten CREATE;</li>
 *   <li>an EXECUTE IMMEDIATE of a block, whose answer took a query ID of its own beside the statement's.</li>
 * </ul>
 */
public class InternalSqlIsNoStatementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ins_db");
        engine.execute("USE SCHEMA ins_db.PUBLIC");
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

    private long historyRows() {
        return Long.parseLong(first(
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 10000))"));
    }

    /**
     * Runs {@code statement} after a marker SELECT, and answers what RESULT_SCAN(LAST_QUERY_ID(-2)) reads then and
     * how many history rows the statement added: the marker and one row when the statement is one statement.
     */
    private String afterMarker(final String statement) {
        final long before = historyRows();
        engine.executeQuery("SELECT 'mark'");
        try {
            engine.execute(statement);
        } catch (final RuntimeException expected) {
            // A statement that fails is still one statement; what follows checks it took one row and one ID.
        }
        final String previous = first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))");
        // The count above and the marker each added a row, and so did the RESULT_SCAN; the statement adds one.
        return previous + " / " + (historyRows() - before - 3);
    }

    @Test
    public void bodiesCompileWithoutStatementsOfTheirOwn() {
        assertEquals("mark / 1", afterMarker("CREATE VIEW v AS SELECT a, a * 2 AS b FROM t"));
        assertEquals("mark / 1", afterMarker("CREATE SECURE VIEW sv AS SELECT a FROM t"));
        assertEquals("mark / 1", afterMarker("CREATE MATERIALIZED VIEW mv AS SELECT a FROM t"));
        assertEquals("mark / 1", afterMarker("CREATE FUNCTION fqp(m INT) RETURNS NUMBER AS 'SELECT MAX(a) FROM t WHERE a < m'"));
        assertEquals("mark / 1", afterMarker("CREATE FUNCTION tf(k INT) RETURNS TABLE (x INT) AS 'SELECT a * k FROM t'"));
        assertEquals("mark / 1", afterMarker("EXPLAIN SELECT a FROM t"));
        assertEquals("mark / 1", afterMarker(
            "CREATE DYNAMIC TABLE dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM t"));
        assertEquals("mark / 1", afterMarker("DESCRIBE DYNAMIC TABLE dt"));
        assertEquals("mark / 1", afterMarker("CREATE VIEW broken AS SELECT a FROM nosuch"),
            "a refused CREATE VIEW is one failed statement");
    }

    @Test
    public void setValuesAndCallArgumentsBelongToTheirStatement() {
        engine.execute("CREATE PROCEDURE p(s STRING) RETURNS STRING LANGUAGE SQL AS $$ BEGIN RETURN s; END; $$");
        assertEquals("mark / 1", afterMarker("SET v1 = UPPER('a')"));
        assertEquals("mark / 1", afterMarker("SET v2 = (SELECT 1)"));
        assertEquals("mark / 1", afterMarker("CALL p(UPPER('b'))"));
        assertEquals("mark / 1", afterMarker("CALL SYSTEM$WAIT(0)"));
        assertEquals("A", first("SELECT $v1"));
        // Inside a block the SET is one child statement, as it is sent on its own: its value takes no query ID, so
        // the one before the SET's own is the SELECT's.
        assertEquals("inner4", first("EXECUTE IMMEDIATE $$ BEGIN SELECT 'inner4'; SET sv = UPPER('zz');"
            + " LET r RESULTSET := (SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))); RETURN TABLE(r); END; $$"),
            "inside a block, LAST_QUERY_ID(-2) after the SET reads the SELECT before it");
    }

    @Test
    public void toQueryJoinViewStreamsAndTemplatesAreOneStatement() throws IOException {
        assertEquals("mark / 1", afterMarker("SELECT * FROM TABLE(TO_QUERY('SELECT a FROM t'))"));

        engine.execute("CREATE TABLE u (a INT, b INT)");
        engine.execute("CREATE VIEW jv AS SELECT t.a, u.b FROM t JOIN u ON t.a = u.a");
        engine.execute("CREATE STREAM sjv ON VIEW jv");
        engine.execute("INSERT INTO u VALUES (1, 10)");
        assertEquals("mark / 1", afterMarker("SELECT a, b, METADATA$ACTION FROM sjv"));
        assertEquals("1", first("SELECT COUNT(*) FROM sjv"), "the stream still reads the joined row");

        // A template query reads INFER_SCHEMA, so a file is staged for it to read.
        final Path file = Files.createTempDirectory("fl_internal_tpl").resolve("one.csv");
        Files.writeString(file, "id\n1\n");
        engine.execute("CREATE STAGE tpl_st");
        engine.execute("CREATE FILE FORMAT tpl_ff TYPE = CSV PARSE_HEADER = TRUE");
        engine.execute("PUT file://" + file.toAbsolutePath() + " @tpl_st AUTO_COMPRESS=FALSE");
        assertEquals("mark / 1", afterMarker("CREATE TABLE tpl USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*))"
            + " FROM TABLE(INFER_SCHEMA(LOCATION => '@tpl_st', FILE_FORMAT => 'tpl_ff')))"));
        assertEquals("id", first("SELECT column_name FROM information_schema.columns WHERE table_name = 'TPL'"),
            "the template's name as written");
    }

    @Test
    public void anExecuteImmediateOfABlockTakesOneQueryIdAsTheBlockDoes() {
        assertEquals("mark / 1", afterMarker("DECLARE x INT DEFAULT 7; BEGIN RETURN x; END;"));
        assertEquals("mark / 1", afterMarker("EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 7; BEGIN RETURN x; END; $$"));
        assertEquals("7", first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-3)))"),
            "the EXECUTE IMMEDIATE's own ID reads the block's answer");
    }

    /** One engine serves sessions on several threads at once; what one of them runs unrecorded is its own business. */
    @Test
    public void unrecordedSqlOnOneThreadLeavesAnotherThreadsStatementsRecorded() throws InterruptedException {
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final Thread other = new Thread(new Runnable() {
            @Override
            public void run() {
                engine.getExecutor().runUnrecorded(new Runnable() {
                    @Override
                    public void run() {
                        inside.countDown();
                        try {
                            release.await();
                        } catch (final InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        });
        other.start();
        inside.await();
        try {
            assertFalse(engine.getExecutor().isUnrecorded());
            final long before = historyRows();
            engine.executeQuery("SELECT 'recorded'");
            assertEquals(before + 2, historyRows(), "this thread's statements are still recorded");
        } finally {
            release.countDown();
            other.join();
        }
    }
}
