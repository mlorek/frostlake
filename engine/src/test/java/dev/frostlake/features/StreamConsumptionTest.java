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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake stream-consumption semantics: a stream is queryable as a table source, {@code SELECT} reads it
 * NON-destructively, a consuming DML ({@code INSERT … SELECT FROM stream}) advances the offset, and
 * {@code SYSTEM$STREAM_HAS_DATA} reflects the unconsumed count. (Before the fix, streams weren't queryable,
 * no SQL ever consumed them, and {@code SYSTEM$STREAM_HAS_DATA} always returned false.)
 */
public class StreamConsumptionTest extends BaseDatabaseTest {

    private long scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    private boolean hasData(final String stream) {
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('" + stream + "')");
        return Boolean.parseBoolean(String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void selectFromStreamIsNonDestructiveAndExposesMetadata() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1,'a'),(2,'b')");

        // SELECT reads the change rows, with the change-tracking metadata columns appended (via SELECT *).
        final ResultSet rs = engine.executeQuery("SELECT * FROM s");
        assertEquals(2, rs.getRowCount());
        final Row first = rs.getRows().get(0);
        assertEquals(5, first.getValues().size());                  // id, name + METADATA$ACTION/ISUPDATE/ROW_ID
        assertEquals("INSERT", String.valueOf(first.getValue(2)));  // METADATA$ACTION (positional)
        // ...and referenceable BY NAME now that '$' is allowed in identifiers.
        final ResultSet byName = engine.executeQuery("SELECT METADATA$ACTION FROM s WHERE id = 1");
        assertEquals("INSERT", String.valueOf(byName.getRows().get(0).getValue(0)));

        // Reading is non-destructive: the offset does not move, so repeated reads see the same data.
        assertEquals(2, scalar("SELECT COUNT(*) FROM s"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM s"));
        assertTrue(hasData("s"));
    }

    @Test
    public void consumingDmlAdvancesTheOffset() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1,'a'),(2,'b')");
        engine.execute("CREATE TABLE sink (id INTEGER, name VARCHAR)");
        assertTrue(hasData("s"));

        // Consuming DML: read the stream into another table -> offset advances.
        engine.execute("INSERT INTO sink SELECT id, name FROM s");
        assertEquals(2, scalar("SELECT COUNT(*) FROM sink"));
        assertFalse(hasData("s"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM s"));

        // A new change after consumption shows up again (offset advanced, not reset to zero base).
        engine.execute("INSERT INTO src VALUES (3,'c')");
        assertTrue(hasData("s"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM s"));
    }

    @Test
    public void plainSelectDoesNotConsume() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1),(2)");

        engine.executeQuery("SELECT * FROM s");        // plain SELECT
        engine.executeQuery("SELECT COUNT(*) FROM s"); // and again

        assertTrue(hasData("s"));                       // still unconsumed
        assertEquals(2, scalar("SELECT COUNT(*) FROM s"));
    }

    @Test
    public void hasDataReflectsUnconsumedState() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM s ON TABLE src");
        assertFalse(hasData("s"));                      // empty stream -> no data
        engine.execute("INSERT INTO src VALUES (1)");
        assertTrue(hasData("s"));                        // has unconsumed data
    }

    @Test
    public void mergeUsingStreamConsumes() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1,'a'),(2,'b')");
        engine.execute("CREATE TABLE tgt (id INTEGER, name VARCHAR)");
        assertTrue(hasData("s"));

        engine.execute("MERGE INTO tgt USING s ON tgt.id = s.id "
            + "WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)");
        assertEquals(2, scalar("SELECT COUNT(*) FROM tgt"));
        assertFalse(hasData("s"));                       // the MERGE consumed the stream
    }

    @Test
    public void explicitTransactionCommitConsumes() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1),(2)");
        engine.execute("CREATE TABLE sink (id INTEGER)");

        engine.execute("BEGIN");
        engine.execute("INSERT INTO sink SELECT id FROM s");
        assertTrue(hasData("s"));                        // read but not yet committed -> not consumed
        engine.execute("COMMIT");
        assertFalse(hasData("s"));                       // consumed on COMMIT
    }

    @Test
    public void explicitTransactionRollbackDoesNotConsume() {
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1),(2)");
        engine.execute("CREATE TABLE sink (id INTEGER)");

        engine.execute("BEGIN");
        engine.execute("INSERT INTO sink SELECT id FROM s");
        engine.execute("ROLLBACK");
        assertTrue(hasData("s"));                        // rolled back -> NOT consumed
        assertEquals(2, scalar("SELECT COUNT(*) FROM s"));
    }

    @Test
    public void ctasFromStreamConsumes() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM s ON TABLE src");
        engine.execute("INSERT INTO src VALUES (1,'a'),(2,'b')");
        assertTrue(hasData("s"));

        engine.execute("CREATE TABLE snapshot AS SELECT id, name FROM s");
        assertEquals(2, scalar("SELECT COUNT(*) FROM snapshot"));
        assertFalse(hasData("s"));                       // CTAS consumed the stream
    }
}
