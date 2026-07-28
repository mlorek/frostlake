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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake: every DDL statement implicitly commits the open transaction before running — INSIDE a
 * stored procedure's {@code BEGIN…END} body just as at top level. The pre-commit used to live only in
 * the string entry point's statement loop, which a procedural body bypasses (it dispatches statements
 * through the visitor), so a mid-procedure {@code CREATE TEMP TABLE} left the whole-CALL transaction
 * open — and the common flush-the-stream-then-load idiom silently mis-scoped its stream windows. The
 * pre-commit now lives in {@code visitDdlStatement}, the dispatch point every path shares.
 */
public class DdlAutocommitInProcedureTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    @Test
    public void aMidProcedureDdlCommitsWorkBufferedBeforeIt() {
        engine.execute("CREATE TABLE t (id INT)");
        // Each statement of a procedure runs in its own autocommit transaction (Snowflake): both
        // inserts are committed the moment they complete — the DDL between them also commits, and the
        // later RAISE fails only the procedure, never the already-committed statements before it.
        engine.execute("""
            CREATE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE boom EXCEPTION (-20001, 'boom');
            BEGIN
              INSERT INTO t VALUES (1);
              CREATE OR REPLACE TEMP TABLE scratch (x INT);
              INSERT INTO t VALUES (2);
              RAISE boom;
            END $$""");
        try {
            engine.execute("CALL p()");
        } catch (final RuntimeException expected) {
            // the CALL fails; what matters is which inserts survived
        }
        assertEquals(1, count("SELECT id FROM t WHERE id = 1"));   // committed at its statement end
        assertEquals(1, count("SELECT id FROM t WHERE id = 2"));   // likewise — a later RAISE cannot undo it
    }

    @Test
    public void topLevelDdlStillCommitsAnExplicitTransaction() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE TABLE other (x INT)");   // DDL commits the open transaction
        engine.execute("ROLLBACK");                      // nothing left to roll back
        assertEquals(1, count("SELECT id FROM t"));
    }
}
