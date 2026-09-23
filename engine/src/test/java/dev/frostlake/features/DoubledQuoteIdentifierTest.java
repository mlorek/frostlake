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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A doubled quote inside a quoted identifier is one quote character: {@code "a""b"} names {@code a"b}, which SHOW,
 * INFORMATION_SCHEMA and result labels print, and a name holding a quote resolves as a schema, a table, an alias
 * and a sequence alike. A refusal prints the quote once, {@code Object '"c"d"'}, except the missing-table sentence
 * and GET_DDL, which double it again. Frostlake kept both quotes in the name (live-verified).
 */
public class DoubledQuoteIdentifierTest extends BaseDatabaseTest {

    private static final String COMPILATION = "SQL compilation error:\n";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE \"a\"\"b\" (x INT, \"c\"\"d\" INT)");
        engine.execute("INSERT INTO \"a\"\"b\" VALUES (1, 2)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** Each row's cells joined by commas, rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    /** The result's column labels joined by commas. */
    private String labels(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int c = 0; c < result.getColumnCount(); c++) {
            text.append(c > 0 ? ", " : "").append(result.getColumns().get(c).getName());
        }
        return text.toString();
    }

    @Test
    public void aDoubledQuoteNamesOneQuote() {
        assertEquals("a\"b", rows("SHOW TABLES LIKE 'a%' ->> SELECT \"name\" FROM $1"));
        assertEquals("a\"b", rows("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'a%'"));
        assertEquals("X | c\"d", rows("SHOW COLUMNS IN TABLE \"a\"\"b\" ->> SELECT \"column_name\" FROM $1 ORDER BY 1"));
        assertEquals("X | c\"d",
            rows("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'a\"b' ORDER BY 1"));
        assertEquals("c\"d", labels("SELECT \"c\"\"d\" FROM \"a\"\"b\""));
        assertEquals("y\"z", labels("SELECT x AS \"y\"\"z\" FROM \"a\"\"b\""));
        assertEquals("X, c\"d", labels("SELECT \"a\"\"b\".* FROM \"a\"\"b\""));
        assertEquals("1, 2", rows("SELECT \"a\"\"b\".* FROM \"a\"\"b\""));
        assertEquals("1, 2", rows("SELECT * FROM \"a\"\"b\" AS \"q\"\"r\" WHERE \"q\"\"r\".x = 1"));
    }

    @Test
    public void aNameHoldingAQuoteResolvesInEveryPosition() {
        engine.execute("CREATE SCHEMA \"s\"\"t\"");
        assertEquals("s\"t", rows("SELECT CURRENT_SCHEMA()"));
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE \"s\"\"t\".\"e\"\"f\" (x INT)");
        engine.execute("INSERT INTO \"s\"\"t\".\"e\"\"f\" VALUES (1)");
        engine.execute("UPDATE \"s\"\"t\".\"e\"\"f\" SET x = 2");
        assertEquals("2", rows("SELECT \"e\"\"f\".x FROM \"s\"\"t\".\"e\"\"f\""));
        assertEquals("s\"t, e\"f",
            rows("SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema = 's\"t'"));
        engine.execute("CREATE VIEW \"s\"\"t\".\"v\"\"w\" AS SELECT x FROM \"s\"\"t\".\"e\"\"f\"");
        assertEquals("2", rows("SELECT * FROM \"s\"\"t\".\"v\"\"w\""));
        engine.execute("CREATE SEQUENCE \"s\"\"t\".\"q\"\"s\"");
        assertEquals("1", rows("SELECT \"s\"\"t\".\"q\"\"s\".nextval"));
        engine.execute("ALTER TABLE \"s\"\"t\".\"e\"\"f\" RENAME TO \"s\"\"t\".\"g\"\"h\"");
        assertEquals("2", rows("SELECT x FROM \"s\"\"t\".\"g\"\"h\""));
        engine.execute("DROP VIEW \"s\"\"t\".\"v\"\"w\"");
        engine.execute("DROP TABLE \"s\"\"t\".\"g\"\"h\"");
        engine.execute("DROP SCHEMA \"s\"\"t\"");
    }

    @Test
    public void aRefusalPrintsTheQuoteOnceAndTheMissingTableSentenceDoublesIt() {
        assertEquals(hinted(COMPILATION + "Object '\"c\"d\"' does not exist or not authorized."), refusal("SELECT * FROM \"c\"\"d\""));
        assertEquals(COMPILATION + "Object '\"g\"h\"' does not exist or not authorized.", refusal("SELECT \"g\"\"h\".*"));
        assertEquals(hinted(COMPILATION + "Object '\"n\"o\"' does not exist or not authorized."),
            refusal("UPDATE \"n\"\"o\" SET x = 1"));
        final String at7 = "SQL compilation error: error line 1 at position 7\n";
        assertEquals(at7 + "invalid identifier '\"e\"f\"'", refusal("SELECT \"e\"\"f\""));
        assertEquals(at7 + "invalid identifier '\"a\"b\".NOSUCH'", refusal("SELECT \"a\"\"b\".nosuch FROM \"a\"\"b\""));
        assertEquals(at7 + "invalid identifier '\"q\"x\".NEXTVAL'", refusal("SELECT \"q\"\"x\".nextval"));
        assertEquals("SQL compilation error: error line 1 at position 30\ninvalid identifier '\"n\"o\"'",
            refusal("SELECT x FROM \"a\"\"b\" GROUP BY \"n\"\"o\""));
        assertEquals(COMPILATION + "column 'n\"o' does not exist", refusal("ALTER TABLE \"a\"\"b\" DROP COLUMN \"n\"\"o\""));
        assertEquals(hinted(COMPILATION + "View 'TEST_DB.TEST_SCHEMA.\"n\"o\"' does not exist or not authorized."),
            refusal("DROP VIEW \"n\"\"o\""));
        assertEquals(hinted(COMPILATION + "Sequence 'TEST_DB.TEST_SCHEMA.\"n\"o\"' does not exist or not authorized."),
            refusal("DROP SEQUENCE \"n\"\"o\""));
        // A table alone is spelled with its quote doubled.
        assertEquals(hinted(COMPILATION + "Table '\"n\"\"o\"' does not exist or not authorized."), refusal("DESCRIBE TABLE \"n\"\"o\""));
        assertEquals(hinted(COMPILATION + "Table '\"n\"\"o\"' does not exist or not authorized."),
            refusal("INSERT INTO \"n\"\"o\" VALUES (1)"));
        assertEquals(hinted(COMPILATION + "Table 'TEST_DB.TEST_SCHEMA.\"n\"\"o\"' does not exist or not authorized."),
            refusal("DROP TABLE \"n\"\"o\""));
        assertEquals(hinted(COMPILATION + "Table 'TEST_DB.TEST_SCHEMA.\"n\"\"o\"' does not exist or not authorized."),
            refusal("TRUNCATE TABLE \"n\"\"o\""));
    }

    @Test
    public void getDdlDoublesTheQuote() {
        assertEquals("create or replace TABLE \"a\"\"b\" (\n\tX NUMBER(38,0),\n\t\"c\"\"d\" NUMBER(38,0)\n);",
            rows("SELECT GET_DDL('TABLE', '\"a\"\"b\"')"));
        engine.execute("CREATE SEQUENCE \"s\"\"q\"");
        assertEquals("create or replace sequence \"s\"\"q\" start with 1 increment by 1 noorder;",
            rows("SELECT GET_DDL('SEQUENCE', '\"s\"\"q\"')"));
        engine.execute("CREATE VIEW \"v\"\"w\" AS SELECT 1 AS \"a\"\"b\"");
        assertEquals("create or replace view \"v\"\"w\"(\n\t\"a\"\"b\"\n) as SELECT 1 AS \"a\"\"b\";",
            rows("SELECT GET_DDL('VIEW', '\"v\"\"w\"')"));
        assertEquals("v\"w, CREATE VIEW \"v\"\"w\" AS SELECT 1 AS \"a\"\"b\"",
            rows("SHOW VIEWS LIKE 'v%' ->> SELECT \"name\", \"text\" FROM $1"));
    }
}
