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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How wide a binary concatenation is, surface by surface (live-verified). The plan adds the operands'
 * widths up to the 64MB maximum and is unsized past it — two 8MB columns concatenated are BINARY(16777216)
 * in {@code SYSTEM$TYPEOF}, in an argument-type refusal, through a derived table, a view and a set
 * operation — and a binary INSERT splices as a concatenation of the base's two pieces around the insertion.
 * A STORED column settles any wider binary at the 8MB column default: a CTAS stores BINARY(8388608), and a
 * view declares it in DESCRIBE and SHOW COLUMNS, though a query over the view reads the full width.
 *
 * <p>Frostlake used to saturate every concatenation at 8MB and to splice an INSERT at the sum even over an
 * unsized operand.
 */
public class BinaryConcatenationWidthTest extends BaseDatabaseTest {

    private static final String UNSIZED = "BINARY[LOB]";

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE OR REPLACE TABLE bt (vb VARBINARY, b5 BINARY(5), bb BINARY, bn BINARY(8388608), s VARCHAR(10),
                b4 BINARY(4))""");
        engine.execute("""
            INSERT INTO bt SELECT TO_BINARY('00'), TO_BINARY('0102'), TO_BINARY('AB'), TO_BINARY('CD'), '00',
                TO_BINARY('01')""");
        engine.execute("""
            INSERT INTO bt SELECT TO_BINARY('0000'), TO_BINARY('010203'), TO_BINARY('ABCD'), TO_BINARY('CDEF'), '0102',
                TO_BINARY('0203')""");
    }

    /** Every row's first cell, joined, or the refusal on one line. */
    private String cells(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                out.append(String.valueOf(rs.getValue(0)));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** One named column of every row {@code sql} answers, joined. */
    private String column(final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue(name)));
        }
        return out.toString();
    }

    /** The tag of {@code expression} over {@code bt}, on its first row. */
    private String typeOf(final String expression) {
        return cells("SELECT SYSTEM$TYPEOF(" + expression + ") FROM bt LIMIT 1");
    }

    /** The tag of {@code expression} with no FROM. */
    private String typeOfLiteral(final String expression) {
        return cells("SELECT SYSTEM$TYPEOF(" + expression + ")");
    }

    /** Asserts an ABS refusal of {@code sql} names its argument as {@code argumentType}. */
    private void refusalNames(final String sql, final String argumentType) {
        final String refusal = cells(sql);
        assertTrue(refusal.contains("Invalid argument types for function 'ABS': (" + argumentType + ")"),
            sql + " -> " + refusal);
    }

    /** ★ The widths add up to the 64MB maximum, and past it the concatenation is unsized. */
    @Test
    public void aConcatenationAddsItsWidthsUpToTheMaximum() {
        assertEquals("BINARY(16777216)[LOB]", typeOf("vb || vb"));
        assertEquals("BINARY(8388613)[LOB]", typeOf("vb || b5"));
        assertEquals("BINARY(8388613)[LOB]", typeOf("b5 || vb"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("bn || bn"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("bb || bb"));
        assertEquals("BINARY(8388613)[LOB]", typeOf("bn || b5"));
        assertEquals("BINARY(25165824)[LOB]", typeOf("vb || vb || vb"));
        assertEquals("BINARY(67108864)[LOB]", typeOf("vb || vb || vb || vb || vb || vb || vb || vb"));
        assertEquals(UNSIZED, typeOf("vb || vb || vb || vb || vb || vb || vb || vb || b5"));
        assertEquals(UNSIZED, typeOf("vb || vb || vb || vb || vb || vb || vb || vb || vb"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("CONCAT(vb, vb)"));
        assertEquals("BINARY(8388613)[LOB]", typeOf("CONCAT(vb, b5)"));
        assertEquals("BINARY(25165824)[LOB]", typeOf("CONCAT(vb, vb, vb)"));
        assertEquals(UNSIZED, typeOf("CONCAT(vb, vb, vb, vb, vb, vb, vb, vb, vb)"));
        assertEquals(UNSIZED, typeOf("CONCAT(vb, TO_BINARY('00'))"));
        assertEquals("BINARY(10)[LOB]", typeOf("b5 || b5"));
        assertEquals("BINARY(8)[LOB]", typeOf("b4 || b4"));
        assertEquals("BINARY(8388609)[LOB]", typeOfLiteral("X'00'::BINARY(8388608) || X'00'"));
        assertEquals("BINARY(16777216)[LOB]", typeOfLiteral("X'00'::BINARY(8388608) || X'00'::BINARY(8388608)"));
        assertEquals("BINARY(67108864)[LOB]", typeOfLiteral("X'00'::BINARY(60000000) || X'00'::BINARY(7108864)"));
        assertEquals(UNSIZED, typeOfLiteral("X'00'::BINARY(60000000) || X'00'::BINARY(7108865)"));
        assertEquals("BINARY(67108864)[LOB]", typeOfLiteral("X'00'::BINARY(67108863) || X'00'"));
        assertEquals(UNSIZED, typeOfLiteral("X'00'::BINARY(67108864) || X'00'"));
        assertEquals(UNSIZED, typeOf("(vb || vb) || TO_BINARY('00')"));
        assertEquals(UNSIZED, typeOf("COALESCE(b5, TO_BINARY(s)) || b5"));
        assertEquals("0000010203 | 000102", cells("SELECT vb || b5 FROM bt ORDER BY 1"));
    }

    /** The summed width rides through folds, pieces, aggregates, derived tables, views and set operations. */
    @Test
    public void theWidthRidesThroughTheQuery() {
        engine.execute("CREATE OR REPLACE VIEW cv1 AS SELECT vb || vb AS c FROM bt");
        engine.execute("CREATE OR REPLACE VIEW cv2 AS SELECT vb || b5 AS c FROM bt");
        assertEquals("BINARY(16777216)[LOB]", typeOf("IFF(s = '00', vb || vb, b5)"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("IFF(s = '00', b5, vb || vb)"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("COALESCE(vb || vb, b5)"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("SUBSTR(vb || vb, 1, 1)"));
        assertEquals("BINARY(16777216)[LOB]", typeOf("LEFT(vb || vb, 1)"));
        assertEquals("BINARY(16777216)[LOB] | BINARY(16777216)[LOB]", cells("SELECT SYSTEM$TYPEOF(MAX(vb || vb)) FROM bt"));
        assertEquals("BINARY(16777216)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM (SELECT vb || vb AS c FROM bt) LIMIT 1"));
        assertEquals("BINARY(16777216)[LOB]",
            cells("SELECT SYSTEM$TYPEOF(c) FROM (SELECT vb || vb AS c FROM bt UNION ALL SELECT b5 FROM bt) LIMIT 1"));
        assertEquals("BINARY(16777216)[LOB]",
            cells("SELECT SYSTEM$TYPEOF(c) FROM (SELECT b5 AS c FROM bt UNION ALL SELECT vb || vb FROM bt) LIMIT 1"));
        assertEquals("BINARY(67108864)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM (SELECT b5 AS c FROM bt UNION ALL"
            + " SELECT vb || vb || vb || vb || vb || vb || vb || vb || vb FROM bt) LIMIT 1"), "a sized lead meets an unsized arm");
        assertEquals("BINARY(16777216)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM cv1 LIMIT 1"));
        assertEquals("BINARY(8388613)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM cv2 LIMIT 1"));
    }

    /** ★ A binary INSERT splices as a concatenation of the base's two pieces around the insertion. */
    @Test
    public void aBinaryInsertSplicesAsAConcatenation() {
        assertEquals("BINARY(11)[LOB]", typeOf("INSERT(b5, 1, 1, X'00')"));
        assertEquals("BINARY(15)[LOB]", typeOf("INSERT(b5, 1, 1, b5)"));
        assertEquals("BINARY(16777217)[LOB]", typeOf("INSERT(vb, 1, 1, X'00')"));
        assertEquals("BINARY(25165824)[LOB]", typeOf("INSERT(vb, 1, 1, vb)"));
        assertEquals("BINARY(5)[LOB]", typeOfLiteral("INSERT(X'0011', 1, 1, X'22')"));
        assertEquals("BINARY(50331648)[LOB]", typeOf("INSERT(vb || vb, 1, 1, vb || vb)"));
        assertEquals("BINARY(41943040)[LOB]", typeOf("INSERT(vb, 1, 1, vb || vb || vb)"));
        assertEquals("BINARY(67108864)[LOB]", typeOf("INSERT(vb || vb, 1, 1, vb || vb || vb || vb)"));
        assertEquals("BINARY(67108864)[LOB]", typeOf("INSERT(vb || vb || vb, 1, 1, vb || vb)"));
        assertEquals(UNSIZED, typeOf("INSERT(vb || vb || vb, 1, 1, vb || vb || vb)"), "past the maximum");
        assertEquals(UNSIZED, typeOf("INSERT(bn, 1, 1, TO_BINARY('00'))"), "an unsized insertion");
        assertEquals(UNSIZED, typeOf("INSERT(vb, 1, 1, TO_BINARY('00'))"));
        assertEquals(UNSIZED, typeOfLiteral("INSERT(TO_BINARY('0011'), 1, 1, X'00')"), "an unsized base");
        assertEquals(UNSIZED, typeOfLiteral("INSERT(X'0011', 1, 1, TO_BINARY('00'))"));
        assertEquals(UNSIZED, typeOf("INSERT(COALESCE(b5, TO_BINARY(s)), 1, 1, X'00')"));
        assertEquals(UNSIZED, typeOf("INSERT(b5, 1, 1, NULL)"), "a NULL insertion");
        assertEquals(UNSIZED, typeOf("INSERT(vb, 1, 1, NULL)"));
    }

    /** ★ An argument-type refusal names the summed width, and an unsized binary at the 64MB maximum. */
    @Test
    public void aRefusalNamesTheSummedWidth() {
        engine.execute("CREATE OR REPLACE VIEW cv1 AS SELECT vb || vb AS c FROM bt");
        refusalNames("SELECT ABS(vb || vb) FROM bt", "BINARY(16777216)");
        refusalNames("SELECT ABS(vb || b5) FROM bt", "BINARY(8388613)");
        refusalNames("SELECT ABS(bn || bn) FROM bt", "BINARY(16777216)");
        refusalNames("SELECT ABS(INSERT(vb, 1, 1, X'00')) FROM bt", "BINARY(16777217)");
        refusalNames("SELECT ABS(vb || vb || vb || vb || vb || vb || vb || vb || vb) FROM bt", "BINARY(67108864)");
        refusalNames("SELECT ABS(INSERT(vb, 1, 1, TO_BINARY('00'))) FROM bt", "BINARY(67108864)");
        refusalNames("SELECT ABS(c) FROM cv1", "BINARY(16777216)");
    }

    /** ★ A stored column settles a wider binary at the 8MB default: a CTAS stores it, a view declares it. */
    @Test
    public void aStoredColumnSettlesAtTheColumnDefault() {
        engine.execute("CREATE OR REPLACE TABLE ct1 AS SELECT vb || vb AS c, vb || b5 AS d, b4 || b4 AS e FROM bt");
        assertEquals("BINARY(8388608) | BINARY(8388608) | BINARY(8)", column("DESCRIBE TABLE ct1", "type"));
        assertEquals("BINARY(8388608)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM ct1 LIMIT 1"));
        engine.execute("""
            CREATE OR REPLACE TABLE ct2 AS SELECT X'00'::BINARY(67108864) AS c, X'00'::BINARY(16777216) AS d,
                vb || vb || vb AS e FROM bt""");
        assertEquals("BINARY(8388608) | BINARY(8388608) | BINARY(8388608)", column("DESCRIBE TABLE ct2", "type"));
        engine.execute("CREATE OR REPLACE TABLE ct3 (c BINARY(16777216))");
        assertEquals("BINARY(16777216)", column("DESCRIBE TABLE ct3", "type"), "a declared width is its own");
        engine.execute("CREATE OR REPLACE TABLE ct4 (c BINARY(67108864))");
        assertEquals("BINARY(67108864)", column("DESCRIBE TABLE ct4", "type"));
        engine.execute("CREATE OR REPLACE VIEW cv1 AS SELECT vb || vb AS c FROM bt");
        engine.execute("CREATE OR REPLACE VIEW cv2 AS SELECT vb || b5 AS c FROM bt");
        engine.execute("CREATE OR REPLACE VIEW cv6 AS SELECT vb || vb || vb || vb || vb || vb || vb || vb || vb AS c FROM bt");
        engine.execute("CREATE OR REPLACE VIEW cv9 AS SELECT X'00'::BINARY(67108864) AS c");
        engine.execute("CREATE OR REPLACE VIEW cv10 AS SELECT X'00'::BINARY(16777216) AS c");
        engine.execute("CREATE OR REPLACE VIEW cv13 AS SELECT b5 || b5 AS c FROM bt");
        assertEquals("BINARY(8388608)", column("DESCRIBE VIEW cv1", "type"));
        assertEquals("BINARY(8388608)", column("DESCRIBE VIEW cv2", "type"));
        assertEquals("BINARY(8388608)", column("DESCRIBE VIEW cv6", "type"));
        assertEquals("BINARY(8388608)", column("DESCRIBE VIEW cv9", "type"));
        assertEquals("BINARY(8388608)", column("DESCRIBE VIEW cv10", "type"));
        assertEquals("BINARY(10)", column("DESCRIBE VIEW cv13", "type"));
        assertEquals("""
            {"type":"BINARY","length":8388608,"byteLength":8388608,"nullable":true,"fixed":false}""",
            column("SHOW COLUMNS IN VIEW test_db.test_schema.cv1", "data_type"));
        assertEquals("""
            {"type":"BINARY","length":8388608,"byteLength":8388608,"nullable":true,"fixed":true}""",
            column("SHOW COLUMNS IN VIEW test_db.test_schema.cv9", "data_type"));
        assertEquals("BINARY(67108864)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM cv9"), "a query reads the full width");
        assertEquals("BINARY(16777216)[LOB]", cells("SELECT SYSTEM$TYPEOF(c) FROM cv10"));
    }

    /** A wider concatenation still inserts into a narrower binary column whose values it fits. */
    @Test
    public void aWiderConcatenationInsertsWhereItsValuesFit() {
        engine.execute("CREATE OR REPLACE TABLE it (c BINARY(8388608), d BINARY(5))");
        engine.execute("INSERT INTO it (c) SELECT vb || vb FROM bt");
        engine.execute("INSERT INTO it (d) SELECT vb || vb FROM bt");
        assertEquals("0000 | 00000000", cells("SELECT d FROM it WHERE d IS NOT NULL ORDER BY 1"));
    }
}
