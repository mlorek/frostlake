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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A PRIMARY KEY declared in CREATE TABLE makes each of its columns NOT NULL, inline, out of line and as a
 * named constraint alike, and an explicit NULL beside the key does not win. DESCRIBE, SHOW COLUMNS,
 * INFORMATION_SCHEMA and a view over the column all follow, and a write of NULL is refused. A key added later
 * by ALTER TABLE leaves nullability alone, while a column added WITH a key is NOT NULL like any other
 * declaration. Every cell is live-verified.
 */
public class PrimaryKeyNullabilityTest extends BaseDatabaseTest {

    /** "ok", or the refusal on one line. */
    private String run(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String isNullable(final String table, final String column) {
        return String.valueOf(engine.executeQuery("SELECT is_nullable FROM information_schema.columns"
            + " WHERE table_name = '" + table.toUpperCase() + "' AND column_name = '"
            + column.toUpperCase() + "'").getRows().get(0).getValue(0));
    }

    private static String nullWrite(final String table, final String column) {
        return "DML operation to table " + table + " failed on column " + column
            + " with error: NULL result in a non-nullable column";
    }

    @Test
    public void aDeclaredKeyColumnIsNotNullInEveryForm() {
        engine.execute("CREATE TABLE pkn1 (x INT PRIMARY KEY, y INT)");
        engine.execute("CREATE TABLE pkn2 (x INT, y INT, PRIMARY KEY (x, y))");
        engine.execute("CREATE TABLE pkn3 (x INT, y INT, CONSTRAINT pk3 PRIMARY KEY (x))");

        assertEquals("N", describeCell("pkn1", "X", "null?"));
        assertEquals("Y", describeCell("pkn1", "Y", "null?"));
        assertEquals("N", describeCell("pkn2", "X", "null?"));
        assertEquals("N", describeCell("pkn2", "Y", "null?"));
        assertEquals("N", describeCell("pkn3", "X", "null?"));
        assertEquals("Y", describeCell("pkn3", "Y", "null?"));

        assertEquals("NO", isNullable("pkn1", "X"));
        assertEquals("YES", isNullable("pkn1", "Y"));
        assertEquals("NO", isNullable("pkn2", "Y"));
    }

    @Test
    public void anExplicitNullBesideTheKeyDoesNotWin() {
        engine.execute("CREATE TABLE pkn4 (x INT NULL, y INT, CONSTRAINT pk4 PRIMARY KEY (x, y))");
        engine.execute("CREATE TABLE pkn5 (x INT NULL PRIMARY KEY, y INT)");

        assertEquals("N", describeCell("pkn4", "X", "null?"));
        assertEquals("N", describeCell("pkn5", "X", "null?"));
        assertEquals("NO", isNullable("pkn4", "X"));
    }

    @Test
    public void aNullWrittenIntoAKeyColumnIsRefused() {
        engine.execute("CREATE TABLE pkn6 (x INT PRIMARY KEY, y INT)");

        assertEquals(nullWrite("PKN6", "X"), run("INSERT INTO pkn6 VALUES (NULL, 1)"));
        assertEquals(nullWrite("PKN6", "X"), run("INSERT INTO pkn6 (x, y) VALUES (NULL, 1)"));
        assertEquals(nullWrite("PKN6", "X"), run("INSERT INTO pkn6 SELECT NULL, 1"));
        assertEquals("ok", run("INSERT INTO pkn6 VALUES (1, 1)"));
    }

    /** The refusal names the table the way the statement spelled it, upper-cased. */
    @Test
    public void theNullWriteRefusalNamesTheTableAsWritten() {
        engine.execute("CREATE TABLE pkn7 (x INT PRIMARY KEY, y INT)");

        assertEquals(nullWrite("PKN7", "X"), run("INSERT INTO pkn7 VALUES (NULL, 1)"));
        assertEquals(nullWrite("TEST_DB.TEST_SCHEMA.PKN7", "X"),
            run("INSERT INTO test_db.test_schema.pkn7 VALUES (NULL, 1)"));
    }

    @Test
    public void aViewOverAKeyColumnReportsItNotNull() {
        engine.execute("CREATE TABLE pkn8 (x INT PRIMARY KEY, y INT)");
        engine.execute("CREATE VIEW pkn8v AS SELECT x, y FROM pkn8");

        assertEquals("N", describeCell("pkn8v", "X", "null?"));
        assertEquals("Y", describeCell("pkn8v", "Y", "null?"));
    }

    /** A key added after the fact leaves nullability alone; a column added with one is NOT NULL. */
    @Test
    public void alterTableKeepsNullabilityButAnAddedKeyColumnIsNotNull() {
        engine.execute("CREATE TABLE pkn9 (x INT, y INT)");
        engine.execute("INSERT INTO pkn9 VALUES (NULL, 1)");
        engine.execute("ALTER TABLE pkn9 ADD PRIMARY KEY (x)");

        assertEquals("Y", describeCell("pkn9", "X", "null?"));
        assertEquals("ok", run("INSERT INTO pkn9 VALUES (NULL, 2)"));

        engine.execute("CREATE TABLE pkn10 (a INT)");
        engine.execute("ALTER TABLE pkn10 ADD COLUMN b INT PRIMARY KEY");
        assertEquals("N", describeCell("pkn10", "B", "null?"));
    }

    /** A CTAS keeps no key, so the column it selects stays nullable. */
    @Test
    public void aCtasDropsTheKeyAndItsNullability() {
        engine.execute("CREATE TABLE pkn11 (x INT PRIMARY KEY, y INT)");
        engine.execute("CREATE TABLE pkn12 AS SELECT x, y FROM pkn11");

        assertEquals("Y", describeCell("pkn12", "X", "null?"));
    }
}
