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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Statements inside a stored procedure each run in their OWN autocommit transaction (Snowflake) —
 * a procedure body does not wrap its statements into one implicit transaction. Three observable
 * consequences locked in here: an append-only stream keeps an INSERT even when a later statement
 * deletes the row (one shared transaction consolidated the pair away and the stream missed it); a
 * failed statement rolls back ONLY itself, keeping earlier statements' committed work; an explicit
 * BEGIN TRANSACTION inside the body still suspends autocommit until COMMIT.
 */
public class ProcStatementAutocommitTest extends BaseDatabaseTest {

    private String one(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void appendOnlyStreamKeepsInsertDeletedByLaterStatement() {
        engine.execute("CREATE TABLE ao_t (id INTEGER, v VARCHAR)");
        engine.execute("CREATE STREAM ao_s ON TABLE ao_t APPEND_ONLY = TRUE");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE stage_and_clear() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO ao_t VALUES (1, 'staged');
              DELETE FROM ao_t WHERE id = 1;
              RETURN 'done';
            END $$""");
        engine.execute("CALL stage_and_clear()");
        assertEquals("0", one("SELECT COUNT(*) FROM ao_t"));
        final ResultSet rs = engine.executeQuery("SELECT id, METADATA$ACTION FROM ao_s");
        assertEquals(1, rs.getRowCount(), "append-only stream must keep the committed INSERT");
        assertEquals("INSERT", String.valueOf(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void failedStatementKeepsEarlierStatementsCommitted() {
        engine.execute("CREATE TABLE pc_t (id INTEGER NOT NULL)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE partial_work() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO pc_t VALUES (1);
              INSERT INTO pc_t SELECT NULL;
              RETURN 'unreachable';
            EXCEPTION
              WHEN OTHER THEN
                RETURN 'handled';
            END $$""");
        assertEquals("handled", one("CALL partial_work()"));
        assertEquals("1", one("SELECT COUNT(*) FROM pc_t"),
            "the statement before the failure stays committed (Snowflake per-statement autocommit)");
    }

    @Test
    public void alterSessionDoesNotCommitAnOpenTransaction() {
        // Snowflake: ALTER SESSION is not transaction-committing DDL. Loaders set QUERY_TAG between
        // statements INSIDE their explicit transaction — if it committed, the transaction split and a
        // SECOND stream read within it saw an already-consumed empty window.
        //
        // At the TOP LEVEL, which is where a loader sets it: a real account refuses ALTER SESSION
        // inside a stored procedure outright ("Unsupported statement type 'ALTER_SESSION'"), so the
        // procedure form this once used could not have been what the loaders run.
        engine.execute("CREATE TABLE as_t (id INTEGER)");
        engine.execute("INSERT INTO as_t VALUES (1), (2)");
        engine.execute("CREATE STREAM as_s ON TABLE as_t");
        engine.execute("INSERT INTO as_t VALUES (3)");
        engine.execute("CREATE TABLE sink1 (id INTEGER)");
        engine.execute("CREATE TABLE sink2 (id INTEGER)");

        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO sink1 SELECT id FROM as_s");
        engine.execute("ALTER SESSION SET QUERY_TAG = 'mid-transaction'");
        engine.execute("INSERT INTO sink2 SELECT id FROM as_s");
        engine.execute("COMMIT");

        assertEquals("1", one("SELECT COUNT(*) FROM sink1"), "first read sees the streamed insert");
        assertEquals("1", one("SELECT COUNT(*) FROM sink2"),
            "ALTER SESSION must not commit — the second read shares the same stream window");
    }

    @Test
    public void identifierJoinSourceSeesSameTransactionWrites() {
        // The aggregation-loader shape: fill IDENTIFIER(:tmp) and JOIN it one statement later inside
        // the same explicit transaction — the IDENTIFIER read path did a raw scan, blind to the
        // write-set overlay, so the join silently saw an empty table.
        engine.execute("CREATE TABLE base_rows (grp_id VARCHAR, v INTEGER)");
        engine.execute("INSERT INTO base_rows VALUES ('c1', 1), ('c2', 2)");
        engine.execute("CREATE TABLE aff (grp_id VARCHAR)");
        engine.execute("CREATE TABLE joined_out (grp_id VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE fill_and_join() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              BEGIN TRANSACTION;
              INSERT INTO IDENTIFIER('aff') SELECT 'c1';
              INSERT INTO joined_out SELECT s.grp_id
                FROM base_rows s JOIN IDENTIFIER('aff') t ON t.grp_id = s.grp_id;
              COMMIT;
              RETURN 'done';
            END $$""");
        engine.execute("CALL fill_and_join()");
        assertEquals("1", one("SELECT COUNT(*) FROM joined_out"));
        assertEquals("c1", one("SELECT grp_id FROM joined_out"));
    }

    @Test
    public void explicitTransactionInsideProcStillSpansStatements() {
        engine.execute("CREATE TABLE tx_t (id INTEGER)");
        engine.execute("CREATE STREAM tx_s ON TABLE tx_t");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE tx_block() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              BEGIN TRANSACTION;
              INSERT INTO tx_t VALUES (1);
              DELETE FROM tx_t WHERE id = 1;
              COMMIT;
              RETURN 'done';
            END $$""");
        engine.execute("CALL tx_block()");
        assertEquals("0", one("SELECT COUNT(*) FROM tx_t"));
        // A STANDARD stream nets insert+delete of the same row inside one transaction to nothing.
        assertEquals(0, engine.executeQuery("SELECT * FROM tx_s").getRowCount());
    }
}
