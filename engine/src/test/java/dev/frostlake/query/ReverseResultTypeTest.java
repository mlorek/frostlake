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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REVERSE hands a TEXT and a BINARY back at their own declared widths, and CONVERTS everything else
 * to text first — so its result over a number, a date, a BOOLEAN, a FLOAT or a VARIANT is the 128MB
 * text every converting string function declares, not the argument's own type.
 *
 * <p>The distinction is not cosmetic: the declared type is what a CTAS or a view over the expression
 * writes down, and what a comparison or an arithmetic over it is typed from.
 */
public class ReverseResultTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rv (n INT, n52 NUMBER(5,2), v VARCHAR(5), "
            + "b BINARY(4), va VARIANT, d DATE, bo BOOLEAN, f FLOAT)");
        engine.execute("INSERT INTO rv SELECT 22, 1.25, 'abc', TO_BINARY('AABB','HEX'), "
            + "TO_VARIANT('xy'), '2026-01-02', TRUE, 1.5");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A text argument comes back at its OWN width, a literal included. */
    @Test
    public void textKeepsItsOwnWidth() {
        assertEquals("VARCHAR(5)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(v)) FROM rv"));
        assertEquals("VARCHAR(3)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE('abc'))"));
    }

    /** A binary keeps its own width too — REVERSE over one is still a BINARY. */
    @Test
    public void binaryKeepsItsOwnWidth() {
        assertEquals("BINARY(4)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(b)) FROM rv"));
    }

    /** Every other family is converted, and the result is the unknown-length text. */
    @Test
    public void everythingElseIsConvertedText() {
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(n)) FROM rv"));
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(n52)) FROM rv"));
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(d)) FROM rv"));
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(bo)) FROM rv"));
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(f)) FROM rv"));
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(va)) FROM rv"),
            "a VARIANT is converted like the rest");
        assertEquals("VARCHAR(134217728)[LOB]", answer("SELECT SYSTEM$TYPEOF(REVERSE(123))"),
            "an integer literal too");
    }

    /** What a CTAS over the expression declares follows the same rule. */
    @Test
    public void aCtasDeclaresTheSameTypes() {
        engine.execute("CREATE OR REPLACE TABLE rvc AS SELECT REVERSE(n) AS c1, REVERSE(v) AS c2, "
            + "REVERSE(b) AS c3, REVERSE(d) AS c4 FROM rv");
        final ResultSet rs = engine.executeQuery("SELECT column_name, data_type, "
            + "character_maximum_length FROM information_schema.columns "
            + "WHERE table_name = 'RVC' ORDER BY column_name");
        final StringBuilder declared = new StringBuilder();
        while (rs.next()) {
            declared.append(rs.getValue(0)).append(" ").append(rs.getValue(1))
                .append("(").append(rs.getValue(2)).append(") ");
        }
        assertEquals("C1 TEXT(16777216) C2 TEXT(5) C3 BINARY(null) C4 TEXT(16777216) ",
            declared.toString());
    }

    /** The values are unaffected — only the declared type was wrong. */
    @Test
    public void theValuesAreUnchanged() {
        assertEquals("22", answer("SELECT REVERSE(n) FROM rv"));
        assertEquals("BBAA", answer("SELECT REVERSE(b) FROM rv"));
        assertEquals("20-10-6202", answer("SELECT REVERSE(d) FROM rv"));
        assertEquals("eurt", answer("SELECT REVERSE(bo) FROM rv"));
        assertEquals("yx", answer("SELECT REVERSE(va) FROM rv"));
    }
}
