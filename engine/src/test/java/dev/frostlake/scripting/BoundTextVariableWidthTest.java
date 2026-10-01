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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text variable bound into a statement is the value it holds, and the statement types it at that value's own
 * length — one character at least — whatever the declaration says: a VARCHAR(10) holding 'abc' reads VARCHAR(3) in
 * SYSTEM$TYPEOF, carries that width through the functions over it, and makes a VARCHAR(3) column. A procedure's text
 * parameter binds the same way. Read in the block's own expression the name has no width at all, a number keeps its
 * declared type, and a NULL keeps the declared width in a table built over it. Every cell is live-verified.
 */
public class BoundTextVariableWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tt (n INT)");
        engine.execute("INSERT INTO tt VALUES (1), (2)");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A block that declares {@code s} as written, then answers what SYSTEM$TYPEOF says of {@code expr} in a query. */
    private static String typed(final String declaration, final String expr) {
        return "DECLARE " + declaration + " t VARCHAR; BEGIN t := (SELECT SYSTEM$TYPEOF(" + expr + ")); RETURN t; END;";
    }

    private String columnsOf(final String table) {
        return answer("SELECT LISTAGG(column_name || ':' || data_type || ':'"
            + " || COALESCE(character_maximum_length::VARCHAR, 'null'), ' ')"
            + " WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = '"
            + table + "'");
    }

    @Test
    public void aBoundTextIsTypedAtItsValuesLength() {
        assertCells(new String[][] {
            {"DECLARE t VARCHAR; BEGIN LET s := 'abc'; t := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;",
                "VARCHAR(3)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", ":s"), "VARCHAR(3)[LOB]"},
            {typed("s VARCHAR DEFAULT 'abcde';", ":s"), "VARCHAR(5)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT '';", ":s"), "VARCHAR(1)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'héllo';", ":s"), "VARCHAR(5)[LOB]"},
            {typed("s STRING DEFAULT 'abc';", ":s"), "VARCHAR(3)[LOB]"},
            {typed("s TEXT DEFAULT 'ab';", ":s"), "VARCHAR(2)[LOB]"},
            {typed("s CHAR(5) DEFAULT 'ab';", ":s"), "VARCHAR(2)[LOB]"},
            {"DECLARE t VARCHAR; BEGIN LET s VARCHAR(10) := 'abc'; t := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;",
                "VARCHAR(3)[LOB]"},
            {"DECLARE t VARCHAR; BEGIN LET s VARCHAR := 12; t := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;",
                "VARCHAR(2)[LOB]"},
            {"DECLARE t VARCHAR; BEGIN LET s := 'x' || 'yz'; t := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;",
                "VARCHAR(3)[LOB]"},
        });
    }

    @Test
    public void theValuesLengthCarriesThroughTheStatement() {
        assertCells(new String[][] {
            {typed("s VARCHAR(10) DEFAULT 'abc';", ":s || 'x'"), "VARCHAR(4)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", ":s || :s"), "VARCHAR(6)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", "UPPER(:s)"), "VARCHAR(9)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", "LEFT(:s, 2)"), "VARCHAR(3)[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", "IFF(TRUE, :s, 'abcdef')"), "VARCHAR(6)[LOB]"},
            {"DECLARE s VARCHAR(10) DEFAULT 'abc'; t VARCHAR; BEGIN"
                + " t := (SELECT SYSTEM$TYPEOF(:s) FROM tt LIMIT 1); RETURN t; END;", "VARCHAR(3)[LOB]"},
            {"DECLARE s VARCHAR(10) DEFAULT 'abc'; t VARCHAR; BEGIN"
                + " SELECT SYSTEM$TYPEOF(:s) INTO :t FROM tt LIMIT 1; RETURN t; END;", "VARCHAR(3)[LOB]"},
            {"DECLARE s VARCHAR(10) DEFAULT 'abc'; t VARCHAR; BEGIN"
                + " t := (SELECT SYSTEM$TYPEOF(:s) UNION ALL SELECT 'x' LIMIT 1); RETURN t; END;", "VARCHAR(3)[LOB]"},
        });
    }

    @Test
    public void otherReadingsKeepTheirOwnType() {
        assertCells(new String[][] {
            {"DECLARE s VARCHAR(10) DEFAULT 'abc'; t VARCHAR; BEGIN t := SYSTEM$TYPEOF(s); RETURN t; END;",
                "VARCHAR[LOB]"},
            {typed("x NUMBER(10,4) DEFAULT 1.7777;", ":x"), "NUMBER(10,4)[SB2]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", ":s::VARCHAR"), "VARCHAR[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", "IFF(TRUE, :s, NULL)"), "VARCHAR(134217728)[LOB]"},
            {typed("d DATE DEFAULT '2024-01-15';", ":d"), "DATE[SB4]"},
        });
    }

    @Test
    public void aProcedureTextParameterBindsAtItsValuesLength() {
        engine.execute("CREATE OR REPLACE PROCEDURE pt(x VARCHAR(10)) RETURNS VARCHAR LANGUAGE SQL"
            + " AS 'BEGIN RETURN (SELECT SYSTEM$TYPEOF(:x)); END'");
        assertEquals("VARCHAR(3)[LOB]", answer("CALL pt('abc')"));
        engine.execute("CREATE OR REPLACE PROCEDURE pu(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL"
            + " AS 'BEGIN RETURN (SELECT SYSTEM$TYPEOF(:x)); END'");
        assertEquals("VARCHAR(6)[LOB]", answer("CALL pu('abcdef')"));
    }

    @Test
    public void aTableBuiltOverTheBoundTextStoresItsLength() {
        engine.execute("DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN CREATE OR REPLACE TABLE ct AS SELECT :s AS c;"
            + " RETURN 'ok'; END;");
        engine.execute("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN CREATE OR REPLACE TABLE cw AS SELECT :s AS c;"
            + " RETURN 'ok'; END;");
        engine.execute("DECLARE s VARCHAR(10) DEFAULT ''; BEGIN CREATE OR REPLACE TABLE ce AS SELECT :s AS c;"
            + " RETURN 'ok'; END;");
        engine.execute("DECLARE s VARCHAR(10); BEGIN CREATE OR REPLACE TABLE cn AS SELECT :s AS c;"
            + " RETURN 'ok'; END;");
        assertEquals("C:TEXT:3", columnsOf("CT"));
        assertEquals("C:TEXT:3", columnsOf("CW"));
        assertEquals("C:TEXT:1", columnsOf("CE"));
        assertEquals("C:TEXT:10", columnsOf("CN"), "a NULL keeps the declared width");
    }
}
