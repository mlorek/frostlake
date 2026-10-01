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
 * A TEMPORARY table may take a PERMANENT table's name, and a permanent table may be created under a name a
 * temporary table holds; the temporary table then shadows the permanent one. Reads, writes, SHOW TABLES and
 * every statement by name reach the temporary table, INFORMATION_SCHEMA lists both, a permanent create meets
 * only the permanent table, and dropping, renaming or moving the temporary table uncovers the permanent one.
 * Every cell is live-verified.
 */
public class TemporaryTableShadowTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";
    private static final String MIXED_SWAP = "Swapping of a temporary table with another non-temporary table is not allowed.";

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

    /** Every row's first cell, in order, joined by " | ". */
    private String column(final String sql) {
        final StringBuilder cells = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            cells.append(cells.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return cells.toString();
    }

    /** What SHOW TABLES lists under that exact name here: each row's kind, joined by " | ". */
    private String kinds(final String name, final String schema) {
        final ResultSet listed = engine.executeQuery("SHOW TABLES LIKE '" + name + "' IN SCHEMA " + schema);
        final StringBuilder cells = new StringBuilder();
        for (final Row row : listed.getRows()) {
            cells.append(cells.length() > 0 ? " | " : "").append(cell(listed, row, "kind"));
        }
        return cells.toString();
    }

    private String kinds(final String name) {
        return kinds(name, "test_db.test_schema");
    }

    /** The TABLE_TYPE of every INFORMATION_SCHEMA.TABLES row of that name in that schema, in order. */
    private String tableTypes(final String schema, final String name) {
        return column("SELECT table_type FROM test_db.information_schema.tables WHERE table_schema = '" + schema
            + "' AND table_name = '" + name + "' ORDER BY 1");
    }

    @Test
    public void aTemporaryTableTakesAPermanentTablesName() {
        engine.execute("CREATE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1), (2)");
        engine.execute("CREATE TEMPORARY TABLE kt (b VARCHAR)");
        assertEquals("0", answer("SELECT COUNT(*) FROM kt"));
        engine.execute("INSERT INTO kt VALUES ('x')");
        engine.execute("CREATE VIEW vk AS SELECT * FROM kt");
        assertEquals("x", column("SELECT * FROM kt"));
        assertEquals("x", column("SELECT * FROM vk"));
        assertEquals("TEMPORARY", kinds("KT"));
        assertEquals("B", answer("DESCRIBE TABLE kt"));
        assertEquals("BASE TABLE | LOCAL TEMPORARY", tableTypes("TEST_SCHEMA", "KT"));
        assertEquals("A | B", column("SELECT column_name FROM test_db.information_schema.columns"
            + " WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'KT' ORDER BY 1"));
        assertEquals(ERROR + "Object 'KT' already exists.", answer("CREATE TEMPORARY TABLE kt (c INT)"));
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE kt (c INT)");
        assertEquals("C", answer("DESCRIBE TABLE kt"));
        engine.execute("DROP TABLE kt");
        assertEquals("1 | 2", column("SELECT * FROM kt ORDER BY 1"));
        assertEquals("TABLE", kinds("KT"));
        assertEquals("BASE TABLE", tableTypes("TEST_SCHEMA", "KT"));

        // OR REPLACE and IF NOT EXISTS take the name too, and leave the permanent table alone.
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE kt (d INT)");
        assertEquals("D", answer("DESCRIBE TABLE kt"));
        engine.execute("DROP TABLE kt");
        engine.execute("CREATE TEMPORARY TABLE IF NOT EXISTS kt (e INT)");
        assertEquals("E", answer("DESCRIBE TABLE kt"));
        engine.execute("DROP TABLE IF EXISTS kt");
        assertEquals("1 | 2", column("SELECT * FROM kt ORDER BY 1"));
        assertEquals(ERROR + "Object 'KT' already exists.", answer("UNDROP TABLE kt"));
    }

    @Test
    public void aPermanentCreateMeetsOnlyThePermanentTable() {
        engine.execute("CREATE TABLE kp (a INT)");
        engine.execute("INSERT INTO kp VALUES (1), (2)");
        engine.execute("CREATE TEMPORARY TABLE kp (b VARCHAR)");
        engine.execute("INSERT INTO kp VALUES ('x')");
        assertEquals(ERROR + "Object 'KP' already exists.", answer("CREATE TABLE kp (z INT)"));
        assertEquals(ERROR + "Object 'KP' already exists.", answer("CREATE TRANSIENT TABLE kp (z INT)"));
        engine.execute("CREATE TABLE IF NOT EXISTS kp (z INT)");
        engine.execute("CREATE OR REPLACE TABLE kp (z INT)");
        assertEquals("x", column("SELECT * FROM kp"));
        assertEquals("TEMPORARY", kinds("KP"));
        assertEquals("B | Z", column("SELECT column_name FROM test_db.information_schema.columns"
            + " WHERE table_schema = 'TEST_SCHEMA' AND table_name = 'KP' ORDER BY 1"));
        engine.execute("DROP TABLE kp");
        assertEquals("no row", answer("SELECT * FROM kp"));
        assertEquals("Z", answer("DESCRIBE TABLE kp"));

        // Under a temporary table alone, a permanent create goes beneath it; its query reads the temporary table.
        engine.execute("CREATE TEMPORARY TABLE kx (a INT)");
        engine.execute("INSERT INTO kx VALUES (1)");
        engine.execute("CREATE TABLE kx AS SELECT a + 1 AS a FROM kx");
        assertEquals("1", column("SELECT * FROM kx"));
        assertEquals("TEMPORARY", kinds("KX"));
        assertEquals("BASE TABLE | LOCAL TEMPORARY", tableTypes("TEST_SCHEMA", "KX"));
        engine.execute("DROP TABLE kx");
        assertEquals("2", column("SELECT * FROM kx"));
        assertEquals("TABLE", kinds("KX"));
        engine.execute("CREATE TEMPORARY TABLE ky (b INT)");
        engine.execute("CREATE OR REPLACE TABLE ky (a INT)");
        engine.execute("INSERT INTO ky VALUES (7)");
        engine.execute("DROP TABLE ky");
        assertEquals("no row", answer("SELECT * FROM ky"));
        assertEquals("A", answer("DESCRIBE TABLE ky"));
    }

    @Test
    public void aCopyReadsTheTableItsNameIsAboutToHide() {
        engine.execute("CREATE TABLE kc (a INT)");
        engine.execute("INSERT INTO kc VALUES (5)");
        engine.execute("CREATE TEMPORARY TABLE kc AS SELECT a + 10 AS a FROM kc");
        assertEquals("15", column("SELECT * FROM kc"));
        engine.execute("DROP TABLE kc");
        engine.execute("CREATE TEMPORARY TABLE kc LIKE kc");
        assertEquals("0", answer("SELECT COUNT(*) FROM kc"));
        engine.execute("DROP TABLE kc");
        engine.execute("CREATE TEMPORARY TABLE kc CLONE kc");
        assertEquals("5", column("SELECT * FROM kc"));
        assertEquals("TEMPORARY", kinds("KC"));
        engine.execute("DELETE FROM kc");
        engine.execute("DROP TABLE kc");
        assertEquals("5", column("SELECT * FROM kc"));
        assertEquals("TABLE", kinds("KC"));
    }

    @Test
    public void renamingSwappingOrMovingTheTemporaryTable() {
        engine.execute("CREATE SCHEMA other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1), (2)");
        engine.execute("CREATE TEMPORARY TABLE kt (b VARCHAR)");
        engine.execute("INSERT INTO kt VALUES ('x')");
        engine.execute("ALTER TABLE kt RENAME TO kt4");
        assertEquals("1 | 2", column("SELECT * FROM kt ORDER BY 1"));
        assertEquals("x", column("SELECT * FROM kt4"));
        assertEquals(ERROR + "Object 'KT' already exists.", answer("ALTER TABLE kt4 RENAME TO kt"));

        engine.execute("CREATE TEMPORARY TABLE kt (c INT)");
        engine.execute("CREATE TABLE ks (a INT)");
        assertEquals(ERROR + "Object 'KT' already exists.", answer("ALTER TABLE ks RENAME TO kt"));
        assertEquals(ERROR + "Object 'KT' already exists.", answer("ALTER TABLE kt4 RENAME TO kt"));
        assertEquals(MIXED_SWAP, answer("ALTER TABLE kt SWAP WITH ks"));
        assertEquals(MIXED_SWAP, answer("ALTER TABLE ks SWAP WITH kt"));

        // Two temporary tables swap; the permanent table stays beneath the name.
        engine.execute("CREATE TEMPORARY TABLE kt6 (d INT)");
        engine.execute("INSERT INTO kt6 VALUES (6)");
        engine.execute("ALTER TABLE kt SWAP WITH kt6");
        assertEquals("6", column("SELECT * FROM kt"));
        assertEquals("no row", answer("SELECT * FROM kt6"));
        engine.execute("DROP TABLE kt");
        assertEquals("1 | 2", column("SELECT * FROM kt ORDER BY 1"));

        // Moving the temporary table to another schema uncovers the permanent one too.
        engine.execute("CREATE TEMPORARY TABLE kt (e INT)");
        engine.execute("ALTER TABLE kt RENAME TO other.kt");
        assertEquals("1 | 2", column("SELECT * FROM kt ORDER BY 1"));
        assertEquals("TABLE", kinds("KT"));
        assertEquals("TEMPORARY", kinds("KT", "test_db.other"));
    }

    @Test
    public void aSchemaKeepsTheHiddenTableThroughRenameDropAndClone() {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE s2.km (a INT)");
        engine.execute("INSERT INTO s2.km VALUES (8)");
        engine.execute("CREATE TEMPORARY TABLE s2.km (b INT)");
        engine.execute("INSERT INTO s2.km VALUES (4), (5)");
        engine.execute("ALTER SCHEMA s2 RENAME TO s3");
        assertEquals("4 | 5", column("SELECT * FROM s3.km ORDER BY 1"));
        engine.execute("DROP SCHEMA s3");
        engine.execute("UNDROP SCHEMA s3");
        assertEquals("4 | 5", column("SELECT * FROM s3.km ORDER BY 1"));
        assertEquals("BASE TABLE | LOCAL TEMPORARY", tableTypes("S3", "KM"));

        // A clone copies the permanent table and never the temporary one.
        engine.execute("CREATE SCHEMA s4 CLONE s3");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertEquals("BASE TABLE", tableTypes("S4", "KM"));
        assertEquals("8", column("SELECT * FROM s4.km"));
        engine.execute("DROP TABLE s3.km");
        assertEquals("8", column("SELECT * FROM s3.km"));
    }
}
