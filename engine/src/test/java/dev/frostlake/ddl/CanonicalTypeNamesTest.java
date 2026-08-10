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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A column's type is reported CANONICALLY, never as the alias it was declared with: DESCRIBE TABLE's
 * {@code type} cell and GET_DDL's column line agree, and both spell the parameters even when the
 * declaration left them out.
 *
 * <p>Every row here is live-measured. The ones worth naming: the whole integer family collapses to
 * NUMBER(38,0), CHAR and NVARCHAR are VARCHAR of their length, DOUBLE and REAL are FLOAT, a bare
 * TIME is TIME(9) while a bare TIMESTAMP is TIMESTAMP_NTZ(9), and a bare BINARY is 8MB wide.
 */
public class CanonicalTypeNamesTest extends BaseDatabaseTest {

    /** Declared type, then the canonical spelling live reports for it. */
    private static final String[][] TYPES = {
        {"INT", "NUMBER(38,0)"},
        {"INTEGER", "NUMBER(38,0)"},
        {"BIGINT", "NUMBER(38,0)"},
        {"SMALLINT", "NUMBER(38,0)"},
        {"BYTEINT", "NUMBER(38,0)"},
        {"NUMBER", "NUMBER(38,0)"},
        {"NUMBER(10,2)", "NUMBER(10,2)"},
        {"DECIMAL(5)", "NUMBER(5,0)"},
        {"VARCHAR", "VARCHAR(16777216)"},
        {"STRING", "VARCHAR(16777216)"},
        {"TEXT", "VARCHAR(16777216)"},
        {"VARCHAR(20)", "VARCHAR(20)"},
        {"CHAR", "VARCHAR(1)"},
        {"CHAR(5)", "VARCHAR(5)"},
        {"NVARCHAR(9)", "VARCHAR(9)"},
        {"FLOAT", "FLOAT"},
        {"DOUBLE", "FLOAT"},
        {"REAL", "FLOAT"},
        {"BOOLEAN", "BOOLEAN"},
        {"DATE", "DATE"},
        {"TIME", "TIME(9)"},
        {"TIME(3)", "TIME(3)"},
        {"TIMESTAMP", "TIMESTAMP_NTZ(9)"},
        {"TIMESTAMP_TZ(6)", "TIMESTAMP_TZ(6)"},
        {"BINARY", "BINARY(8388608)"},
        {"BINARY(100)", "BINARY(100)"},
        {"VARIANT", "VARIANT"},
        {"OBJECT", "OBJECT"},
        {"ARRAY", "ARRAY"}
    };

    @Override
    protected void setupTest() {
        final StringBuilder columns = new StringBuilder();
        for (int i = 0; i < TYPES.length; i++) {
            columns.append(i == 0 ? "" : ", ").append("c").append(i).append(' ').append(TYPES[i][0]);
        }
        engine.execute("CREATE TABLE ty_t (" + columns + ")");
    }

    @Test
    public void describeSpellsEveryTypeCanonically() {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE ty_t");
        for (int i = 0; i < TYPES.length; i++) {
            assertEquals(TYPES[i][1],
                cell(described, soleRowWhere(described, "name", "C" + i), "type"),
                "column c" + i + " declared " + TYPES[i][0]);
        }
    }

    /**
     * A STRUCTURED type spells its parts: each OBJECT field as "name TYPE" separated by ", " with the
     * name in its DECLARED case, NOT NULL inside the parentheses, and nesting all the way down. The
     * unstructured OBJECT and ARRAY above stay bare, which is why both are asserted.
     */
    @Test
    public void structuredTypesSpellTheirParts() {
        engine.execute("""
            CREATE TABLE st_t (
              o OBJECT(a VARCHAR),
              o2 OBJECT(a VARCHAR, b NUMBER),
              o3 OBJECT(MixedCase VARCHAR),
              a1 ARRAY(NUMBER),
              a2 ARRAY(VARCHAR(4)),
              a3 ARRAY(ARRAY(NUMBER)),
              o4 OBJECT(a OBJECT(b VARCHAR)),
              m MAP(VARCHAR, NUMBER))""");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE st_t");
        assertEquals("OBJECT(a VARCHAR(16777216))", cell(rs, soleRowWhere(rs, "name", "O"), "type"));
        assertEquals("OBJECT(a VARCHAR(16777216), b NUMBER(38,0))",
            cell(rs, soleRowWhere(rs, "name", "O2"), "type"));
        assertEquals("OBJECT(MixedCase VARCHAR(16777216))",
            cell(rs, soleRowWhere(rs, "name", "O3"), "type"));
        assertEquals("ARRAY(NUMBER(38,0))", cell(rs, soleRowWhere(rs, "name", "A1"), "type"));
        assertEquals("ARRAY(VARCHAR(4))", cell(rs, soleRowWhere(rs, "name", "A2"), "type"));
        assertEquals("ARRAY(ARRAY(NUMBER(38,0)))", cell(rs, soleRowWhere(rs, "name", "A3"), "type"));
        assertEquals("OBJECT(a OBJECT(b VARCHAR(16777216)))",
            cell(rs, soleRowWhere(rs, "name", "O4"), "type"));
        assertEquals("MAP(VARCHAR(16777216), NUMBER(38,0))",
            cell(rs, soleRowWhere(rs, "name", "M"), "type"));
    }

    @Test
    public void getDdlSpellsThemTheSameWay() {
        final String ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'ty_t')")
            .getRows().get(0).getValue(0).toString();
        for (int i = 0; i < TYPES.length; i++) {
            assertTrue(ddl.contains("C" + i + " " + TYPES[i][1]),
                "column c" + i + " declared " + TYPES[i][0] + " in: " + ddl);
        }
    }
}
