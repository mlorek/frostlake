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
        assertEquals("SQL compilation error:|syntax error line 1 at position 47 unexpected 'ORDER'.",
            outcome("CREATE TABLE tx (a INT IDENTITY (1, 1) NOORDER ORDER)"));
        assertEquals("ok", outcome("CREATE TABLE tx (a INT IDENTITY START 1 ORDER INCREMENT 1)"));
        assertEquals(rows("A=YES"), ordered("TX"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'ORDER'.",
            outcome("CREATE TABLE ty (a INT ORDER)"));
    }

    @Test
    public void aCopyKeepsTheChoice() {
        engine.execute("CREATE TABLE ti_like LIKE ti");
        assertEquals(rows("A=YES", "B=NO", "C=YES", "D=NO", "E=NO", "F=YES", "G=null"), ordered("TI_LIKE"));
        engine.execute("CREATE TABLE ti_clone CLONE ti");
        assertEquals(rows("A=YES", "B=NO", "C=YES", "D=NO", "E=NO", "F=YES", "G=null"), ordered("TI_CLONE"));
    }
}
