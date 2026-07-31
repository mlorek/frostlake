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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream read INSIDE a transaction must reflect that transaction's own not-yet-committed DML to the
 * stream's base table — the deferred-apply equivalent of read-your-writes. A stored procedure relies on
 * this when it inserts into a table and then reads a stream over that table in the same (proc) transaction:
 * without it the stream reads empty and the downstream load silently loads nothing. A rollback, which just
 * discards the write set, must leave the stream unchanged.
 */
public class StreamInTransactionTest extends BaseDatabaseTest {

    private static final String SAME_TRANSACTION_READ =
        "deliberate, documented model divergence: Frostlake lets a stream read see its own transaction's "
        + "uncommitted DML (read-your-writes), while Snowflake pins a stream read to the transaction's "
        + "start time and therefore returns nothing for changes made inside it";

    private long streamCount(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    @Test
    public void appendOnlyStreamSeesSameTransactionInserts() {
        Assumptions.assumeFalse(isLiveSnowflake(), SAME_TRANSACTION_READ);
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=TRUE");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        assertEquals(2, streamCount("SELECT * FROM s"));   // buffered inserts visible before COMMIT
        engine.execute("COMMIT");
        assertEquals(2, streamCount("SELECT * FROM s"));
    }

    @Test
    public void streamAfterFlushSeesOnlyNewSameTransactionInserts() {
        Assumptions.assumeFalse(isLiveSnowflake(), SAME_TRANSACTION_READ);
        // The loader shape: seed + flush the stream, then insert more in a transaction and read.
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=TRUE");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE TEMP TABLE reset AS SELECT * FROM s WHERE 1=0");  // flush past row 1
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO t VALUES (2), (3)");
        assertEquals(2, streamCount("SELECT * FROM s"));   // only the new 2,3, not the flushed 1
        engine.execute("COMMIT");
    }

    @Test
    public void rollbackDiscardsBufferedStreamChanges() {
        Assumptions.assumeFalse(isLiveSnowflake(), SAME_TRANSACTION_READ);
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=TRUE");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        assertEquals(2, streamCount("SELECT * FROM s"));
        engine.execute("ROLLBACK");
        assertEquals(0, streamCount("SELECT * FROM s"));   // discarded — nothing was committed
    }

    @Test
    public void standardStreamSeesSameTransactionUpdateAndDelete() {
        Assumptions.assumeFalse(isLiveSnowflake(), SAME_TRANSACTION_READ);
        engine.execute("CREATE TABLE t (id INT, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE STREAM s ON TABLE t");   // standard (not APPEND_ONLY), no initial rows
        engine.execute("BEGIN TRANSACTION");
        engine.execute("UPDATE t SET v = 'z' WHERE id = 1");
        engine.execute("DELETE FROM t WHERE id = 2");
        // The UPDATE nets to a DELETE(old)+INSERT(new) pair and the DELETE to one DELETE → 3 change rows.
        assertEquals(3, streamCount("SELECT * FROM s"));
        engine.execute("COMMIT");
    }

    @Test
    public void viewStreamSeesSameTransactionInsertsToBothBranches() {
        Assumptions.assumeFalse(isLiveSnowflake(), SAME_TRANSACTION_READ);
        engine.execute("CREATE TABLE active (id INT, st VARCHAR)");
        engine.execute("CREATE TABLE del (id INT, st VARCHAR)");
        engine.execute("CREATE VIEW v AS SELECT id, st FROM active UNION ALL SELECT id, 'DELETE' AS st FROM del");
        engine.execute("CREATE STREAM vs ON VIEW v APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=TRUE");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO active VALUES (1, 'ACTIVE'), (2, 'ACTIVE')");   // branch 1 (buffered)
        engine.execute("INSERT INTO del VALUES (1, 'DELETE')");                     // branch 2 (buffered)
        assertEquals(3, streamCount("SELECT * FROM vs"));
        // Each branch's rows route correctly (the DELETE branch surfaces its one change).
        assertEquals(1, streamCount("SELECT * FROM vs WHERE st = 'DELETE'"));
        engine.execute("COMMIT");
    }

    @Test
    public void crossSchemaTableStreamCapturesCommittedChanges() {
        // A stream very often lives in a different schema from the table it reads (a BASE_TRANSFORM stream ON
        // an INGEST table). Change tracking only searched the MUTATED table's own schema for streams, so such
        // a stream never saw a committed INSERT/UPDATE/DELETE at all — and rows that were visible mid
        // transaction disappeared again at COMMIT.
        engine.execute("CREATE SCHEMA src_schema");
        engine.execute("CREATE TABLE src_schema.raw (id INTEGER)");
        engine.execute("CREATE STREAM s_cross ON TABLE src_schema.raw APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=FALSE");
        engine.execute("INSERT INTO src_schema.raw VALUES (1), (2)");
        assertEquals(2, streamCount("SELECT * FROM s_cross"));
        engine.execute("INSERT INTO src_schema.raw VALUES (3)");
        assertEquals(3, streamCount("SELECT * FROM s_cross"));
    }

    @Test
    public void aStreamDoesNotCaptureASameNamedTableInAnotherSchema() {
        // Widening the search must stay exact: two schemas with a same-named table must not cross-capture.
        engine.execute("CREATE SCHEMA schema_a");
        engine.execute("CREATE SCHEMA schema_b");
        engine.execute("CREATE TABLE schema_a.t (id INTEGER)");
        engine.execute("CREATE TABLE schema_b.t (id INTEGER)");
        engine.execute("CREATE STREAM s_a ON TABLE schema_a.t APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=FALSE");
        engine.execute("INSERT INTO schema_b.t VALUES (1), (2)");   // the OTHER schema's table
        assertEquals(0, streamCount("SELECT * FROM s_a"));
        engine.execute("INSERT INTO schema_a.t VALUES (9)");
        assertEquals(1, streamCount("SELECT * FROM s_a"));
    }

    @Test
    public void crossSchemaViewStreamSeesSameTransactionInserts() {
        Assumptions.assumeFalse(isLiveSnowflake(), SAME_TRANSACTION_READ);
        // The loader shape: a view (and its stream) in one schema over base tables in ANOTHER schema, all
        // driven inside one stored-proc transaction. The write-set match is by bare table name, not schema.
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_schema.active (id INT, st VARCHAR)");
        engine.execute("CREATE TABLE other_schema.del (id INT, st VARCHAR)");
        engine.execute("CREATE VIEW v AS SELECT id, st FROM other_schema.active "
            + "UNION ALL SELECT id, 'DELETE' AS st FROM other_schema.del");
        engine.execute("CREATE STREAM vs ON VIEW v APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=TRUE");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO other_schema.active VALUES (1, 'ACTIVE'), (2, 'ACTIVE')");
        engine.execute("INSERT INTO other_schema.del VALUES (1, 'DELETE')");
        assertEquals(3, streamCount("SELECT * FROM vs"));
        assertEquals(1, streamCount("SELECT * FROM vs WHERE st = 'DELETE'"));
        engine.execute("COMMIT");
    }
}
