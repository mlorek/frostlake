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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SELECT from a stream returns the NET delta per logical row (Snowflake semantics), not the raw
 * change log: a row inserted then updated inside the window is one INSERT of its final image
 * (ISUPDATE false); inserted then deleted cancels out; a pre-existing row updated (repeatedly)
 * nets to one DELETE(original)+INSERT(final) pair (ISUPDATE true); updated then deleted nets to a
 * plain DELETE of the original image. Offset semantics (DML consumption, rollback restore,
 * CREATE OR REPLACE reset) and APPEND_ONLY filtering are covered alongside.
 */
public class StreamNetDeltaTest extends BaseDatabaseTest {

    private ResultSet run(final String sql) {
        return engine.executeQuery(sql);
    }

    // ── net-delta consolidation ────────────────────────────────────────────────────────────────────

    @Test
    public void insertsCapturedWithMetadata() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1, 'a'), (2, 'b')");
        final ResultSet rs = run("SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM st ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals("INSERT", rs.getRows().get(0).getValue(1));
        assertEquals(Boolean.FALSE, rs.getRows().get(0).getValue(2));
    }

    @Test
    public void preexistingUpdateProducesPair() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'old')");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("UPDATE src SET v = 'new' WHERE id = 1");
        final ResultSet rs = run(
            "SELECT v, METADATA$ACTION, METADATA$ISUPDATE FROM st ORDER BY METADATA$ACTION");
        assertEquals(2, rs.getRowCount());
        assertEquals("old", rs.getRows().get(0).getValue(0));
        assertEquals("DELETE", rs.getRows().get(0).getValue(1));
        assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(2));
        assertEquals("new", rs.getRows().get(1).getValue(0));
        assertEquals("INSERT", rs.getRows().get(1).getValue(1));
        assertEquals(Boolean.TRUE, rs.getRows().get(1).getValue(2));
    }

    @Test
    public void insertThenUpdateNetsToSingleInsert() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1, 'a')");
        engine.execute("UPDATE src SET v = 'a2' WHERE id = 1");
        final ResultSet rs = run("SELECT v, METADATA$ACTION, METADATA$ISUPDATE FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals("a2", rs.getRows().get(0).getValue(0));
        assertEquals("INSERT", rs.getRows().get(0).getValue(1));
        assertEquals(Boolean.FALSE, rs.getRows().get(0).getValue(2));
    }

    @Test
    public void insertThenDeleteCancelsOut() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("DELETE FROM src WHERE id = 1");
        assertEquals(0, run("SELECT id FROM st").getRowCount());
    }

    @Test
    public void multiUpdateCollapsesToOnePair() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'v0')");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("UPDATE src SET v = 'v1' WHERE id = 1");
        engine.execute("UPDATE src SET v = 'v2' WHERE id = 1");
        final ResultSet rs = run(
            "SELECT v, METADATA$ACTION FROM st ORDER BY METADATA$ACTION");
        assertEquals(2, rs.getRowCount());
        assertEquals("v0", rs.getRows().get(0).getValue(0)); // DELETE of the ORIGINAL image
        assertEquals("DELETE", rs.getRows().get(0).getValue(1));
        assertEquals("v2", rs.getRows().get(1).getValue(0)); // INSERT of the FINAL image
        assertEquals("INSERT", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void updateThenDeleteNetsToDeleteOfOriginal() {
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'orig')");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("UPDATE src SET v = 'mid' WHERE id = 1");
        engine.execute("DELETE FROM src WHERE id = 1");
        final ResultSet rs = run("SELECT v, METADATA$ACTION, METADATA$ISUPDATE FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals("orig", rs.getRows().get(0).getValue(0));
        assertEquals("DELETE", rs.getRows().get(0).getValue(1));
        assertEquals(Boolean.FALSE, rs.getRows().get(0).getValue(2));
    }

    // APPEND_ONLY records only true inserts — a later delete does not remove the captured insert.
    @Test
    public void appendOnlyKeepsInsertAfterDelete() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE src APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("DELETE FROM src WHERE id = 1");
        final ResultSet rs = run("SELECT id, METADATA$ACTION FROM st");
        assertEquals(1, rs.getRowCount());
        assertEquals("INSERT", rs.getRows().get(0).getValue(1));
    }

    // ── offset semantics ───────────────────────────────────────────────────────────────────────────

    @Test
    public void plainSelectDoesNotConsumeButDmlDoes() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE TABLE dst (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1), (2)");
        assertEquals(2, run("SELECT id FROM st").getRowCount());
        assertEquals(2, run("SELECT id FROM st").getRowCount()); // plain SELECT: not consumed
        engine.execute("INSERT INTO dst SELECT id FROM st");     // DML read: consumes on commit
        assertEquals(0, run("SELECT id FROM st").getRowCount());
        engine.execute("INSERT INTO src VALUES (3)");            // new changes accrue after the offset
        assertEquals(1, run("SELECT id FROM st").getRowCount());
    }

    @Test
    public void rollbackRestoresStreamOffset() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE TABLE dst (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO dst SELECT id FROM st");
        engine.execute("ROLLBACK");
        assertEquals(1, run("SELECT id FROM st").getRowCount());
    }

    @Test
    public void createOrReplaceResetsOffset() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM st ON TABLE src");
        assertEquals(0, run("SELECT id FROM st").getRowCount());
    }

    // SHOW_INITIAL_ROWS seeds the stream with the table's existing rows as INSERTs, after which
    // consumption and new-change accrual behave like any other stream.
    @Test
    public void showInitialRowsSeedsExistingRows() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE TABLE dst (id INTEGER)");
        engine.execute("INSERT INTO src VALUES (1), (2)");
        engine.execute("CREATE STREAM st ON TABLE src SHOW_INITIAL_ROWS = TRUE");
        final ResultSet rs = run("SELECT id, METADATA$ACTION FROM st ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals("INSERT", rs.getRows().get(0).getValue(1));
        engine.execute("INSERT INTO dst SELECT id FROM st"); // consume the initial rows
        assertEquals(0, run("SELECT id FROM st").getRowCount());
        engine.execute("INSERT INTO src VALUES (3)");
        assertEquals(1, run("SELECT id FROM st").getRowCount());
    }

    // SHOW STREAMS reflects the stream's actual mode (was hardcoded to DEFAULT).
    @Test
    public void showStreamsReflectsMode() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM st_std ON TABLE src");
        engine.execute("CREATE STREAM st_ao ON TABLE src APPEND_ONLY = TRUE");
        final ResultSet rs = run("SHOW STREAMS");
        assertEquals("DEFAULT", streamMode(rs, "st_std"));
        assertEquals("APPEND_ONLY", streamMode(rs, "st_ao"));
    }

    private String streamMode(final ResultSet rs, final String streamName) {
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (streamName.equalsIgnoreCase(String.valueOf(rs.getRows().get(i).getValue(1)))) {
                return String.valueOf(rs.getRows().get(i).getValue(rs.getColumnIndex("mode")));
            }
        }
        throw new AssertionError("Stream not found in SHOW STREAMS: " + streamName);
    }
}
