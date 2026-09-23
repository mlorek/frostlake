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
 * ALTER VIEW … RENAME TO takes a qualified name, resolved as a created name is: an unqualified one in the
 * session's schema, a two-part one in a schema of the session's database, so the view MOVES. A name any relation
 * there holds is refused as written, a missing schema or database is refused by name, and IF EXISTS forgives only
 * a missing view. Every cell is live-verified.
 */
public class ViewRenameTest extends BaseDatabaseTest {

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

    /** Every view of the test database, schema-qualified, in order. */
    private String views() {
        final StringBuilder names = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT table_schema || '.' || table_name FROM test_db.information_schema.views"
                + " WHERE table_schema <> 'INFORMATION_SCHEMA' ORDER BY 1").getRows()) {
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
    public void aViewMovesWhereItsNewNameResolves() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        engine.execute("CREATE VIEW taken AS SELECT 1 AS x");
        engine.execute("CREATE VIEW other.otaken AS SELECT 1 AS x");
        engine.execute("ALTER VIEW v RENAME TO test_db.test_schema.v2");
        assertEquals("OTHER.OTAKEN TEST_SCHEMA.TAKEN TEST_SCHEMA.V2", views());
        engine.execute("ALTER VIEW v2 RENAME TO test_schema.v3");
        engine.execute("ALTER VIEW v3 RENAME TO other.v4");
        assertEquals("OTHER.OTAKEN OTHER.V4 TEST_SCHEMA.TAKEN", views());
        engine.execute("ALTER VIEW other.v4 RENAME TO v5");
        assertEquals("OTHER.OTAKEN TEST_SCHEMA.TAKEN TEST_SCHEMA.V5", views());
        assertCells(new String[][] {
            {"ALTER VIEW v5 RENAME TO taken", ERROR + "Object 'TAKEN' already exists."},
            {"ALTER VIEW v5 RENAME TO other.otaken", ERROR + "Object 'OTHER.OTAKEN' already exists."},
            {"ALTER VIEW v5 RENAME TO t", ERROR + "Object 'T' already exists."},
            {"ALTER VIEW v5 RENAME TO nosuchschema.v6", ERROR + "Schema 'TEST_DB.NOSUCHSCHEMA' does not exist or not authorized."},
            {"ALTER VIEW v5 RENAME TO nosuchdb.public.v6", ERROR + "Database 'NOSUCHDB' does not exist or not authorized."},
            {"ALTER VIEW nosuch RENAME TO v7", ERROR + "View 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."},
            {"ALTER VIEW IF EXISTS v5 RENAME TO taken", ERROR + "Object 'TAKEN' already exists."},
        });
        engine.execute("ALTER VIEW IF EXISTS nosuch RENAME TO v7");
        engine.execute("ALTER VIEW v5 RENAME TO test_db..v8");
        assertEquals("OTHER.OTAKEN PUBLIC.V8 TEST_SCHEMA.TAKEN", views());
        engine.execute("ALTER VIEW public.v8 RENAME TO \"lower v\"");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM \"lower v\"", "0"},
            {"ALTER VIEW \"lower v\" RENAME TO test_schema.\"lower v\"", ERROR + "Object 'TEST_SCHEMA.\"lower v\"' already exists."},
        });
    }

    @Test
    public void withNoCurrentDatabaseOnlyAFullNamePlacesTheView() {
        engine.execute("CREATE VIEW v9 AS SELECT 1 AS x");
        engine.execute("CREATE DATABASE view_rename_gone");
        engine.execute("USE DATABASE view_rename_gone");
        engine.execute("DROP DATABASE view_rename_gone");
        try {
            final String refusal = "Cannot perform CREATE VIEW. This session does not have a current database."
                + " Call 'USE DATABASE', or use a qualified name.";
            assertEquals(refusal, answer("ALTER VIEW test_db.test_schema.v9 RENAME TO v10"));
            assertEquals(refusal, answer("ALTER VIEW test_db.test_schema.v9 RENAME TO test_schema.v10"));
            engine.execute("ALTER VIEW test_db.test_schema.v9 RENAME TO test_db.test_schema.v10");
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
        }
        assertEquals("TEST_SCHEMA.V10", views());
    }
}
