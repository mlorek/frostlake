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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER SEQUENCE … RENAME TO renames a sequence where its new name resolves, as a created name does: an
 * unqualified name in the session's schema and a two-part one in a schema of the session's database, so the
 * sequence MOVES, keeping its value. A name a sequence there holds, the sequence itself included, is refused as
 * written, a table's name is no obstacle, a missing schema or database is refused by name, and IF EXISTS forgives
 * only a missing sequence. Every cell is live-verified.
 */
public class SequenceRenameTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Every sequence of the test database, schema-qualified, in order. */
    private String sequences() {
        final StringBuilder names = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT sequence_schema || '.' || sequence_name"
                + " FROM test_db.information_schema.sequences ORDER BY 1").getRows()) {
            names.append(names.length() > 0 ? " " : "").append(row.getValue(0));
        }
        return names.toString();
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(hinted(cell[1]), answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aTakenNameIsRefusedAndAFreeOneRenames() {
        engine.execute("CREATE SEQUENCE s1");
        engine.execute("CREATE SEQUENCE s2");
        engine.execute("CREATE TABLE tt (a INT)");
        final long drawn = Long.parseLong(answer("SELECT s1.nextval"));
        assertCells(new String[][] {
            {"ALTER SEQUENCE s1 RENAME TO s2", ERROR + "Object 'S2' already exists."},
            {"ALTER SEQUENCE s1 RENAME TO s1", ERROR + "Object 'S1' already exists."},
            {"ALTER SEQUENCE IF EXISTS s1 RENAME TO s2", ERROR + "Object 'S2' already exists."},
            {"ALTER SEQUENCE nosuch RENAME TO x", ERROR + "Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."},
        });
        engine.execute("ALTER SEQUENCE IF EXISTS nosuch RENAME TO x");
        engine.execute("ALTER SEQUENCE s1 RENAME TO tt");
        assertEquals("true", String.valueOf(Long.parseLong(answer("SELECT tt.nextval")) > drawn));
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier 'S1.NEXTVAL'",
            answer("SELECT s1.nextval"));
        assertEquals("TEST_SCHEMA.S2 TEST_SCHEMA.TT", sequences());
    }

    @Test
    public void aSequenceMovesWhereItsNewNameResolves() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SEQUENCE s4");
        engine.execute("CREATE SEQUENCE other.taken");
        engine.execute("ALTER SEQUENCE s4 RENAME TO other.s4");
        assertEquals("OTHER.S4 OTHER.TAKEN", sequences());
        engine.execute("ALTER SEQUENCE other.s4 RENAME TO s5");
        assertEquals("OTHER.TAKEN TEST_SCHEMA.S5", sequences());
        assertCells(new String[][] {
            {"ALTER SEQUENCE s5 RENAME TO other.taken", ERROR + "Object 'OTHER.TAKEN' already exists."},
            {"ALTER SEQUENCE s5 RENAME TO nosuchschema.s6", ERROR + "Schema 'TEST_DB.NOSUCHSCHEMA' does not exist or not authorized."},
            {"ALTER SEQUENCE s5 RENAME TO nosuchdb.public.s6", ERROR + "Database 'NOSUCHDB' does not exist or not authorized."},
        });
        engine.execute("ALTER SEQUENCE s5 RENAME TO test_db.test_schema.\"a b\"");
        assertCells(new String[][] {
            {"ALTER SEQUENCE \"a b\" RENAME TO test_schema.\"a b\"", ERROR + "Object 'TEST_SCHEMA.\"a b\"' already exists."},
            {"ALTER SEQUENCE \"a b\" RENAME TO test_db.test_schema.\"a b\"",
                ERROR + "Object 'TEST_DB.TEST_SCHEMA.\"a b\"' already exists."},
        });
        engine.execute("ALTER SEQUENCE \"a b\" RENAME TO test_db..s7");
        assertEquals("OTHER.TAKEN PUBLIC.S7", sequences());
    }

    @Test
    public void withNoCurrentDatabaseOnlyAFullNamePlacesTheSequence() {
        engine.execute("CREATE SEQUENCE s8");
        engine.execute("CREATE DATABASE seq_rename_gone");
        engine.execute("USE DATABASE seq_rename_gone");
        engine.execute("DROP DATABASE seq_rename_gone");
        try {
            final String refusal = "Cannot perform CREATE SEQUENCE. This session does not have a current database."
                + " Call 'USE DATABASE', or use a qualified name.";
            assertEquals(refusal, answer("ALTER SEQUENCE test_db.test_schema.s8 RENAME TO s9"));
            assertEquals(refusal, answer("ALTER SEQUENCE test_db.test_schema.s8 RENAME TO test_schema.s9"));
            engine.execute("ALTER SEQUENCE test_db.test_schema.s8 RENAME TO test_db.test_schema.s9");
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
        }
        assertEquals("TEST_SCHEMA.S9", sequences());
    }
}
