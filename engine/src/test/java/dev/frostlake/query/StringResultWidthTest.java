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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How WIDE a string-producing expression is declared. Snowflake computes a width from the arguments
 * wherever it can and falls back to the 128MB conversion width where it cannot — it never answers the
 * 16MB a declared column defaults to unless a 16MB column is what went in.
 *
 * <pre>
 *   UPPER(v)      VARCHAR(15)          v is VARCHAR(5), and upper-casing is budgeted THREE characters
 *   LOWER(v)      VARCHAR(5)           lower-casing is not — it cannot lengthen
 *   TRIM / LEFT   VARCHAR(5)           the SOURCE width, not the length asked for
 *   CONCAT(v, w)  VARCHAR(14)          the widths add, and keep adding past 16MB
 *   MD5 / SHA1    VARCHAR(32) / (40)   a digest is a fixed width
 *   TO_CHAR(i)    VARCHAR(134217728)   nothing bounds it, so the maximum
 * </pre>
 *
 * <p>The rules compose: {@code UPPER(SUBSTR(t, 1, 2))} is three times 16MB, and
 * {@code MIN(UPPER(v))} is the 15 its argument carries.
 */
public class StringResultWidthTest extends BaseDatabaseTest {

    /** The width a string takes when nothing about its arguments can bound it. */
    private static final String UNBOUNDED = "VARCHAR(134217728)";

    /** A declared column's own default width, which a derived string reaches only by inheriting it. */
    private static final String SIXTEEN_MB = "VARCHAR(16777216)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sw (v VARCHAR(5), w VARCHAR(9), t TEXT, i INT,"
            + " n NUMBER(10,2), f FLOAT, d DATE, ts TIMESTAMP_NTZ, bn BINARY(5), bo BOOLEAN,"
            + " b8 BINARY(8))");
        engine.execute("INSERT INTO sw SELECT 'ab', 'cdefg', 'hi', 1, 2.50, 3.5, '2020-01-01',"
            + " '2020-01-01 10:00:00', TO_BINARY('AB'), TRUE, TO_BINARY('AABBCCDDEEFF0011')");
    }

    /** The declared type of the expression's result column, with the parameters that are comparable. */
    private String typeOf(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM sw");
        final DataType type = rs.getColumns().get(0).getDataType();
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            final NumericType numeric = (NumericType) type;
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            return type.getName() + "(" + ((BinaryType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** Upper-casing is budgeted three characters for one; lower-casing keeps the width it was given. */
    @Test
    public void caseChangingTriplesTheWidthOnlyUpwards() {
        assertEquals("VARCHAR(15)", typeOf("UPPER(v)"));
        assertEquals("VARCHAR(15)", typeOf("INITCAP(v)"));
        assertEquals("VARCHAR(15)", typeOf("INITCAP(v, ' ')"));
        assertEquals("VARCHAR(27)", typeOf("UPPER(w)"));
        assertEquals("VARCHAR(9)", typeOf("UPPER('abc')"));
        assertEquals("VARCHAR(50331648)", typeOf("UPPER(t)"));
        assertEquals("VARCHAR(5)", typeOf("LOWER(v)"));
        assertEquals(SIXTEEN_MB, typeOf("LOWER(t)"));
    }

    /** Concatenation adds the widths, and keeps adding past the 16MB a column can declare. */
    @Test
    public void concatenationAddsTheWidths() {
        assertEquals("VARCHAR(10)", typeOf("CONCAT(v, v)"));
        assertEquals("VARCHAR(14)", typeOf("CONCAT(v, w)"));
        assertEquals("VARCHAR(14)", typeOf("v || w"));
        assertEquals("VARCHAR(15)", typeOf("CONCAT(v, v, v)"));
        assertEquals("VARCHAR(6)", typeOf("CONCAT(v, 'x')"));
        assertEquals("VARCHAR(4)", typeOf("CONCAT('ab', 'cd')"));
        assertEquals("VARCHAR(16777221)", typeOf("CONCAT(v, t)"));
        assertEquals("VARCHAR(16777217)", typeOf("t || 'x'"));
        assertEquals("VARCHAR(33554432)", typeOf("t || t"));
        assertEquals(UNBOUNDED, typeOf("CONCAT(t, t, t, t, t, t, t, t)"));
    }

    /** CONCAT_WS pays for the separator once per gap, and INSERT for its base twice. */
    @Test
    public void theSeparatorIsPaidOncePerGap() {
        assertEquals("VARCHAR(15)", typeOf("CONCAT_WS(',', v, w)"));
        assertEquals("VARCHAR(21)", typeOf("CONCAT_WS(',', v, w, v)"));
        assertEquals("VARCHAR(16)", typeOf("CONCAT_WS('--', v, w)"));
        assertEquals("VARCHAR(11)", typeOf("INSERT(v, 1, 1, 'x')"));
        assertEquals("VARCHAR(19)", typeOf("INSERT(w, 1, 1, 'x')"));
        assertEquals("VARCHAR(19)", typeOf("INSERT(v, 1, 1, w)"));
    }

    /** Taking a PIECE of a string keeps the source's width — not the length asked for. */
    @Test
    public void takingAPieceKeepsTheSourceWidth() {
        assertEquals("VARCHAR(5)", typeOf("TRIM(v)"));
        assertEquals("VARCHAR(5)", typeOf("LTRIM(v)"));
        assertEquals("VARCHAR(5)", typeOf("RTRIM(v)"));
        assertEquals("VARCHAR(5)", typeOf("TRIM(v, 'a')"));
        assertEquals("VARCHAR(5)", typeOf("SUBSTR(v, 1, 2)"));
        assertEquals("VARCHAR(5)", typeOf("SUBSTR(v, 1, 10)"));
        assertEquals("VARCHAR(5)", typeOf("LEFT(v, 2)"));
        assertEquals("VARCHAR(5)", typeOf("RIGHT(v, 2)"));
        assertEquals("VARCHAR(5)", typeOf("SPLIT_PART(v, ',', 1)"));
        assertEquals("VARCHAR(5)", typeOf("REGEXP_SUBSTR(v, 'a')"));
        assertEquals("VARCHAR(5)", typeOf("REVERSE(v)"));
        assertEquals("VARCHAR(5)", typeOf("STRTOK(v, ',', 1)"));
        assertEquals(SIXTEEN_MB, typeOf("SUBSTR(t, 1, 2)"));
    }

    /** Where nothing bounds the answer the width is the 128MB maximum, never the 16MB default. */
    @Test
    public void anUnboundedResultIsTheMaximum() {
        assertEquals(UNBOUNDED, typeOf("REPEAT(v, 3)"));
        assertEquals(UNBOUNDED, typeOf("LPAD(v, 10, 'x')"));
        assertEquals(UNBOUNDED, typeOf("RPAD(v, 10, 'x')"));
        assertEquals(UNBOUNDED, typeOf("SPACE(3)"));
        assertEquals(UNBOUNDED, typeOf("REPLACE(v, 'a', 'bb')"));
        assertEquals(UNBOUNDED, typeOf("TRANSLATE(v, 'a', 'b')"));
        assertEquals(UNBOUNDED, typeOf("REGEXP_REPLACE(v, 'a', 'b')"));
        assertEquals(UNBOUNDED, typeOf("CURRENT_DATABASE()"));
        assertEquals(UNBOUNDED, typeOf("ARRAY_TO_STRING(ARRAY_CONSTRUCT(v), ',')"));
        assertEquals(UNBOUNDED, typeOf("LISTAGG(v)"));
    }

    /** A conversion to text is unbounded too — including a CAST of a string to a bare VARCHAR. */
    @Test
    public void aConversionToTextIsUnbounded() {
        assertEquals(UNBOUNDED, typeOf("CAST(i AS VARCHAR)"));
        assertEquals(UNBOUNDED, typeOf("i::VARCHAR"));
        assertEquals(UNBOUNDED, typeOf("CAST(v AS VARCHAR)"));
        assertEquals(UNBOUNDED, typeOf("TO_CHAR(i)"));
        assertEquals(UNBOUNDED, typeOf("TO_CHAR(d)"));
        assertEquals(UNBOUNDED, typeOf("TO_VARCHAR(f)"));
        assertEquals(UNBOUNDED, typeOf("TO_CHAR(i, '999')"));
        assertEquals(UNBOUNDED, typeOf("v || i"));
        assertEquals(UNBOUNDED, typeOf("v || d"));
        assertEquals(UNBOUNDED, typeOf("v || bo"));
        assertEquals("VARCHAR(4)", typeOf("CAST(i AS VARCHAR(4))"));
        assertEquals("VARCHAR(3)", typeOf("v::VARCHAR(3)"));
    }

    /** A digest, a name and a generated identifier are fixed widths whatever went in. */
    @Test
    public void aFixedWidthIgnoresItsInput() {
        assertEquals("VARCHAR(32)", typeOf("MD5(v)"));
        assertEquals("VARCHAR(32)", typeOf("MD5(t)"));
        assertEquals("VARCHAR(32)", typeOf("MD5_HEX(v)"));
        assertEquals("VARCHAR(40)", typeOf("SHA1(v)"));
        assertEquals("VARCHAR(128)", typeOf("SHA2(v)"));
        assertEquals("VARCHAR(128)", typeOf("SHA2(v, 256)"));
        assertEquals("VARCHAR(7)", typeOf("SOUNDEX(v)"));
        assertEquals("VARCHAR(36)", typeOf("UUID_STRING()"));
        assertEquals("VARCHAR(1)", typeOf("CHR(65)"));
        assertEquals("VARCHAR(3)", typeOf("DAYNAME(d)"));
        assertEquals("VARCHAR(3)", typeOf("MONTHNAME(d)"));
        assertEquals("VARCHAR(0)", typeOf("TRY_TO_UUID(v)"));
    }

    /** Encoding counts BYTES, and a character is budgeted four of them. */
    @Test
    public void encodingCountsBytesNotCharacters() {
        assertEquals("VARCHAR(10)", typeOf("HEX_ENCODE(bn)"));
        assertEquals("VARCHAR(16)", typeOf("HEX_ENCODE(b8)"));
        assertEquals("VARCHAR(40)", typeOf("HEX_ENCODE(v)"));
        assertEquals("VARCHAR(72)", typeOf("HEX_ENCODE(w)"));
        assertEquals("VARCHAR(8)", typeOf("BASE64_ENCODE(bn)"));
        assertEquals("VARCHAR(12)", typeOf("BASE64_ENCODE(b8)"));
        assertEquals("VARCHAR(28)", typeOf("BASE64_ENCODE(v)"));
        assertEquals("VARCHAR(2)", typeOf("TRY_HEX_DECODE_STRING(v)"));
        assertEquals("VARCHAR(4)", typeOf("TRY_HEX_DECODE_STRING(w)"));
        assertEquals("VARCHAR(3)", typeOf("TRY_BASE64_DECODE_STRING(v)"));
        assertEquals("VARCHAR(6)", typeOf("TRY_BASE64_DECODE_STRING(w)"));
    }

    /** NULLIF keeps its FIRST argument's flavour, widened by the second only within that family. */
    @Test
    public void nullIfKeepsTheFirstArgumentsFlavour() {
        assertEquals("VARCHAR(9)", typeOf("NULLIF(v, w)"));
        assertEquals("VARCHAR(9)", typeOf("NULLIF(w, v)"));
        assertEquals("VARCHAR(15)", typeOf("NULLIF(UPPER(v), w)"));
        assertEquals("NUMBER(38,2)", typeOf("NULLIF(i, n)"));
        assertEquals("NUMBER(38,0)", typeOf("NULLIF(i, f)"));
        assertEquals("DATE", typeOf("NULLIF(d, ts)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("NULLIF(ts, d)"));
        assertEquals("BINARY(5)", typeOf("NULLIF(bn, bn)"));
        assertEquals("VARCHAR(0)", typeOf("NULLIF(NULL, v)"));
        assertEquals(UNBOUNDED, typeOf("NULLIF(v, NULL)"));
        assertEquals(UNBOUNDED, typeOf("NULLIF(v, bo)"));
    }

    /**
     * The width survives a VIEW, a derived table and a CTE — the RESULT column is never clamped, though
     * the COLUMN metadata over the same view stops at the 16MB a column can declare (DerivedWidthTest
     * holds that side).
     */
    @Test
    public void theWidthSurvivesEveryDerivedRelation() {
        engine.execute("CREATE OR REPLACE VIEW sw_v AS SELECT t || t c2, UPPER(v) c4,"
            + " CAST(i AS VARCHAR) c1 FROM sw");
        assertEquals("VARCHAR(33554432)", typeOf2("SELECT c2 FROM sw_v"));
        assertEquals("VARCHAR(15)", typeOf2("SELECT c4 FROM sw_v"));
        assertEquals(UNBOUNDED, typeOf2("SELECT c1 FROM sw_v"));
        assertEquals("VARCHAR(45)", typeOf2("SELECT UPPER(c4) FROM sw_v"));
        assertEquals("VARCHAR(33554433)", typeOf2("SELECT c2 || 'x' FROM sw_v"));
        assertEquals("VARCHAR(33554432)", typeOf2("SELECT c FROM (SELECT t || t c FROM sw) x"));
        assertEquals("VARCHAR(33554432)",
            typeOf2("WITH q AS (SELECT t || t c FROM sw) SELECT c FROM q"));
        assertEquals(UNBOUNDED,
            typeOf2("WITH q AS (SELECT CAST(i AS VARCHAR) c FROM sw) SELECT c FROM q"));
    }

    /** The declared type of a whole statement's first result column. */
    private String typeOf2(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final DataType type = rs.getColumns().get(0).getDataType();
        return type instanceof StringType
            ? type.getName() + "(" + ((StringType) type).getMaxLength() + ")"
            : String.valueOf(type == null ? "null" : type.getName());
    }

    /** The rules COMPOSE — through nesting, through an aggregate and through a conditional. */
    @Test
    public void theWidthsCompose() {
        assertEquals("VARCHAR(45)", typeOf("UPPER(UPPER(v))"));
        assertEquals("VARCHAR(42)", typeOf("UPPER(v || w)"));
        assertEquals("VARCHAR(42)", typeOf("CONCAT(UPPER(v), UPPER(w))"));
        assertEquals("VARCHAR(50331648)", typeOf("UPPER(SUBSTR(t, 1, 2))"));
        assertEquals("VARCHAR(50331653)", typeOf("UPPER(t) || v"));
        assertEquals("VARCHAR(5)", typeOf("MAX(v)"));
        assertEquals("VARCHAR(15)", typeOf("MIN(UPPER(v))"));
        assertEquals("VARCHAR(15)", typeOf("IFF(bo, UPPER(v), w)"));
        assertEquals("VARCHAR(27)", typeOf("COALESCE(UPPER(v), UPPER(w))"));
    }
}
