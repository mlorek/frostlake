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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A whole number keeps the VARIANT kind of its own scale: 3.00 out of a NUMBER(10,2) is DECIMAL wherever it
 * becomes a VARIANT — TO_VARIANT, ::VARIANT, ARRAY_CONSTRUCT, OBJECT_CONSTRUCT, ARRAY_AGG, OBJECT_AGG, a stored
 * VARIANT column, FLATTEN — though its text is the descaled 3 and it equals the INTEGER 3. A scale-0 value is
 * INTEGER, a literal 3.00 is a NUMBER(1,0) and so INTEGER, and so is a whole JSON text. A member read back
 * out of a container is a VARIANT holding that DECIMAL: it prints, compares, casts and concatenates as 3, and
 * keeps its kind when it is embedded again. Every cell is live-verified.
 */
public class DecimalVariantKindTest extends BaseDatabaseTest {

    private static final String FROM = " FROM tv ORDER BY i";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tv (d NUMBER(10,2), i NUMBER(38,0), e NUMBER(38,10))");
        engine.execute("INSERT INTO tv VALUES (3.00, 4, 5), (4.50, 7, 6.25), (5.00, 9, 7)");
    }

    /** Rows joined by " | ", columns by ", ", a BOOLEAN spelled the account's way. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    out.append(", ");
                }
                final String text = String.valueOf(row.getValue(c));
                out.append("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)
                    ? text.toUpperCase(Locale.ROOT) : text);
            }
        }
        return out.toString();
    }

    private static String thrice(final String row) {
        return row + " | " + row + " | " + row;
    }

    @Test
    public void aScaledWholeNumberIsADecimalVariant() {
        assertEquals(thrice("DECIMAL, DECIMAL, DECIMAL"), rows(
            "SELECT TYPEOF(TO_VARIANT(d)), TYPEOF(ARRAY_CONSTRUCT(d)[0]), TYPEOF(OBJECT_CONSTRUCT('a', d):a)" + FROM));
        assertEquals(thrice("DECIMAL, DECIMAL, DECIMAL, DECIMAL"), rows("""
            SELECT TYPEOF(TO_VARIANT(d + 0)), TYPEOF(TO_VARIANT(d * 1)), TYPEOF(TO_VARIANT(CAST(d AS NUMBER(10,2)))),
                TYPEOF(TO_VARIANT(i::NUMBER(10,2)))""" + FROM));
        assertEquals(thrice("INTEGER, DECIMAL, DECIMAL, INTEGER"), rows(
            "SELECT TYPEOF(TO_VARIANT(i)), TYPEOF(TO_VARIANT(e)), TYPEOF(d::VARIANT), TYPEOF(TO_VARIANT(d::NUMBER(10,0)))"
                + FROM));
        assertEquals("DECIMAL, INTEGER | DECIMAL, DECIMAL | DECIMAL, INTEGER",
            rows("SELECT TYPEOF(GET(ARRAY_CONSTRUCT(d), 0)), TYPEOF(PARSE_JSON(TO_JSON(TO_VARIANT(d))))" + FROM));
        assertEquals(thrice("INTEGER, INTEGER, INTEGER, DECIMAL"), rows(
            "SELECT TYPEOF(ROUND(d)::VARIANT), TYPEOF(ROUND(d, 0)::VARIANT), TYPEOF(FLOOR(d)::VARIANT), TYPEOF(TO_VARIANT(d / 1))"
                + FROM));
        assertEquals(thrice("DECIMAL, DECIMAL, DECIMAL, DOUBLE"), rows(
            "SELECT TYPEOF(TO_VARIANT(IFF(i > 0, d, d))), TYPEOF(TO_VARIANT(-d)), TYPEOF(TO_VARIANT(ABS(d))), TYPEOF(TO_VARIANT(d::FLOAT))"
                + FROM));
    }

    @Test
    public void itsTextIsDescaledAndItEqualsTheInteger() {
        assertEquals("3, 3, 5, [3], {\"a\":3} | 4.5, 4.5, 6.25, [4.5], {\"a\":4.5} | 5, 5, 7, [5], {\"a\":5}", rows(
            "SELECT TO_VARIANT(d), TO_JSON(TO_VARIANT(d)), TO_VARIANT(e), ARRAY_CONSTRUCT(d)::VARCHAR, OBJECT_CONSTRUCT('a', d)::VARCHAR"
                + FROM));
        assertEquals("TRUE, TRUE, TRUE | FALSE, FALSE, FALSE | FALSE, FALSE, FALSE", rows(
            "SELECT TO_VARIANT(d) = TO_VARIANT(3), ARRAY_CONTAINS(3::VARIANT, ARRAY_CONSTRUCT(d)), ARRAY_CONSTRUCT(d) = ARRAY_CONSTRUCT(3)"
                + FROM));
    }

    @Test
    public void theKindDecidesTheExtractors() {
        assertEquals(thrice("TRUE, FALSE, TRUE, TRUE"), rows(
            "SELECT IS_DECIMAL(TO_VARIANT(d)), IS_INTEGER(TO_VARIANT(d)), IS_INTEGER(TO_VARIANT(i)), IS_DECIMAL(TO_VARIANT(i))"
                + FROM));
        assertEquals("null, 3, 3, 3.00 | null, 5, 5, 4.50 | null, 5, 5, 5.00", rows(
            "SELECT AS_INTEGER(TO_VARIANT(d)), AS_DECIMAL(TO_VARIANT(d)), AS_NUMBER(TO_VARIANT(d)), AS_DECIMAL(TO_VARIANT(d), 10, 2)"
                + FROM));
    }

    @Test
    public void aggregatesStorageAndFlattenKeepTheKind() {
        assertEquals("DECIMAL, [3,4.5,5]", rows(
            "SELECT TYPEOF(ARRAY_AGG(d) WITHIN GROUP (ORDER BY i)[0]), ARRAY_AGG(d) WITHIN GROUP (ORDER BY i)::VARCHAR FROM tv"));
        assertEquals("DECIMAL", rows("SELECT TYPEOF(OBJECT_AGG(TO_VARCHAR(i), d::VARIANT):\"4\") FROM tv"));
        assertEquals("DECIMAL, DECIMAL, DECIMAL, DECIMAL", rows(
            "SELECT TYPEOF(TO_VARIANT(SUM(d))), TYPEOF(TO_VARIANT(AVG(i))), TYPEOF(TO_VARIANT(MAX(d))), TYPEOF(TO_VARIANT(MIN(e))) FROM tv"));
        engine.execute("CREATE TABLE vt (v VARIANT)");
        engine.execute("INSERT INTO vt SELECT TO_VARIANT(d) FROM tv");
        assertEquals("DECIMAL, 3 | DECIMAL, 4.5 | DECIMAL, 5", rows("SELECT TYPEOF(v), v FROM vt ORDER BY v"));
        assertEquals("DECIMAL | DECIMAL | DECIMAL",
            rows("SELECT TYPEOF(f.value) FROM tv, LATERAL FLATTEN(ARRAY_CONSTRUCT(d)) f ORDER BY i"));
    }

    @Test
    public void anExtractedMemberIsAVariantHoldingTheDecimal() {
        assertEquals("3, 3 | 4.5, 4.5 | 5, 5", rows("SELECT ARRAY_CONSTRUCT(d)[0], OBJECT_CONSTRUCT('a', d):a" + FROM));
        assertEquals("3, DECIMAL, DECIMAL | 4.5, DECIMAL, DECIMAL | 5, DECIMAL, DECIMAL", rows(
            "SELECT OBJECT_CONSTRUCT('a', d):a, TYPEOF(GET_PATH(OBJECT_CONSTRUCT('a', d), 'a')), TYPEOF(GET(OBJECT_CONSTRUCT('a', d), 'a'))"
                + FROM));
        assertEquals("3x, 1, 3 | 4.5x, 3, 4.5 | 5x, 1, 5", rows(
            "SELECT ARRAY_CONSTRUCT(d)[0] || 'x', LENGTH(ARRAY_CONSTRUCT(d)[0]), ARRAY_CONSTRUCT(d)[0]::VARCHAR" + FROM));
        assertEquals(thrice("DECIMAL, DECIMAL"),
            rows("SELECT TYPEOF(ARRAY_CONSTRUCT(d)[0]::VARIANT), TYPEOF(TO_VARIANT(ARRAY_CONSTRUCT(d)[0]))" + FROM));
        assertEquals("[3], DECIMAL | [4.5], DECIMAL | [5], DECIMAL", rows(
            "SELECT ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(d)[0])::VARCHAR, TYPEOF(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(d)[0])[0])" + FROM));
        assertEquals("TRUE, 3.00, FLOAT[DOUBLE] | FALSE, 4.50, FLOAT[DOUBLE] | FALSE, 5.00, FLOAT[DOUBLE]", rows(
            "SELECT ARRAY_CONSTRUCT(d)[0] = 3, ARRAY_CONSTRUCT(d)[0]::NUMBER(10,2), SYSTEM$TYPEOF(ARRAY_CONSTRUCT(d)[0] + 1)" + FROM));
        assertEquals("FALSE, n, TRUE | TRUE, n, FALSE | TRUE, y, TRUE", rows(
            "SELECT ARRAY_CONSTRUCT(d)[0] > 4, IFF(ARRAY_CONSTRUCT(d)[0] = 5, 'y', 'n'), ARRAY_CONSTRUCT(d)[0] IN (3, 5)" + FROM));
        assertEquals("5, DECIMAL, 3", rows(
            "SELECT MAX(ARRAY_CONSTRUCT(d)[0]), TYPEOF(MAX(ARRAY_CONSTRUCT(d)[0])), MIN(OBJECT_CONSTRUCT('a', d):a) FROM tv"));
        assertEquals("12.5, 5, DECIMAL",
            rows("SELECT SUM(f.value), MAX(f.value), TYPEOF(MAX(f.value)) FROM tv, LATERAL FLATTEN(ARRAY_CONSTRUCT(d)) f"));
        assertEquals("3, DECIMAL | 4.5, DECIMAL | 5, DECIMAL", rows(
            "SELECT f.value, TYPEOF(f.value::NUMBER(10,2)) FROM tv, LATERAL FLATTEN(ARRAY_CONSTRUCT(d)) f ORDER BY i"));
    }

    @Test
    public void literalsAndJsonTextStayIntegers() {
        assertEquals("INTEGER, DECIMAL, DECIMAL, NUMBER(1,0)[SB1], NUMBER(2,1)[SB1]", rows(
            "SELECT TYPEOF(TO_VARIANT(3.00)), TYPEOF(TO_VARIANT(3.50)), TYPEOF(TO_VARIANT(3.10)), SYSTEM$TYPEOF(3.00), SYSTEM$TYPEOF(3.50)"));
        assertEquals("DECIMAL, DECIMAL, DECIMAL", rows(
            "SELECT TYPEOF(TO_VARIANT(3.00::NUMBER(10,2))), TYPEOF(TO_VARIANT(0.00::NUMBER(10,2))), TYPEOF(TO_VARIANT(-3.00::NUMBER(5,2)))"));
        assertEquals("INTEGER, INTEGER, 3, INTEGER",
            rows("SELECT TYPEOF(PARSE_JSON('3.00')), TYPEOF(PARSE_JSON('3')), PARSE_JSON('3.00'), TYPEOF(PARSE_JSON('[3.00]')[0])"));
    }
}
