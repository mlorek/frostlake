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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DESCRIBE SCHEMA lists a schema's tables and views by name, each with its creation time and kind, and
 * DESCRIBE DATABASE lists a database's schemas; both take a written or an IDENTIFIER() name and a TYPE they
 * ignore. DESCRIBE TABLE, VIEW and MATERIALIZED VIEW take TYPE = STAGE — a table's stage properties, a view's
 * location alone — and TYPE = COLUMNS, the last TYPE winning and any other value refused as written, before
 * the object is looked up. A plain word where the object's type stands is a type the account does not
 * describe, and anything else there is refused at the DESCRIBE itself. Every cell is live-verified.
 */
public class DescribeSchemaDatabaseTest extends BaseDatabaseTest {

    /** Each row's cells from the second on (the first is a creation time), a comma between cells, a bar between rows. */
    private String listing(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 1; i < row.getValues().size(); i++) {
                out.append(i > 1 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** Every row's cells, a comma between cells and a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                for (int i = 0; i < row.getValues().size(); i++) {
                    out.append(i > 0 ? ", " : "").append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private int rowCount(final String sql) {
        return engine.executeQuery(sql).getRows().size();
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aSchemaListsItsTablesAndViewsByName() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE OR REPLACE TRANSIENT TABLE tt1 (a INT)");
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE tmp1 (a INT)");
        engine.execute("CREATE OR REPLACE SECURE VIEW sv1 AS SELECT 1 AS x");
        engine.execute("CREATE OR REPLACE TABLE \"my table\" (b VARCHAR)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq1");
        engine.execute("CREATE OR REPLACE STAGE stg1");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff1 TYPE = CSV");
        engine.execute("CREATE OR REPLACE STREAM st1 ON TABLE t1");
        engine.execute("CREATE OR REPLACE SCHEMA empty_s");
        engine.execute("USE SCHEMA test_schema");
        final String objects = "SV1, VIEW | T1, TABLE | TMP1, TEMPORARY | TT1, TABLE | my table, TABLE";
        assertEquals(objects, listing("DESCRIBE SCHEMA test_schema"));
        assertEquals(objects, listing("DESC SCHEMA IDENTIFIER('TEST_SCHEMA')"));
        assertEquals(objects, listing("DESCRIBE SCHEMA IDENTIFIER('test_db.test_schema')"));
        assertEquals(objects, listing("DESCRIBE SCHEMA test_db.test_schema TYPE = STAGE"));
        assertEquals("", listing("DESCRIBE SCHEMA empty_s"));
        final ResultSet described = engine.executeQuery("DESCRIBE SCHEMA test_schema");
        assertEquals("created_on", described.getColumns().get(0).getName());
        assertEquals("name", described.getColumns().get(1).getName());
        assertEquals("kind", described.getColumns().get(2).getName());
    }

    @Test
    public void aDatabaseListsItsSchemasByName() {
        engine.execute("CREATE OR REPLACE SCHEMA \"lower s\"");
        engine.execute("CREATE OR REPLACE SCHEMA empty_s");
        engine.execute("SET db = 'TEST_DB'");
        final String schemas = "EMPTY_S, SCHEMA | INFORMATION_SCHEMA, SCHEMA | PUBLIC, SCHEMA | TEST_SCHEMA, SCHEMA"
            + " | lower s, SCHEMA";
        assertEquals(schemas, listing("DESCRIBE DATABASE test_db"));
        assertEquals(schemas, listing("DESC DATABASE IDENTIFIER($db)"));
        assertEquals(schemas, listing("DESCRIBE DATABASE test_db TYPE = STAGE"));
    }

    @Test
    public void aMissingOrOverQualifiedContainerIsRefused() {
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSUCH' does not exist or not authorized."),
            answer("DESCRIBE SCHEMA nosuch"));
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCH' does not exist or not authorized."),
            answer("DESCRIBE DATABASE nosuch"));
        assertEquals(hinted("SQL compilation error:|Database '\"test_db\"' does not exist or not authorized."),
            answer("DESCRIBE DATABASE \"test_db\""));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DESCRIBE SCHEMA test_db.test_schema.x"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DESCRIBE DATABASE test_db.test_schema"));
        assertEquals(syntax(15, "<EOF>"), answer("DESCRIBE SCHEMA"));
        assertEquals(syntax(17, "<EOF>"), answer("DESCRIBE DATABASE"));
        assertEquals("SQL compilation error:|invalid value [foo] for parameter 'TYPE'",
            answer("DESCRIBE SCHEMA nosuch TYPE = foo"));
    }

    @Test
    public void typeStageDescribesARelationsStage() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT 1 AS x");
        final String location = "STAGE_LOCATION, URL, String, , ";
        final String[] tables = {
            "DESCRIBE TABLE t1 TYPE = STAGE", "DESC TABLE t1 TYPE=stage", "DESCRIBE TABLE t1 TYPE = 'STAGE'",
            "DESCRIBE TABLE t1 TYPE = \"STAGE\"", "DESCRIBE TABLE t1 TYPE = COLUMNS TYPE = STAGE",
            "DESCRIBE VIEW t1 TYPE = STAGE", "DESCRIBE TABLE IDENTIFIER('t1') TYPE = STAGE",
        };
        for (final String sql : tables) {
            final ResultSet rs = engine.executeQuery(sql);
            assertEquals(32, rs.getRows().size(), sql);
            assertEquals("STAGE_FILE_FORMAT, TYPE, String, CSV, CSV", answer(sql).split(" \\| ")[0], sql);
        }
        final String stage = answer("DESCRIBE TABLE t1 TYPE = STAGE");
        assertEquals(true, stage.contains(" | STAGE_FILE_FORMAT, NULL_IF, List, [\\\\N], [\\\\N] | "), stage);
        assertEquals(true, stage.contains(" | STAGE_COPY_OPTIONS, ON_ERROR, String, ABORT_STATEMENT, ABORT_STATEMENT | "), stage);
        assertEquals(true, stage.endsWith(" | STAGE_COPY_OPTIONS, FORCE, Boolean, false, false | " + location), stage);
        assertEquals(location, answer("DESCRIBE TABLE v1 TYPE = STAGE"));
        assertEquals(location, answer("DESCRIBE VIEW v1 TYPE = STAGE"));
        assertEquals(location, answer("DESCRIBE MATERIALIZED VIEW v1 TYPE = STAGE"));
        assertEquals(1, rowCount("DESCRIBE TABLE t1 TYPE = 'columns'"));
        assertEquals(1, rowCount("DESCRIBE VIEW v1 TYPE = COLUMNS"));
        assertEquals("SQL compilation error:|invalid value [foo] for parameter 'TYPE'", answer("DESCRIBE TABLE t1 TYPE = foo"));
        assertEquals("SQL compilation error:|invalid value ['foo'] for parameter 'TYPE'", answer("DESCRIBE TABLE t1 TYPE = 'foo'"));
        assertEquals("SQL compilation error:|invalid value [\"Foo\"] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 TYPE = \"Foo\""));
        assertEquals("SQL compilation error:|invalid value [1] for parameter 'TYPE'", answer("DESCRIBE TABLE t1 TYPE = 1"));
        assertEquals("SQL compilation error:|invalid value [foo] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 TYPE = STAGE TYPE = foo"));
        assertEquals("SQL compilation error:|invalid value [foo] for parameter 'TYPE'", answer("DESCRIBE TABLE nosuch TYPE = foo"));
        assertEquals(hinted("SQL compilation error:|Table 'NOSUCH' does not exist or not authorized."),
            answer("DESCRIBE TABLE nosuch TYPE = STAGE"));
        assertEquals(hinted("SQL compilation error:|View 'NOSUCH' does not exist or not authorized."),
            answer("DESCRIBE VIEW nosuch TYPE = STAGE"));
        assertEquals(syntax(23, "STAGE"), answer("DESCRIBE TABLE t1 TYPE STAGE"));
        assertEquals(syntax(25, "<EOF>"), answer("DESCRIBE TABLE t1 TYPE = "));
    }

    @Test
    public void aPlainWordWhereTheTypeStandsIsNoType() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        assertEquals("Unsupported feature 'DESCRIBE T1'.", answer("DESC t1 x"));
        assertEquals(syntax(7, "<EOF>"), answer("DESC t1"));
        assertEquals(syntax(11, "<EOF>"), answer("DESCRIBE t1"));
        assertEquals(syntax(8, "<EOF>"), answer("DESC FOO"));
        assertEquals(syntax(10, "y"), answer("DESC t1 x y"));
        assertEquals(syntax(7, ";"), answer("DESC t1;"));
        assertEquals(syntax(7, "."), answer("DESC t1.x"));
        assertEquals(syntax(12, "."), answer("DESCRIBE foo.bar"));
        assertEquals(syntax(10, "<EOF>"), answer("DESC TABLE"));
        final String[] refusedAtDesc = {"DESC IDENTIFIER('t1')", "DESC \"T1\"", "DESC 1", "DESC", "DESC IDENTIFIER",
            "DESC IDENTIFIER x"};
        for (final String sql : refusedAtDesc) {
            assertEquals(syntax(0, "DESC"), answer(sql), sql);
        }
        assertEquals(syntax(0, "DESCRIBE"), answer("DESCRIBE IDENTIFIER('t1')"));
        assertEquals(syntax(0, "DESCRIBE"), answer("DESCRIBE \"x\""));
        assertEquals(syntax(0, "DESCRIBE"), answer("DESCRIBE"));
    }
}
