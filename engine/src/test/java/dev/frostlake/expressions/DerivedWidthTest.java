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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The WIDTH a derived column declares, which Frostlake used to answer with the family maximum whenever
 * the value did not come straight off a declared column.
 *
 * <p>Three places decide it and all three were wrong:
 *
 * <ul>
 *   <li>a CAST names a real type, parameter and all — {@code CAST(s AS VARCHAR(10))} is a VARCHAR(10),
 *       not the 16MB maximum, and the same held for BINARY, TIME and the timestamps. Only NUMBER's
 *       parameters survived;</li>
 *   <li>a binary-returning FUNCTION is as wide as its result — a hash its digest, a decoder a ratio of
 *       its argument's declared width;</li>
 *   <li>a set operation's column is as wide as its WIDEST branch, where Frostlake took the FIRST
 *       branch's — which also silently narrowed strings.</li>
 * </ul>
 */
public class DerivedWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE dw (s4 VARCHAR(4), s100 VARCHAR(100), s VARCHAR,"
            + " b4 BINARY(4), b100 BINARY(100), t TIMESTAMP_NTZ, n NUMBER)");
        engine.execute("INSERT INTO dw VALUES ('4142', '4142', '4142', TO_BINARY('41424344'),"
            + " TO_BINARY('4142'), '2026-01-01', 1)");
    }

    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW dw_v AS SELECT " + expression + " AS c FROM dw");
        return descriptor();
    }

    private String declaredTypeOfBody(final String select) {
        engine.execute("CREATE OR REPLACE VIEW dw_v AS " + select);
        return descriptor();
    }

    private String descriptor() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.dw_v");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    /** A cast's declared width is the one it names, in every family that has one. */
    @Test
    public void aCastKeepsTheWidthItNames() {
        assertEquals("""
            {"type":"TEXT","length":10,"byteLength":40,"nullable":true,"fixed":false}""",
            declaredType("CAST(s AS VARCHAR(10))"));
        assertEquals("""
            {"type":"TEXT","length":7,"byteLength":28,"nullable":true,"fixed":false}""",
            declaredType("CAST(s AS CHAR(7))"));
        assertEquals("""
            {"type":"TEXT","length":6,"byteLength":24,"nullable":true,"fixed":false}""",
            declaredType("s::VARCHAR(6)"));
        assertEquals("""
            {"type":"BINARY","length":10,"byteLength":10,"nullable":true,"fixed":true}""",
            declaredType("CAST(b4 AS BINARY(10))"));
    }

    /** A temporal cast's parameter is its fractional-second precision, reported as the scale. */
    @Test
    public void aTemporalCastKeepsItsPrecision() {
        assertEquals("""
            {"type":"TIMESTAMP_NTZ","precision":0,"scale":3,"nullable":true}""",
            declaredType("CAST(t AS TIMESTAMP_NTZ(3))"));
        assertEquals("""
            {"type":"TIMESTAMP_LTZ","precision":0,"scale":6,"nullable":true}""",
            declaredType("CAST(t AS TIMESTAMP_LTZ(6))"));
        // Unparameterised stays at the family default of nine.
        assertEquals("""
            {"type":"TIMESTAMP_NTZ","precision":0,"scale":9,"nullable":true}""",
            declaredType("CAST(t AS TIMESTAMP_NTZ)"));
    }

    /** A bare cast still means the family maximum — the parameter is what narrows it. */
    @Test
    public void aBareCastIsStillTheFamilyMaximum() {
        assertEquals("""
            {"type":"TEXT","length":16777216,"byteLength":67108864,"nullable":true,"fixed":false}""",
            declaredType("CAST(s AS VARCHAR)"));
        assertEquals("""
            {"type":"BINARY","length":8388608,"byteLength":8388608,"nullable":true,"fixed":true}""",
            declaredType("CAST(b4 AS BINARY)"));
    }

    /** Nesting takes the OUTER width, narrowing included, and a concatenation sums what it joins. */
    @Test
    public void nestedCastsTakeTheOuterWidth() {
        assertWidth(20, "CAST(CAST(s AS VARCHAR(10)) AS VARCHAR(20))");
        assertWidth(5, "CAST(CAST(s AS VARCHAR(20)) AS VARCHAR(5))");
        assertWidth(7, "CAST(s AS VARCHAR(3)) || CAST(s AS VARCHAR(4))");
    }

    /** A hash is its digest's width — and SHA2's stays 64 whichever digest size is asked for. */
    @Test
    public void aHashIsItsDigestWide() {
        assertWidth(20, "SHA1_BINARY(s4)");
        assertWidth(16, "MD5_BINARY(s4)");
        assertWidth(64, "SHA2_BINARY(s4)");
        assertWidth(64, "SHA2_BINARY(s4, 224)");
        assertWidth(64, "SHA2_BINARY(s4, 512)");
    }

    /** A decoder's width follows its ARGUMENT's declared width, by a ratio per encoding. */
    @Test
    public void aDecoderFollowsItsArgumentsWidth() {
        assertWidth(2, "HEX_DECODE_BINARY(s4)");
        assertWidth(50, "HEX_DECODE_BINARY(s100)");
        assertWidth(2, "TRY_HEX_DECODE_BINARY(s4)");
        assertWidth(3, "BASE64_DECODE_BINARY(s4)");
        assertWidth(75, "BASE64_DECODE_BINARY(s100)");
        assertWidth(16, "STRING_AS_BINARY(s4)");
        assertWidth(400, "STRING_AS_BINARY(s100)");
        // A literal argument carries its own length, so the result is that literal's decoding.
        assertWidth(3, "HEX_DECODE_BINARY('414243')");
        assertWidth(1, "HEX_DECODE_BINARY('41')");
    }

    /** A set operation is as wide as its widest branch, whichever branch leads. */
    @Test
    public void aUnionIsAsWideAsItsWidestBranch() {
        assertEquals("""
            {"type":"TEXT","length":100,"byteLength":400,"nullable":true,"fixed":false}""",
            declaredTypeOfBody("SELECT s4 AS c FROM dw UNION ALL SELECT s100 FROM dw"));
        assertEquals("""
            {"type":"BINARY","length":8,"byteLength":8,"nullable":true,"fixed":false}""",
            declaredTypeOfBody("SELECT b4 AS c FROM dw UNION ALL SELECT b4 || b4 FROM dw"));
        assertEquals("""
            {"type":"BINARY","length":8,"byteLength":8,"nullable":true,"fixed":false}""",
            declaredTypeOfBody("SELECT b4 || b4 AS c FROM dw UNION ALL SELECT b4 FROM dw"));
    }

    /**
     * And the merged spelling follows the measured rule: two declared binaries stay fixed however far
     * apart their widths, while a branch that is not fixed takes it away — unless nothing widened.
     */
    @Test
    public void theMergedBinaryKeepsTheFixedSpellingOnlyWhenNothingWidenedIt() {
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":true}""",
            declaredTypeOfBody("SELECT b4 AS c FROM dw UNION ALL SELECT b100 FROM dw"));
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":false}""",
            declaredTypeOfBody("SELECT b4 AS c FROM dw UNION ALL SELECT b100 FROM dw"
                + " UNION ALL SELECT b4 || b4 FROM dw"));
    }

    /** Asserts a binary or string expression declares this width. */
    private void assertWidth(final int expected, final String expression) {
        final String descriptor = declaredType(expression);
        assertEquals(true, descriptor.contains("\"length\":" + expected + ","),
            expression + " should be " + expected + " wide, but declares " + descriptor);
    }
}
