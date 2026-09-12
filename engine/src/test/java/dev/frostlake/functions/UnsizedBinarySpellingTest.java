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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binary the plan never sized — the result of TO_BINARY, a cast to a bare BINARY or VARBINARY, the
 * crypto and compression families, a SQL UDF — is named bare {@code BINARY} by SYSTEM$TYPEOF and
 * {@code BINARY(67108864)} by an argument-type refusal, while a sized one keeps its own width on both
 * surfaces. A fold takes its LEADING binary branch's spelling and widens a sized lead that meets an
 * unsized branch to the 64MB maximum; a concatenation over an unsized operand is unsized; a piece of an
 * unsized binary is at the maximum; and a column stored from one is the 8MB column default. Every cell
 * is live-verified.
 */
public class UnsizedBinarySpellingTest extends BaseDatabaseTest {

    private static final String UNSIZED = "BINARY[LOB]";
    private static final String MAXIMUM = "BINARY(67108864)[LOB]";
    private static final String DEFAULT = "BINARY(8388608)[LOB]";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE bt (vb VARBINARY, b5 BINARY(5), bb BINARY, s VARCHAR(10))");
        engine.execute("INSERT INTO bt SELECT TO_BINARY('00'), TO_BINARY('0102'), TO_BINARY('AB'), '00'");
        engine.execute("INSERT INTO bt SELECT TO_BINARY('0000'), TO_BINARY('010203'), TO_BINARY('ABCD'), '0102'");
    }

    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        return out;
    }

    private String typeOf(final String expression) {
        final List<String> answer = rows("SELECT SYSTEM$TYPEOF(" + expression + ")");
        return answer.size() == 1 ? answer.get(0) : String.valueOf(answer);
    }

    /** The typeof of every row of {@code expression} over {@code bt}, which holds two. */
    private List<String> overTable(final String expression) {
        return rows("SELECT SYSTEM$TYPEOF(" + expression + ") FROM bt");
    }

    private static List<String> twice(final String cell) {
        return Collections.nCopies(2, cell);
    }

    /** The refusal {@code sql} earns, on one line, or a marker when it runs. */
    private String refusalOf(final String sql) {
        try {
            engine.executeQuery(sql);
            return "(no refusal)";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Asserts that an ABS refusal of {@code sql} names its argument as {@code argumentType}. */
    private void refusalNames(final String sql, final String argumentType) {
        final String refusal = refusalOf(sql);
        final String expected = "Invalid argument types for function 'ABS': (" + argumentType + ")";
        assertTrue(refusal.contains(expected), sql + " -> " + refusal);
    }

    @Test
    public void aConversionIsUnsized() {
        for (final String conversion : List.of("TO_BINARY('00')", "TO_BINARY('00', 'HEX')",
                "TO_BINARY('AA==', 'BASE64')", "TO_BINARY('abc', 'UTF-8')", "TRY_TO_BINARY('00')",
                "TRY_TO_BINARY('00', 'HEX')", "X'00'::BINARY", "X'00'::VARBINARY", "CAST(X'00' AS BINARY)",
                "CAST('00' AS BINARY)", "'00'::BINARY", "'00'::VARBINARY", "TRY_CAST('00' AS BINARY)",
                "NULL::BINARY", "TO_BINARY(NULL)", "TO_BINARY('00')::BINARY::VARBINARY", "COMPRESS('abc', 'SNAPPY')",
                "DECOMPRESS_BINARY(COMPRESS('abc', 'SNAPPY'), 'SNAPPY')", "AS_BINARY(TO_VARIANT(X'00'))",
                "PARSE_JSON('\"00\"')::BINARY", "ENCRYPT('a', 'p')", "DECRYPT(X'00', 'p')", "TRY_DECRYPT(X'00', 'p')",
                "DECRYPT_RAW(X'00', X'00', X'00')", "TRY_DECRYPT_RAW(X'00', X'00', X'00')", "(SELECT TO_BINARY('00'))")) {
            assertEquals(UNSIZED, typeOf(conversion), conversion);
        }
        assertEquals(twice(UNSIZED), overTable("TO_BINARY(s)"));
        assertEquals(twice(UNSIZED), overTable("TO_BINARY(s, 'UTF-8')"));
        assertEquals(twice(UNSIZED), overTable("TO_BINARY('00')"));
        assertEquals(twice(UNSIZED), overTable("s::BINARY"));
        assertEquals(twice(UNSIZED), overTable("b5::BINARY"));
        assertEquals(twice(UNSIZED), overTable("b5::VARBINARY"));
        assertEquals(twice(UNSIZED), overTable("vb::BINARY"));
        assertEquals(twice(UNSIZED), overTable("TO_BINARY(b5)"));
        assertEquals(twice(UNSIZED), overTable("MIN(TO_BINARY(s))"));
        assertEquals(twice(UNSIZED), overTable("MAX(TO_BINARY(s))"));
        assertEquals(twice(UNSIZED), overTable("ANY_VALUE(TO_BINARY(s))"));
        assertEquals(twice(UNSIZED), overTable("LAG(TO_BINARY(s)) OVER (ORDER BY s)"));
    }

    @Test
    public void aSizedBinaryKeepsItsWidth() {
        assertEquals("BINARY(1)[LOB]", typeOf("X'00'"));
        assertEquals("BINARY(3)[LOB]", typeOf("X'00'::BINARY(3)"));
        assertEquals("BINARY(3)[LOB]", typeOf("X'00'::VARBINARY(3)"));
        assertEquals(DEFAULT, typeOf("X'00'::BINARY(8388608)"));
        assertEquals(MAXIMUM, typeOf("CAST(X'00' AS BINARY(67108864))"));
        assertEquals("BINARY(4)[LOB]", typeOf("TO_BINARY('00')::BINARY(4)"));
        assertEquals(DEFAULT, typeOf("CAST(TO_BINARY('00') AS BINARY(8388608))"));
        assertEquals("BINARY(16)[LOB]", typeOf("MD5_BINARY('a')"));
        assertEquals("BINARY(20)[LOB]", typeOf("SHA1_BINARY('a')"));
        assertEquals("BINARY(64)[LOB]", typeOf("SHA2_BINARY('a')"));
        assertEquals("BINARY(2)[LOB]", typeOf("X'00' || X'01'"));
        assertEquals("BINARY(2)[LOB]", typeOf("IFF(TRUE, X'00', X'0102')"));
        assertEquals(twice(DEFAULT), overTable("vb"));
        assertEquals(twice("BINARY(5)[LOB]"), overTable("b5"));
        assertEquals(twice(DEFAULT), overTable("bb"));
        assertEquals(twice("BINARY(10)[LOB]"), overTable("b5 || b5"));
        assertEquals(twice("BINARY(5)[LOB]"), overTable("SUBSTR(b5, 1, 1)"));
        assertEquals(twice(DEFAULT), overTable("SUBSTR(vb, 1, 1)"));
        assertEquals(twice("BINARY(5)[LOB]"), overTable("MAX(b5)"));
        assertEquals(twice(DEFAULT), overTable("MIN(vb)"));
        assertEquals(twice("BINARY(5)[LOB]"), overTable("FIRST_VALUE(b5) OVER (ORDER BY s)"));
        assertEquals(twice(DEFAULT), overTable("IFF(TRUE, vb, vb)"));
        assertEquals(twice(DEFAULT), overTable("COALESCE(b5, vb)"), "a fold of sized widths");
        assertEquals(twice(DEFAULT), overTable("IFF(s = '00', b5, vb)"));
        assertEquals(twice("BINARY(5)[LOB]"), overTable("IFF(s = '00', b5, X'00')"));
        assertEquals(twice("BINARY(5)[LOB]"), overTable("COALESCE(b5, b5)"));
        assertEquals(DEFAULT, typeOf("(SELECT vb FROM bt LIMIT 1)"));
    }

    @Test
    public void aFoldTakesItsLeadingBranchSpelling() {
        assertEquals(UNSIZED, typeOf("IFF(TRUE, TO_BINARY('00'), X'00')"));
        assertEquals(MAXIMUM, typeOf("IFF(TRUE, X'00', TO_BINARY('00'))"));
        assertEquals(UNSIZED, typeOf("COALESCE(TO_BINARY('00'), X'00')"));
        assertEquals(MAXIMUM, typeOf("COALESCE(X'00', TO_BINARY('00'))"));
        assertEquals(MAXIMUM, typeOf("COALESCE(CAST(X'00' AS BINARY(67108864)), TO_BINARY('00'))"));
        for (final String unsized : List.of("COALESCE(TO_BINARY(s), b5)", "COALESCE(TO_BINARY(s), TO_BINARY(s))",
                "COALESCE(TO_BINARY(s), X'00')", "IFF(s = '00', TO_BINARY(s), TO_BINARY(s))",
                "IFF(s = '00', TO_BINARY(s), X'00')", "NVL(TO_BINARY(s), b5)",
                "CASE WHEN s = '00' THEN TO_BINARY(s) ELSE b5 END", "COALESCE(NULL, TO_BINARY(s), b5)",
                "IFF(s = '00', NULL, TO_BINARY(s))", "IFF(s = '00', TO_BINARY(s), NULL)",
                "DECODE(s, '00', TO_BINARY(s), b5)", "NULLIF(TO_BINARY(s), b5)", "NVL2(s, TO_BINARY(s), b5)")) {
            assertEquals(twice(UNSIZED), overTable(unsized), unsized);
        }
        for (final String maximum : List.of("COALESCE(b5, TO_BINARY('00'))", "COALESCE(vb, TO_BINARY('00'))",
                "IFF(s = '00', X'00', TO_BINARY(s))", "DECODE(s, '00', b5, TO_BINARY(s))", "NULLIF(b5, TO_BINARY(s))",
                "NVL2(s, b5, TO_BINARY(s))", "COALESCE(b5, vb, TO_BINARY(s))", "COALESCE(b5, TO_BINARY(s), vb)",
                "COALESCE(COALESCE(b5, TO_BINARY(s)), b5)")) {
            assertEquals(twice(MAXIMUM), overTable(maximum), maximum);
        }
    }

    @Test
    public void aConcatenationOverAnUnsizedOperandIsUnsizedAndAPieceOfOneIsAtTheMaximum() {
        assertEquals(UNSIZED, typeOf("X'00' || TO_BINARY('00')"));
        assertEquals(UNSIZED, typeOf("TO_BINARY('00') || TO_BINARY('00')"));
        assertEquals(UNSIZED, typeOf("CAST(X'00' AS BINARY(67108864)) || X'00'"), "past the maximum");
        assertEquals(twice(UNSIZED), overTable("b5 || TO_BINARY(s)"));
        assertEquals(twice(UNSIZED), overTable("COALESCE(b5, TO_BINARY(s)) || b5"));
        assertEquals(MAXIMUM, typeOf("SUBSTR(TO_BINARY('0011'), 1, 1)"));
        assertEquals(twice(MAXIMUM), overTable("SUBSTR(TO_BINARY(s), 1, 1)"));
        assertEquals(twice(MAXIMUM), overTable("LEFT(TO_BINARY(s), 1)"));
        assertEquals(twice(MAXIMUM), overTable("SUBSTR(COALESCE(b5, TO_BINARY(s)), 1, 1)"));
    }

    @Test
    public void theSpellingRidesThroughARelationButNotIntoAStoredColumn() {
        engine.execute("CREATE OR REPLACE VIEW binv AS SELECT TO_BINARY(s) AS x, b5 FROM bt");
        engine.execute("CREATE OR REPLACE TABLE binct AS SELECT TO_BINARY(s) AS x, COALESCE(b5, TO_BINARY(s)) AS y FROM bt");
        engine.execute("CREATE OR REPLACE FUNCTION fb() RETURNS BINARY AS 'TO_BINARY(''00'')'");
        engine.execute("CREATE OR REPLACE FUNCTION fb5() RETURNS BINARY(5) AS 'TO_BINARY(''00'')'");
        engine.execute("CREATE OR REPLACE FUNCTION fvb() RETURNS VARBINARY AS 'TO_BINARY(''00'')'");
        assertEquals(List.of(UNSIZED), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT TO_BINARY('00') AS x)"));
        assertEquals(List.of(UNSIZED), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT X'00'::BINARY AS x)"));
        assertEquals(twice(UNSIZED), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT TO_BINARY(s) AS x FROM bt)"));
        assertEquals(twice(MAXIMUM),
            rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT COALESCE(b5, TO_BINARY(s)) AS x FROM bt)"));
        assertEquals(twice(MAXIMUM),
            rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT SUBSTR(TO_BINARY(s), 1, 1) AS x FROM bt)"));
        assertEquals(twice(UNSIZED), rows("WITH c AS (SELECT TO_BINARY(s) AS x FROM bt) SELECT SYSTEM$TYPEOF(x) FROM c"));
        assertEquals(twice(UNSIZED), rows("SELECT SYSTEM$TYPEOF(x) FROM binv"));
        assertEquals(twice("BINARY(5)[LOB]"), rows("SELECT SYSTEM$TYPEOF(b5) FROM binv"));
        assertEquals(twice(UNSIZED), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT TO_BINARY('00') AS x UNION ALL SELECT X'00')"));
        assertEquals(twice(UNSIZED),
            rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT TO_BINARY('00') AS x UNION ALL SELECT TO_BINARY('01'))"));
        assertEquals(twice(MAXIMUM), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT X'00' AS x UNION ALL SELECT TO_BINARY('01'))"));
        assertEquals(Collections.nCopies(4, MAXIMUM),
            rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT b5 AS x FROM bt UNION ALL SELECT TO_BINARY(s) FROM bt)"));
        assertEquals(Collections.nCopies(4, DEFAULT),
            rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT b5 AS x FROM bt UNION ALL SELECT vb FROM bt)"));
        assertEquals(UNSIZED, typeOf("fb()"), "a SQL UDF is typed by its body");
        assertEquals(UNSIZED, typeOf("fb5()"));
        assertEquals(UNSIZED, typeOf("fvb()"));
        assertEquals(twice(DEFAULT), rows("SELECT SYSTEM$TYPEOF(x) FROM binct"), "stored at the column default");
        assertEquals(twice(DEFAULT), rows("SELECT SYSTEM$TYPEOF(y) FROM binct"));
    }

    @Test
    public void aRefusalNamesAnUnsizedBinaryAtTheMaximum() {
        engine.execute("CREATE OR REPLACE VIEW binv AS SELECT TO_BINARY(s) AS x, b5 FROM bt");
        engine.execute("CREATE OR REPLACE FUNCTION fb() RETURNS BINARY AS 'TO_BINARY(''00'')'");
        engine.execute("CREATE OR REPLACE FUNCTION fb5() RETURNS BINARY(5) AS 'TO_BINARY(''00'')'");
        final String maximum = "BINARY(67108864)";
        final String column = "BINARY(8388608)";
        refusalNames("SELECT ABS(TO_BINARY('00'))", maximum);
        refusalNames("SELECT ABS(X'00'::BINARY)", maximum);
        refusalNames("SELECT ABS(TO_BINARY(s)) FROM bt", maximum);
        refusalNames("SELECT ABS(COALESCE(b5, TO_BINARY('00'))) FROM bt", maximum);
        refusalNames("SELECT ABS(COALESCE(TO_BINARY(s), TO_BINARY(s))) FROM bt", maximum);
        refusalNames("SELECT ABS(COALESCE(b5, vb, TO_BINARY(s))) FROM bt", maximum);
        refusalNames("SELECT ABS(X'00' || TO_BINARY('00'))", maximum);
        refusalNames("SELECT ABS(SUBSTR(TO_BINARY(s), 1, 1)) FROM bt", maximum);
        refusalNames("SELECT ABS(ENCRYPT('a', 'p'))", maximum);
        refusalNames("SELECT ABS(x) FROM (SELECT TO_BINARY('00') AS x)", maximum);
        refusalNames("SELECT ABS(x) FROM (SELECT TO_BINARY('00') AS x UNION ALL SELECT X'00')", maximum);
        refusalNames("SELECT ABS(x) FROM binv", maximum);
        refusalNames("SELECT ABS((SELECT TO_BINARY('00')))", maximum);
        refusalNames("SELECT ABS(MIN(TO_BINARY(s))) FROM bt", maximum);
        refusalNames("SELECT ABS(fb())", maximum);
        refusalNames("SELECT ABS(fb5())", maximum);
        refusalNames("SELECT ABS(b5) FROM bt", "BINARY(5)");
        refusalNames("SELECT ABS(vb) FROM bt", column);
        refusalNames("SELECT ABS(COALESCE(b5, vb)) FROM bt", column);
        refusalNames("SELECT ABS(IFF(s = '00', b5, vb)) FROM bt", column);
        refusalNames("SELECT ABS(X'00'::BINARY(8388608))", column);
        refusalNames("SELECT ABS(x) FROM (SELECT b5 AS x FROM bt UNION ALL SELECT vb FROM bt)", column);
        refusalNames("SELECT ABS((SELECT vb FROM bt LIMIT 1))", column);
        refusalNames("SELECT ABS(MIN(vb)) FROM bt", column);
    }

    @Test
    public void aDecoderIsSizedInWholeGroups() {
        engine.execute("""
            CREATE OR REPLACE TABLE dw (s1 VARCHAR(1), s5 VARCHAR(5), s6 VARCHAR(6), s7 VARCHAR(7), s9 VARCHAR(9),
                sv VARCHAR, v VARIANT)""");
        engine.execute("INSERT INTO dw SELECT 'A', 'AAAA', 'AAAA', 'AAAA', 'AAAA', 'AAAA', TO_VARIANT('AAAA')");
        engine.execute("INSERT INTO dw SELECT 'B', 'BBBB', 'BBBB', 'BBBB', 'BBBB', 'BBBB', TO_VARIANT('BBBB')");
        final String hex = "SELECT SYSTEM$TYPEOF(HEX_DECODE_BINARY(";
        final String base64 = "SELECT SYSTEM$TYPEOF(BASE64_DECODE_BINARY(";
        assertEquals(twice("BINARY(0)[LOB]"), rows(hex + "s1)) FROM dw"));
        assertEquals(twice("BINARY(2)[LOB]"), rows(hex + "s5)) FROM dw"));
        assertEquals(twice("BINARY(3)[LOB]"), rows(hex + "s7)) FROM dw"));
        assertEquals(twice(DEFAULT), rows(hex + "sv)) FROM dw"));
        assertEquals(twice(MAXIMUM), rows(hex + "v)) FROM dw"), "a VARIANT's width is unknown");
        assertEquals(twice("BINARY(3)[LOB]"), rows("SELECT SYSTEM$TYPEOF(TRY_HEX_DECODE_BINARY(s7)) FROM dw"));
        assertEquals(twice("BINARY(0)[LOB]"), rows(base64 + "s1)) FROM dw"));
        assertEquals(twice("BINARY(3)[LOB]"), rows(base64 + "s5)) FROM dw"));
        assertEquals(twice("BINARY(3)[LOB]"), rows(base64 + "s6)) FROM dw"));
        assertEquals(twice("BINARY(3)[LOB]"), rows(base64 + "s7)) FROM dw"));
        assertEquals(twice("BINARY(6)[LOB]"), rows(base64 + "s9)) FROM dw"));
        assertEquals(twice("BINARY(12582912)[LOB]"), rows(base64 + "sv)) FROM dw"), "past a column's 8MB");
        assertEquals(twice(UNSIZED), rows(base64 + "v)) FROM dw"));
        assertEquals(twice("BINARY(3)[LOB]"), rows("SELECT SYSTEM$TYPEOF(TRY_BASE64_DECODE_BINARY(s7)) FROM dw"));
        assertEquals(twice("BINARY(6)[LOB]"), overTable("BASE64_DECODE_BINARY(s)"));
        assertEquals("BINARY(3)[LOB]", typeOf("BASE64_DECODE_BINARY('AAAAAAA')"));
        assertEquals("BINARY(3)[LOB]", typeOf("BASE64_DECODE_BINARY('AA==')"));
        assertEquals("BINARY(0)[LOB]", typeOf("HEX_DECODE_BINARY('0')"));
        assertEquals("BINARY(1)[LOB]", typeOf("HEX_DECODE_BINARY('00')"));
    }
}
