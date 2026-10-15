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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream is bound to its table, not to the table's name, and its source resolves where the stream lives. The
 * engine found a stream's table by name, and that name was resolved against the reading session's schema. A rename
 * broke the stream, a new table under the old name silently re-bound it, a swap moved it to the other table, and a
 * read from another schema found another table or none. Snowflake documents that "Renaming a source object does not
 * break a stream", and that a new object created under a dropped one's name is not linked to the old object's streams.
 */
public class StreamFollowsItsTableTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE sd");
        engine.execute("USE SCHEMA sd.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A query's rows, each one's values joined by '|', and the rows joined by " ; ". */
    private String rows(final String sql) {
        return rows(engine, sql);
    }

    private static String rows(final DatabaseEngine on, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : on.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** One column of SHOW STREAMS for the named stream of the current schema. */
    private String shown(final String stream, final String column) {
        return shown(stream, column, "");
    }

    private String shown(final String stream, final String column, final String scope) {
        final ResultSet listed = engine.executeQuery("SHOW STREAMS LIKE '" + stream + "'" + scope);
        final int index = listed.getColumnIndex(column);
        return String.valueOf(listed.getRows().get(0).getValue(index));
    }

    @Test
    public void aRenamedTableKeepsItsStreamAndANewTableOfTheOldNameIsNotLinked() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE STREAM st ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("ALTER TABLE t RENAME TO t2");
        engine.execute("INSERT INTO t2 VALUES (2)");
        assertEquals("1|INSERT ; 2|INSERT", rows("SELECT a, METADATA$ACTION FROM st ORDER BY a"));
        assertEquals("SD.PUBLIC.T2", shown("ST", "table_name"));

        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (3)");
        assertEquals("1|INSERT ; 2|INSERT", rows("SELECT a, METADATA$ACTION FROM st ORDER BY a"),
            "the new T feeds nothing");

        engine.execute("CREATE SCHEMA moved");
        engine.execute("USE SCHEMA sd.PUBLIC");
        engine.execute("ALTER TABLE t2 RENAME TO sd.moved.t3");
        engine.execute("INSERT INTO sd.moved.t3 VALUES (4)");
        assertEquals("1 ; 2 ; 4", rows("SELECT a FROM st ORDER BY a"), "a move keeps it too");
        assertEquals("SD.MOVED.T3", shown("ST", "table_name"));
    }

    @Test
    public void aDroppedOrReplacedTableLeavesItsStreamStaleUntilUndropped() {
        engine.execute("CREATE TABLE d (x STRING)");
        engine.execute("CREATE STREAM sdr ON TABLE d");
        engine.execute("INSERT INTO d VALUES ('old')");
        engine.execute("DROP TABLE d");
        engine.execute("CREATE TABLE d (x STRING)");
        engine.execute("INSERT INTO d VALUES ('new')");
        assertEquals("Base table 'SD.PUBLIC.D' dropped, cannot read from stream 'SDR' in line 1 position 14.",
            refusal("SELECT x FROM sdr"));
        assertEquals("true", shown("SDR", "stale"));

        engine.execute("CREATE TABLE keep (x STRING)");
        engine.execute("CREATE STREAM sk ON TABLE keep");
        engine.execute("INSERT INTO keep VALUES ('kept')");
        engine.execute("DROP TABLE keep");
        assertTrue(refusal("SELECT x FROM sk").contains("dropped, cannot read from stream 'SK'"));
        engine.execute("UNDROP TABLE keep");
        assertEquals("kept", rows("SELECT x FROM sk"), "the undropped table is the stream's own again");
        assertEquals("false", shown("SK", "stale"));

        engine.execute("CREATE OR REPLACE TABLE keep (x STRING)");
        assertTrue(refusal("SELECT x FROM sk").contains("dropped, cannot read from stream 'SK'"),
            "CREATE OR REPLACE makes the stream stale");
    }

    @Test
    public void aSwappedTableKeepsItsStream() {
        engine.execute("CREATE TABLE sa (v STRING)");
        engine.execute("CREATE TABLE sb (v STRING)");
        engine.execute("CREATE STREAM ssa ON TABLE sa");
        engine.execute("ALTER TABLE sa SWAP WITH sb");
        engine.execute("INSERT INTO sb VALUES ('original sa')");
        engine.execute("INSERT INTO sa VALUES ('original sb')");
        assertEquals("original sa", rows("SELECT v FROM ssa"));
        assertEquals("SD.PUBLIC.SB", shown("SSA", "table_name"));
    }

    @Test
    public void aStreamReadsItsOwnTableFromAnotherSchema() {
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("USE SCHEMA d1.s1");
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("CREATE STREAM str ON TABLE src");
        engine.execute("INSERT INTO src VALUES (7)");
        engine.execute("USE SCHEMA sd.PUBLIC");
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("INSERT INTO src VALUES (99)");
        assertEquals("7|INSERT", rows("SELECT a, METADATA$ACTION FROM d1.s1.str"));
    }

    /** The source is named as the CREATE resolved it, from the session, wherever the stream itself goes. */
    @Test
    public void aStreamCreatedInAnotherSchemaReadsTheTableItsCreateNamed() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("CREATE TABLE other.t (a INT)");
        engine.execute("USE SCHEMA sd.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE STREAM other.st ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("INSERT INTO other.t VALUES (2)");
        assertEquals("1", rows("SELECT a FROM other.st"));
        assertEquals("SD.PUBLIC.T", shown("ST", "table_name", " IN SCHEMA sd.other"));

        engine.execute("CREATE STREAM other.st2 CLONE other.st");
        engine.execute("INSERT INTO t VALUES (3)");
        assertEquals("1 ; 3", rows("SELECT a FROM other.st2 ORDER BY a"), "a clone reads the same table");
    }

    @Test
    public void aRenamedSchemaOrDatabaseKeepsItsStreams() {
        engine.execute("CREATE SCHEMA src_s");
        engine.execute("CREATE TABLE src_s.t (a INT)");
        engine.execute("CREATE SCHEMA str_s");
        engine.execute("USE SCHEMA sd.str_s");
        engine.execute("CREATE STREAM st ON TABLE src_s.t");
        engine.execute("ALTER SCHEMA sd.src_s RENAME TO src_s2");
        engine.execute("INSERT INTO sd.src_s2.t VALUES (1)");
        assertEquals("1", rows("SELECT a FROM sd.str_s.st"));
        engine.execute("ALTER DATABASE sd RENAME TO sd2");
        engine.execute("INSERT INTO sd2.src_s2.t VALUES (2)");
        assertEquals("1 ; 2", rows("SELECT a FROM sd2.str_s.st ORDER BY a"));
    }

    @Test
    public void aRestoredEngineKeepsTheBindingAndTheStaleness() throws Exception {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE STREAM st ON TABLE t");
        engine.execute("ALTER TABLE t RENAME TO t2");
        engine.execute("CREATE TABLE gone (a INT)");
        engine.execute("CREATE STREAM sg ON TABLE gone");
        engine.execute("DROP TABLE gone");
        engine.execute("CREATE TABLE gone (a INT)");
        final DatabaseEngine restored = engine.cloneInstance();
        try {
            restored.execute("USE SCHEMA sd.PUBLIC");
            restored.execute("INSERT INTO t2 VALUES (5)");
            restored.execute("INSERT INTO gone VALUES (6)");
            assertEquals("5", rows(restored, "SELECT a FROM st"));
            try {
                restored.executeQuery("SELECT a FROM sg");
                throw new AssertionError("a stale stream must stay stale across a restore");
            } catch (final RuntimeException refused) {
                assertTrue(String.valueOf(refused.getMessage()).contains("cannot read from stream 'SG'"));
            }
        } finally {
            restored.shutdown();
        }
    }
}
