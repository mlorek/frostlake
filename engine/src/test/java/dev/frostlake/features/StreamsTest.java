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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STREAM behavior, asserted through the SQL surface — {@code SHOW STREAMS} cells and reads of the
 * stream itself ({@code METADATA$ACTION} / {@code METADATA$ISUPDATE} per change row) — so every
 * check runs against whichever engine executed the DDL/DML, embedded or live. A stream is consumed
 * by a DML statement that reads it, not by a plain SELECT.
 */
public class StreamsTest extends BaseDatabaseTest {

    private long countOf(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void testCreateStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        final ResultSet streams = engine.executeQuery("SHOW STREAMS LIKE 'user_stream'");
        final Row stream = soleRowWhere(streams, "name", "USER_STREAM");
        assertTrue(cell(streams, stream, "table_name").endsWith("USERS"),
            cell(streams, stream, "table_name"));
        assertEquals("Table", cell(streams, stream, "source_type"));
        assertEquals("DEFAULT", cell(streams, stream, "mode"));
    }

    @Test
    public void testCreateAppendOnlyStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        final ResultSet streams = engine.executeQuery("SHOW STREAMS LIKE 'user_stream'");
        assertEquals("APPEND_ONLY", cell(streams, soleRowWhere(streams, "name", "USER_STREAM"), "mode"));
    }

    @Test
    public void testQualifiedStreamReferenceResolvesAcrossSchemas() {
        // A schema-qualified stream reference must resolve to that schema, not only the current one — a
        // procedure whose current schema differs still reads e.g. `other_db.some_stream`, which is how a
        // loader reads a stream from another schema context.
        engine.execute("CREATE TABLE test_schema.t (id INTEGER)");
        engine.execute("CREATE STREAM test_schema.strm ON TABLE test_schema.t");
        engine.execute("INSERT INTO test_schema.t VALUES (1), (2)");
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("USE SCHEMA other_schema");

        assertEquals(2L, countOf("SELECT COUNT(*) AS c FROM test_schema.strm"));
    }

    @Test
    public void testStreamReadViaIdentifierFunction() {
        // A loader commonly reads a stream via FROM IDENTIFIER(:name) to toggle a data source between a
        // base table (backfill) and a stream (incremental), so IDENTIFIER() must resolve streams too —
        // not only tables.
        engine.execute("CREATE TABLE test_schema.fact (id INTEGER)");
        engine.execute("CREATE STREAM test_schema.strm ON TABLE test_schema.fact");
        engine.execute("INSERT INTO test_schema.fact VALUES (1), (2), (3)");

        assertEquals(3L, countOf("SELECT COUNT(*) AS c FROM IDENTIFIER('test_schema.strm')"));
    }

    @Test
    public void testStreamTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM user_stream");
        assertEquals(2, delta.getRowCount());
        assertEquals(2, rowsWhere(delta, "METADATA$ACTION", "INSERT").size());
        assertEquals(0, rowsWhere(delta, "METADATA$ISUPDATE", "true").size());
    }

    @Test
    public void testStreamTracksUpdates() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("UPDATE users SET age = 31 WHERE id = 1");

        // An UPDATE reads as a DELETE of the old image plus an INSERT of the new one, both flagged
        // METADATA$ISUPDATE.
        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM user_stream");
        assertEquals(2, delta.getRowCount());
        final Row deleted = soleRowWhere(delta, "METADATA$ACTION", "DELETE");
        final Row inserted = soleRowWhere(delta, "METADATA$ACTION", "INSERT");
        assertEquals("true", cell(delta, deleted, "METADATA$ISUPDATE"));
        assertEquals("true", cell(delta, inserted, "METADATA$ISUPDATE"));
    }

    @Test
    public void testStreamTracksDeletes() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("DELETE FROM users WHERE id = 1");

        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM user_stream");
        assertEquals(1, delta.getRowCount());
        soleRowWhere(delta, "METADATA$ACTION", "DELETE");
    }

    @Test
    public void testAppendOnlyStreamOnlyTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        engine.execute("DELETE FROM users WHERE id = 1");

        final ResultSet delta = engine.executeQuery(
            "SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM user_stream");
        assertEquals(1, delta.getRowCount());
        soleRowWhere(delta, "METADATA$ACTION", "INSERT");
    }

    @Test
    public void testStreamConsume() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("CREATE TABLE users_sink (id INTEGER, name VARCHAR)");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");

        assertEquals(1L, countOf("SELECT COUNT(*) FROM user_stream"));

        // A DML statement reading the stream consumes it when its transaction commits; a plain
        // SELECT never does.
        engine.execute("INSERT INTO users_sink SELECT id, name FROM user_stream");

        assertEquals(0L, countOf("SELECT COUNT(*) FROM user_stream"));
        assertEquals(1L, countOf("SELECT COUNT(*) FROM users_sink"));
    }

    @Test
    public void testDropStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("DROP STREAM user_stream");

        assertEquals(0, engine.executeQuery("SHOW STREAMS LIKE 'user_stream'").getRowCount());
    }

    @Test
    public void testStreamWithMultipleOperations() {
        engine.execute("CREATE TABLE orders (id INTEGER, amount INTEGER, status VARCHAR)");
        engine.execute("CREATE STREAM order_stream ON TABLE orders");

        engine.execute("INSERT INTO orders VALUES (1, 100, 'pending'), (2, 200, 'completed')");

        assertEquals(2L, countOf("SELECT COUNT(*) FROM order_stream"));
    }

    @Test
    public void testStreamOnViewSurvivesDatabaseClone() {
        // A stream ON a VIEW whose base tables live in a DIFFERENT schema (a BASE_T view over BASE.* tables)
        // must still be readable after a database CLONE. Two bugs made the cloned stream unreadable
        // ("Table does not exist: <view/base>"): Schema.clone() rebuilt the stream with the source TYPE
        // defaulted to TABLE (losing VIEW), and the view's base tables were captured unqualified (so they
        // resolved in the wrong schema).
        engine.execute("CREATE DATABASE clonesrc");
        engine.execute("USE DATABASE clonesrc");
        engine.execute("CREATE SCHEMA b");
        engine.execute("CREATE SCHEMA bt");
        engine.execute("CREATE TABLE b.fact_a (id INTEGER, c VARCHAR)");
        engine.execute("CREATE TABLE b.fact_del (id INTEGER, c VARCHAR)");
        engine.execute("CREATE VIEW bt.v_and_delete AS "
            + "SELECT id, c FROM b.fact_a UNION ALL SELECT id, c FROM b.fact_del");
        engine.execute("CREATE OR REPLACE STREAM bt.s_and_delete ON VIEW bt.v_and_delete "
            + "APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = FALSE");

        engine.execute("CREATE DATABASE clonedst CLONE clonesrc");
        engine.execute("USE DATABASE clonedst");
        engine.execute("USE SCHEMA public");   // a schema OTHER than the stream's / base tables'

        // Reading the cloned stream-on-view must not throw "does not exist" (it returns an empty delta).
        final ResultSet viaName = engine.executeQuery("SELECT COUNT(*) FROM bt.s_and_delete");
        assertEquals(1, viaName.getRowCount());
        // And via IDENTIFIER (how the loaders read it).
        final ResultSet viaId = engine.executeQuery(
            "SELECT COUNT(*) FROM IDENTIFIER('bt.s_and_delete')");
        assertEquals(1, viaId.getRowCount());

        // The cloned stream preserved its VIEW source type.
        final ResultSet streams = engine.executeQuery("SHOW STREAMS IN SCHEMA bt");
        assertEquals("View", cell(streams, soleRowWhere(streams, "name", "S_AND_DELETE"), "source_type"));
    }
}
