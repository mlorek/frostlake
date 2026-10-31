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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An identity column declared ORDER. The engine read ORDER and NOORDER and kept neither, so every surface told
 * NOORDER, and GET_DDL wrote NOT NULL on an identity column that takes NULL.
 *
 * <p>On the account INFORMATION_SCHEMA.COLUMNS tells IDENTITY_ORDERED YES for an identity declared ORDER and NO for
 * any other, DESCRIBE TABLE and SHOW COLUMNS end the generator in ORDER or NOORDER, and GET_DDL in {@code order} or
 * {@code noorder}; a LIKE and a CLONE keep the choice. The choice is written once. An identity column takes NULL
 * unless declared NOT NULL. Every cell is live-verified.
 */
public class IdentityOrderTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ti (a INT AUTOINCREMENT ORDER, b INT AUTOINCREMENT NOORDER, c INT IDENTITY(1, 1)"
            + " ORDER, d INT AUTOINCREMENT, e INT IDENTITY START 5 INCREMENT 2 NOORDER, f INT AUTOINCREMENT (3, 4) ORDER,"
            + " g INT)");
    }

    /** The statement's refusal on one line, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** {@code column=IDENTITY_ORDERED} for each column of a table, in order. */
    private List<String> ordered(final String table) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT column_name, identity_ordered FROM information_schema.columns"
                + " WHERE table_name = '" + table + "' AND table_schema = 'TEST_SCHEMA' ORDER BY ordinal_position")
                .getRows()) {
            out.add(row.getValue(0) + "=" + row.getValue(1));
        }
        return out;
    }

    private static List<String> rows(final String... rows) {
        final List<String> out = new ArrayList<>();
        for (final String row : rows) {
            out.add(row);
        }
        return out;
    }

    @Test
    public void theViewTellsTheChoice() {
        assertEquals(rows("A=YES", "B=NO", "C=YES", "D=NO", "E=NO", "F=YES", "G=null"), ordered("TI"));
        final Row a = engine.executeQuery("SELECT is_identity, identity_generation, identity_start, identity_increment,"
            + " identity_cycle FROM information_schema.columns WHERE table_name = 'TI' AND column_name = 'F'").getRows()
            .get(0);
        assertEquals("YES|BY DEFAULT|3|4|NO", a.getValue(0) + "|" + a.getValue(1) + "|" + a.getValue(2) + "|"
            + a.getValue(3) + "|" + a.getValue(4));
    }

    @Test
    public void describeAndShowColumnsSpellTheGenerator() {
        assertEquals("IDENTITY START 1 INCREMENT 1 ORDER", describeCell("ti", "A", "default"));
        assertEquals("IDENTITY START 1 INCREMENT 1 NOORDER", describeCell("ti", "B", "default"));
        assertEquals("IDENTITY START 1 INCREMENT 1 NOORDER", describeCell("ti", "D", "default"));
        assertEquals("IDENTITY START 3 INCREMENT 4 ORDER", describeCell("ti", "F", "default"));
        final ResultSet shown = engine.executeQuery("SHOW COLUMNS IN TABLE ti");
        assertEquals("IDENTITY START 1 INCREMENT 1 ORDER",
            cell(shown, soleRowWhere(shown, "column_name", "C"), "autoincrement"));
        assertEquals("IDENTITY START 5 INCREMENT 2 NOORDER",
            cell(shown, soleRowWhere(shown, "column_name", "E"), "autoincrement"));
    }

    @Test
    public void getDdlEndsTheGeneratorInItsOrder() {
        final String ddl = String.valueOf(engine.executeQuery("SELECT GET_DDL('table', 'ti')").getRows().get(0)
            .getValue(0));
        assertTrue(ddl.contains("\tA NUMBER(38,0) autoincrement start 1 increment 1 order,"), ddl);
        assertTrue(ddl.contains("\tB NUMBER(38,0) autoincrement start 1 increment 1 noorder,"), ddl);
        assertTrue(ddl.contains("\tD NUMBER(38,0) autoincrement start 1 increment 1 noorder,"), ddl);
        assertTrue(ddl.contains("\tF NUMBER(38,0) autoincrement start 3 increment 4 order,"), ddl);
        engine.execute("CREATE TABLE tn (a INT AUTOINCREMENT, c INT IDENTITY ORDER NOT NULL)");
        final String strict = String.valueOf(engine.executeQuery("SELECT GET_DDL('table', 'tn')").getRows().get(0)
            .getValue(0));
        assertTrue(strict.contains("\tA NUMBER(38,0) autoincrement start 1 increment 1 noorder,"), strict);
        assertTrue(strict.contains("\tC NUMBER(38,0) NOT NULL autoincrement start 1 increment 1 order"), strict);
    }

    @Test
    public void anIdentityColumnTakesNullUnlessDeclaredNotNull() {
        engine.execute("CREATE TABLE tn (a INT AUTOINCREMENT, b INT, c INT IDENTITY ORDER NOT NULL)");
        assertEquals("Y", describeCell("tn", "A", "null?"));
        assertEquals("N", describeCell("tn", "C", "null?"));
        engine.execute("INSERT INTO tn (a, b) VALUES (NULL, 1)");
        assertEquals("true", String.valueOf(engine.executeQuery("SELECT a IS NULL FROM tn").getRows().get(0).getValue(0)));
    }

    @Test
    public void theChoiceIsWrittenOnce() {
        final String duplicate = "SQL compilation error:|duplicate property 'SEQUENCE_ORDER';";
        assertEquals(duplicate, outcome("CREATE TABLE tx (a INT AUTOINCREMENT ORDER NOORDER)"));
        assertEquals(duplicate, outcome("CREATE TABLE tx (a INT AUTOINCREMENT ORDER ORDER)"));
        assertEquals(duplicate, outcome("CREATE TABLE tx (a INT IDENTITY NOORDER NOORDER)"));
        assertEquals(duplicate, outcome("CREATE TABLE tx (a INT IDENTITY (1, 1) NOORDER ORDER)"));
        assertEquals(duplicate, outcome("CREATE TABLE tx (a INT AUTOINCREMENT (1, 1) ORDER ORDER)"));
        assertEquals(duplicate, outcome("CREATE TABLE tx (a INT IDENTITY (1, 1) ORDER NOORDER ORDER)"));
        assertEquals("ok", outcome("CREATE TABLE tx (a INT IDENTITY START 1 ORDER INCREMENT 1)"));
        assertEquals(rows("A=YES"), ordered("TX"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'ORDER'.",
            outcome("CREATE TABLE ty (a INT ORDER)"));
    }

    @Test
    public void everyOptionIsWrittenOnceAndTheFirstRepeatedIsTheOneTold() {
        final String order = "SQL compilation error:|duplicate property 'SEQUENCE_ORDER';";
        final String start = "SQL compilation error:|duplicate property 'SEQUENCE_START';";
        final String increment = "SQL compilation error:|duplicate property 'SEQUENCE_INCREMENT';";
        assertEquals(start, outcome("CREATE TABLE tx (a INT IDENTITY START 1 START 2)"));
        assertEquals(start, outcome("CREATE TABLE tx (a INT AUTOINCREMENT START WITH 1 START 2)"));
        assertEquals(increment, outcome("CREATE TABLE tx (a INT IDENTITY INCREMENT 1 INCREMENT 2)"));
        assertEquals(start, outcome("CREATE TABLE tx (a INT IDENTITY START 1 INCREMENT 1 START 2)"));
        assertEquals(order, outcome("CREATE TABLE tx (a INT IDENTITY ORDER ORDER START 1 START 2)"));
        assertEquals(start, outcome("CREATE TABLE tx (a INT IDENTITY START 1 START 2 ORDER ORDER)"));
        assertEquals(increment, outcome("CREATE TABLE tx (a INT IDENTITY INCREMENT 1 START 1 INCREMENT 2 START 2)"));
        assertEquals(start, outcome("CREATE TABLE tx (a INT IDENTITY START 1 ORDER START 2 NOORDER)"));
    }

    @Test
    public void aPairTakesOneChoiceBeforeItAndAnyNumberAfterIt() {
        for (final String form : new String[] {"IDENTITY ORDER (5, 2)", "AUTOINCREMENT ORDER (5, 2)", "IDENTITY (5, 2) ORDER"}) {
            engine.execute("CREATE OR REPLACE TABLE tp (a INT " + form + ", b INT)");
            assertEquals(rows("A=YES", "B=null"), ordered("TP"));
            assertEquals("IDENTITY START 5 INCREMENT 2 ORDER", describeCell("tp", "A", "default"));
        }
        engine.execute("CREATE OR REPLACE TABLE tp (a INT IDENTITY NOORDER (5, 2), b INT)");
        assertEquals("IDENTITY START 5 INCREMENT 2 NOORDER", describeCell("tp", "A", "default"));
        engine.execute("ALTER TABLE tp ADD COLUMN c INT IDENTITY ORDER (1, 1)");
        assertEquals("IDENTITY START 1 INCREMENT 1 ORDER", describeCell("tp", "C", "default"));
        assertEquals("SQL compilation error:|duplicate property 'SEQUENCE_ORDER';",
            outcome("ALTER TABLE tp ADD COLUMN d INT IDENTITY (1, 1) ORDER ORDER"));
        // Nothing but that choice stands beside a pair, and the report ends at the word that does.
        final String[][] cells = {
            {"IDENTITY ORDER (1, 1) ORDER", "45", "ORDER"}, {"IDENTITY ORDER (1, 1) NOORDER", "45", "NOORDER"},
            {"IDENTITY ORDER (1, 1) START 1", "45", "START"}, {"IDENTITY ORDER (1, 1) (1, 1)", "45", "("},
            {"IDENTITY (1, 1) START 1", "39", "START"}, {"IDENTITY (1, 1) START WITH 1", "39", "START"},
            {"IDENTITY (1, 1) INCREMENT 1", "39", "INCREMENT"}, {"IDENTITY (1, 1) ORDER START 1", "45", "START"},
            {"IDENTITY (1, 1) NOORDER START 5", "47", "START"}, {"IDENTITY (1, 1) (1, 1)", "39", "("},
            {"IDENTITY (1, 1) NOORDER (1, 1)", "47", "("}, {"AUTOINCREMENT (1, 1) START 1", "44", "START"},
            {"IDENTITY ()", "33", ")"}, {"AUTOINCREMENT ()", "38", ")"}, {"IDENTITY (x, 1)", "33", "x"},
            {"IDENTITY (1.5, 1)", "33", "1.5"}, {"IDENTITY (, 1)", "33", ","}, {"IDENTITY ORDER ()", "39", ")"},
        };
        for (final String[] cell : cells) {
            assertEquals("SQL compilation error:|syntax error line 1 at position " + cell[1] + " unexpected '" + cell[2]
                + "'.", outcome("CREATE TABLE tx (a INT " + cell[0] + ")"), cell[0]);
        }
        // Two choices before a pair are a word list, which no pair follows.
        assertTrue(outcome("CREATE TABLE tx (a INT IDENTITY NOORDER ORDER (1, 1))")
            .startsWith("SQL compilation error:|syntax error line 1 at position 46 unexpected '('."));
        assertTrue(outcome("CREATE TABLE tx (a INT IDENTITY START 1 (1, 1))")
            .startsWith("SQL compilation error:|syntax error line 1 at position 40 unexpected '('."));
    }

    @Test
    public void startAndIncrementTakeTheirWordAndAnEqualsSign() {
        final String[][] forms = {
            {"START WITH 5", "5", "1"}, {"START = 5", "5", "1"}, {"START WITH = 5", "5", "1"}, {"INCREMENT BY 3", "1", "3"},
            {"INCREMENT = 3", "1", "3"}, {"INCREMENT BY = 3", "1", "3"}, {"START WITH 5 INCREMENT BY 3", "5", "3"},
            {"START WITH = 5 INCREMENT BY = 3", "5", "3"}, {"INCREMENT BY 3 START WITH 5", "5", "3"},
        };
        for (final String[] form : forms) {
            engine.execute("CREATE OR REPLACE TABLE ts (a INT IDENTITY " + form[0] + ", b INT)");
            assertEquals("IDENTITY START " + form[1] + " INCREMENT " + form[2] + " NOORDER",
                describeCell("ts", "A", "default"), form[0]);
        }
        engine.execute("CREATE OR REPLACE TABLE ts (a INT AUTOINCREMENT START WITH -5 INCREMENT BY -3 NOORDER, b INT)");
        assertEquals("IDENTITY START -5 INCREMENT -3 NOORDER", describeCell("ts", "A", "default"));
        engine.execute("INSERT INTO ts (b) VALUES (1)");
        assertEquals("-5", String.valueOf(engine.executeQuery("SELECT a FROM ts").getRows().get(0).getValue(0)));
        final String[][] refused = {
            {"START +1", "38", "+"}, {"START WITH +1", "43", "+"}, {"START = +1", "40", "+"}, {"START 1.5", "38", "1.5"},
            {"START BY 1", "38", "BY"}, {"START WITH", "42", ")"}, {"START =", "39", ")"}, {"START WITH =", "44", ")"},
            {"START", "37", ")"}, {"INCREMENT BY", "44", ")"}, {"INCREMENT =", "43", ")"}, {"INCREMENT = BY 1", "44", "BY"},
        };
        for (final String[] cell : refused) {
            assertEquals("SQL compilation error:|syntax error line 1 at position " + cell[1] + " unexpected '" + cell[2]
                + "'.", outcome("CREATE TABLE tx (a INT IDENTITY " + cell[0] + ")"), cell[0]);
        }
    }

    @Test
    public void aCopyKeepsTheChoice() {
        engine.execute("CREATE TABLE ti_like LIKE ti");
        assertEquals(rows("A=YES", "B=NO", "C=YES", "D=NO", "E=NO", "F=YES", "G=null"), ordered("TI_LIKE"));
        engine.execute("CREATE TABLE ti_clone CLONE ti");
        assertEquals(rows("A=YES", "B=NO", "C=YES", "D=NO", "E=NO", "F=YES", "G=null"), ordered("TI_CLONE"));
    }
}
