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
 * Two databases, or two schemas, whose names differ only in case are two objects: each is created, read,
 * replaced, dropped, undropped and renamed by its own spelling, and a refusal spells the name quoted where it
 * has to be. Renaming a database keeps its tables' rows, and renaming the session's current database leaves
 * the session with no current database. Every cell is live-verified.
 */
public class NamespaceNameCaseTest extends BaseDatabaseTest {

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

    /** Every row, its cells joined by a space, the rows by " | ". */
    private String rows(final String sql) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            text.append(text.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                text.append(i > 0 ? " " : "").append(row.getValue(i));
            }
        }
        return text.toString();
    }

    private String databases(final String pattern) {
        return rows("SELECT database_name FROM test_db.information_schema.databases WHERE database_name ILIKE '"
            + pattern + "' ORDER BY 1");
    }

    private String schemas() {
        return rows("SELECT schema_name FROM test_db.information_schema.schemata WHERE schema_name ILIKE 'ss' ORDER BY 1");
    }

    @Test
    public void databasesDifferingOnlyInCaseAreTwoDatabases() {
        try {
            engine.execute("CREATE DATABASE name_case_db");
            engine.execute("CREATE DATABASE \"name_case_db\"");
            assertEquals("NAME_CASE_DB | name_case_db", databases("name_case_db"));
            assertEquals("name_case_db PUBLIC", rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
            engine.execute("CREATE TABLE t1 (lower_db INT)");
            engine.execute("USE DATABASE name_case_db");
            engine.execute("CREATE TABLE t1 (upper_db INT)");
            assertEquals("LOWER_DB",
                answer("SELECT column_name FROM \"name_case_db\".information_schema.columns WHERE table_name = 'T1'"));
            assertEquals("UPPER_DB",
                answer("SELECT column_name FROM name_case_db.information_schema.columns WHERE table_name = 'T1'"));
            assertEquals(ERROR + "Object '\"name_case_db\"' already exists.", answer("CREATE DATABASE \"name_case_db\""));
            assertEquals(ERROR + "Object 'NAME_CASE_DB' already exists.", answer("CREATE DATABASE name_case_db"));

            // OR REPLACE, DROP and UNDROP each reach only their own database.
            engine.execute("INSERT INTO name_case_db.public.t1 VALUES (1), (2)");
            engine.execute("CREATE OR REPLACE DATABASE \"name_case_db\"");
            assertEquals("2", answer("SELECT COUNT(*) FROM name_case_db.public.t1"));
            assertEquals(hinted(ERROR + "Object '\"name_case_db\".PUBLIC.T1' does not exist or not authorized."),
                answer("SELECT COUNT(*) FROM \"name_case_db\".public.t1"));
            engine.execute("DROP DATABASE \"name_case_db\"");
            assertEquals("NAME_CASE_DB", databases("name_case_db"));
            assertEquals(hinted(ERROR + "Database '\"name_case_db\"' does not exist or not authorized."),
                answer("DROP DATABASE \"name_case_db\""));
            engine.execute("UNDROP DATABASE \"name_case_db\"");
            engine.execute("USE DATABASE \"name_case_db\"");
            assertEquals("name_case_db PUBLIC", rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));

            // A rename keeps the tables' rows, leaves another database's session alone, and may change only case.
            engine.execute("ALTER DATABASE name_case_db RENAME TO name_case_db2");
            assertEquals("2", answer("SELECT COUNT(*) FROM name_case_db2.public.t1"));
            assertEquals("name_case_db PUBLIC", rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
            assertEquals(ERROR + "Object 'NAME_CASE_DB2' already exists.",
                answer("ALTER DATABASE \"name_case_db\" RENAME TO \"NAME_CASE_DB2\""));
            engine.execute("ALTER DATABASE \"name_case_db\" RENAME TO \"Name_Case_Db\"");
            assertEquals("NAME_CASE_DB2 | Name_Case_Db", databases("name_case_db%"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS \"name_case_db\"");
            engine.execute("DROP DATABASE IF EXISTS \"Name_Case_Db\"");
            engine.execute("DROP DATABASE IF EXISTS name_case_db");
            engine.execute("DROP DATABASE IF EXISTS name_case_db2");
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void renamingTheCurrentDatabaseLeavesNoCurrentDatabase() {
        try {
            engine.execute("CREATE DATABASE name_case_current");
            engine.execute("ALTER DATABASE name_case_current RENAME TO name_case_renamed");
            assertEquals("null null", rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS name_case_current");
            engine.execute("DROP DATABASE IF EXISTS name_case_renamed");
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void schemasDifferingOnlyInCaseAreTwoSchemas() {
        engine.execute("CREATE SCHEMA ss");
        engine.execute("CREATE SCHEMA \"ss\"");
        assertEquals("ss", answer("SELECT CURRENT_SCHEMA()"));
        assertEquals("SS | ss", schemas());
        engine.execute("CREATE TABLE test_db.ss.t1 (upper_schema INT)");
        engine.execute("CREATE TABLE test_db.\"ss\".t1 (lower_schema INT)");
        assertEquals("SS UPPER_SCHEMA | ss LOWER_SCHEMA", rows("SELECT table_schema, column_name"
            + " FROM test_db.information_schema.columns WHERE table_name = 'T1' AND table_schema ILIKE 'ss' ORDER BY 1"));
        engine.execute("INSERT INTO test_db.\"ss\".t1 VALUES (7)");
        assertEquals("0", answer("SELECT COUNT(*) FROM test_db.ss.t1"));
        engine.execute("USE SCHEMA test_db.\"ss\"");
        assertEquals("7", answer("SELECT * FROM t1"));
        assertEquals(ERROR + "Object 'TEST_DB.\"ss\"' already exists.", answer("CREATE SCHEMA test_db.\"ss\""));
        assertEquals(ERROR + "Object 'TEST_DB.SS' already exists.", answer("CREATE SCHEMA test_db.ss"));

        // DROP and UNDROP reach only their own schema.
        engine.execute("DROP SCHEMA test_db.\"ss\"");
        assertEquals("SS", schemas());
        assertEquals(hinted(ERROR + "Schema 'TEST_DB.\"ss\"' does not exist or not authorized."), answer("DROP SCHEMA test_db.\"ss\""));
        engine.execute("UNDROP SCHEMA test_db.\"ss\"");
        assertEquals("1", answer("SELECT COUNT(*) FROM test_db.\"ss\".t1"));

        // A rename may change only case, onto a name no schema holds exactly.
        engine.execute("CREATE SCHEMA test_db.\"Ss\"");
        engine.execute("ALTER SCHEMA test_db.\"Ss\" RENAME TO test_db.\"sS\"");
        assertEquals("SS | sS | ss", schemas());
        assertEquals(ERROR + "Object 'TEST_DB.SS' already exists.", answer("ALTER SCHEMA test_db.\"sS\" RENAME TO test_db.ss"));
        assertEquals(ERROR + "Object 'TEST_DB.\"ss\"' already exists.",
            answer("ALTER SCHEMA test_db.ss RENAME TO test_db.\"ss\""));

        // Dropping a schema whose name differs only in case still moves the session to PUBLIC.
        engine.execute("USE SCHEMA test_db.ss");
        engine.execute("DROP SCHEMA test_db.\"ss\"");
        assertEquals("PUBLIC", answer("SELECT CURRENT_SCHEMA()"));
    }
}
