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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A DOUBLE inside a semi-structured value has ONE text on the account, the fifteen-decimal scientific
 * form ({@code [1.000000000000000e+00]}): displayed, and converted by TO_JSON, {@code ::VARCHAR} or
 * TO_VARCHAR alike. Frostlake rendered all of them from the canonical text, in Java's shortest form.
 *
 * <p>★ THE FAMILY DECIDES, NOT THE VALUE — a DECIMAL and an INTEGER convert exactly as they display, so
 * {@code [1.5]} and {@code [1]} pass through untouched. That is what makes this one family's rule rather
 * than a number format, and it is why a plain {@code 3.141592653589793} (no exponent written, therefore
 * DECIMAL) keeps all of its digits while a FLOAT-cast 1.0 does not.
 */
public class JsonDoubleConversionTextTest extends BaseDatabaseTest {

    private String cellOf(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aDoubleIsRewrittenInFifteenDecimalScientificNotation() {
        assertEquals("[1.000000000000000e+00]", cellOf("SELECT TO_JSON(ARRAY_CONSTRUCT(1.0::FLOAT)) AS a"));
        assertEquals("[2.500000000000000e+00]", cellOf("SELECT TO_JSON(ARRAY_CONSTRUCT(2.5::FLOAT)) AS a"));
        assertEquals("[-2.500000000000000e+00]", cellOf("SELECT TO_JSON(ARRAY_CONSTRUCT((-2.5)::FLOAT)) AS a"));
        assertEquals("[0.000000000000000e+00]", cellOf("SELECT TO_JSON(PARSE_JSON('[0e0]')) AS a"));
    }

    @Test
    public void theOtherNumericFamiliesConvertAsTheyDisplay() {
        assertEquals("[1.5]", cellOf("SELECT TO_JSON(PARSE_JSON('[1.5]')) AS a"));
        assertEquals("[1]", cellOf("SELECT TO_JSON(PARSE_JSON('[1]')) AS a"));
        // Written without an exponent, so DECIMAL — every digit survives.
        assertEquals("[3.141592653589793]", cellOf("SELECT TO_JSON(PARSE_JSON('[3.141592653589793]')) AS a"));
    }

    @Test
    public void theExponentFieldWidensRatherThanTruncating() {
        assertEquals("[1.000000000000000e+05]", cellOf("SELECT TO_JSON(PARSE_JSON('[1e5]')) AS a"));
        assertEquals("[1.000000000000000e-05]", cellOf("SELECT TO_JSON(PARSE_JSON('[1e-5]')) AS a"));
        assertEquals("[1.000000000000000e+100]", cellOf("SELECT TO_JSON(PARSE_JSON('[1e100]')) AS a"));
        assertEquals("[1.000000000000000e-100]", cellOf("SELECT TO_JSON(PARSE_JSON('[1e-100]')) AS a"));
        assertEquals("[1.000000000000000e+308]", cellOf("SELECT TO_JSON(PARSE_JSON('[1e308]')) AS a"));
    }

    @Test
    public void theFifteenDecimalsAreTheDoublesOwnDigitsRoundedToFit() {
        assertEquals("[3.333330000000000e-01]", cellOf("SELECT TO_JSON(ARRAY_CONSTRUCT((1.0/3.0)::FLOAT)) AS a"));
        assertEquals("[1.234567890123457e+19]",
            cellOf("SELECT TO_JSON(PARSE_JSON('[12345678901234567890e0]')) AS a"));
    }

    @Test
    public void itAppliesWhereverTheDoubleSitsInTheDocument() {
        assertEquals("1.000000000000000e+00", cellOf("SELECT TO_JSON(PARSE_JSON('1.0e0')) AS a"));
        assertEquals("{\"a\":{\"b\":1.000000000000000e+05}}",
            cellOf("SELECT TO_JSON(PARSE_JSON('{\"a\": {\"b\": 1e5}}')) AS a"));
        assertEquals("[1,1.5,1.000000000000000e+05,\"x\",null,true]",
            cellOf("SELECT TO_JSON(PARSE_JSON('[1, 1.5, 1e5, \"x\", null, true]')) AS a"));
    }

    @Test
    public void aStoredValueConvertsTheSameWayALiteralDoes() {
        engine.execute("CREATE TABLE jd (v VARIANT, f FLOAT)");
        engine.execute("INSERT INTO jd SELECT PARSE_JSON('[1e5]'), 2.5");
        assertEquals("[1.000000000000000e+05]", cellOf("SELECT TO_JSON(v) AS a FROM jd"));
        assertEquals("[2.500000000000000e+00]", cellOf("SELECT TO_JSON(ARRAY_CONSTRUCT(f)) AS a FROM jd"));
        // A bare DOUBLE is the whole document, and takes the same form.
        assertEquals("2.500000000000000e+00", cellOf("SELECT TO_JSON(f) AS a FROM jd"));
    }

    @Test
    public void theDisplayAndTheConversionsAgree() {
        // One value, one text: the display is the text every conversion produces.
        assertEquals("[1.000000000000000e+00]", cellOf("SELECT ARRAY_CONSTRUCT(1.0::FLOAT) AS a"));
        assertEquals("{\"k\":1.000000000000000e+00}", cellOf("SELECT OBJECT_CONSTRUCT('k', 1.0::FLOAT) AS a"));
        assertEquals("[1.000000000000000e+00]", cellOf("SELECT ARRAY_CONSTRUCT(1.0::FLOAT)::VARCHAR AS a"));
        assertEquals("[1.000000000000000e+00]", cellOf("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(1.0::FLOAT)) AS a"));
        assertEquals("{\"k\":1.000000000000000e+00}",
            cellOf("SELECT TO_JSON(OBJECT_CONSTRUCT('k', 1.0::FLOAT)) AS a"));
        // Reading an element back out is a display again, not a conversion.
        assertEquals("100000.0", cellOf("SELECT PARSE_JSON('[1e5]')[0] AS a"));
        assertEquals("100000.0", cellOf("SELECT PARSE_JSON('[1e5]')[0]::FLOAT AS a"));
    }
    @Test
    public void negativeZeroKeepsItsSignThroughTheDoubleFamily() {
        // The reader takes the double off the TOKEN TEXT — a BigDecimal hop has no negative zero —
        // so -0e0 stays signed through conversion and display, while the PLAIN -0.0 is the DECIMAL
        // family and normalises to 0 (live-verified, cell by cell).
        assertEquals("[-0.000000000000000e+00]", cellOf("SELECT TO_JSON(PARSE_JSON('[-0e0]')) AS v"));
        assertEquals("[0.000000000000000e+00]", cellOf("SELECT TO_JSON(PARSE_JSON('[0e0]')) AS v"));
        assertEquals("{\"k\":-0.000000000000000e+00}",
            cellOf("SELECT TO_JSON(PARSE_JSON('{\"k\":-0e0}')) AS v"));
        assertEquals("[-0.000000000000000e+00]", cellOf("SELECT PARSE_JSON('[-0e0]') AS v"));
        assertEquals("[0]", cellOf("SELECT TO_JSON(PARSE_JSON('[-0.0]')) AS v"));
    }

    @Test
    public void anExtractedNegativeZeroLeafKeepsItsSignToo() {
        // The LEAF carries the sign exactly as the array does — through GET, the index and the colon
        // path, a standalone -0e0 document, FLATTEN's value and a stored column alike — and so does
        // every value read off it: the conversion text, the FLOAT cast, arithmetic, the VARCHAR text.
        // Only the DECIMAL and INTEGER spellings (-0.0, -0, -0.00) have no sign to keep.
        engine.execute("CREATE OR REPLACE TABLE nz (i INT, v VARIANT, f FLOAT)");
        engine.execute("INSERT INTO nz SELECT 1, PARSE_JSON('[-0e0]'), -0.0::FLOAT");
        engine.execute("INSERT INTO nz SELECT 2, PARSE_JSON('[-1.5]'), -1.5");
        assertEquals("-0.0", cellOf("SELECT GET(PARSE_JSON('[-0e0]'), 0) AS v"));
        assertEquals("-0.0", cellOf("SELECT PARSE_JSON('[-0e0]')[0] AS v"));
        assertEquals("-0.0", cellOf("SELECT PARSE_JSON('{\"k\":-0e0}'):k AS v"));
        assertEquals("-0.0", cellOf("SELECT PARSE_JSON('-0e0') AS v"));
        assertEquals("-0.0", cellOf("SELECT GET(PARSE_JSON('[-0E+2]'), 0) AS v"));
        assertEquals("-0.0", cellOf("SELECT value AS v FROM TABLE(FLATTEN(PARSE_JSON('[-0e0]')))"));
        assertEquals("-0.0", cellOf("SELECT GET(v, 0) AS v FROM nz WHERE i = 1"));
        assertEquals("-0.0", cellOf("SELECT v[0] AS v FROM nz WHERE i = 1"));
        assertEquals("[-0.000000000000000e+00]", cellOf("SELECT v FROM nz WHERE i = 1"));
        assertEquals("-0.000000000000000e+00", cellOf("SELECT TO_JSON(GET(PARSE_JSON('[-0e0]'), 0)) AS v"));
        assertEquals("-0.000000000000000e+00", cellOf("SELECT TO_JSON(PARSE_JSON('-0e0')) AS v"));
        assertEquals("-0.000000000000000e+00", cellOf("SELECT TO_JSON(GET(v, 0)) AS v FROM nz WHERE i = 1"));
        assertEquals("{\"k\":-0.000000000000000e+00}",
            cellOf("SELECT TO_JSON(OBJECT_CONSTRUCT('k', GET(PARSE_JSON('[-0e0]'), 0))) AS v"));
        assertEquals("-0.0", cellOf("SELECT GET(PARSE_JSON('[-0e0]'), 0)::FLOAT AS v"));
        assertEquals("-0.0", cellOf("SELECT GET(PARSE_JSON('[-0e0]'), 0) * 1 AS v"));
        assertEquals("-0.0", cellOf("SELECT AS_DOUBLE(GET(PARSE_JSON('[-0e0]'), 0)) AS v"));
        assertEquals("-0", cellOf("SELECT GET(PARSE_JSON('[-0e0]'), 0)::VARCHAR AS v"));
        assertEquals("-0", cellOf("SELECT TO_VARCHAR(GET(PARSE_JSON('[-0e0]'), 0)) AS v"));
        assertEquals("x-0", cellOf("SELECT 'x' || GET(PARSE_JSON('[-0e0]'), 0) AS v"));
        assertEquals("0.0", cellOf("SELECT SIGN(GET(PARSE_JSON('[-0e0]'), 0)) AS v"));
        assertEquals("true", cellOf("SELECT GET(PARSE_JSON('[-0e0]'), 0) = 0 AS v"));
        assertEquals("false", cellOf("SELECT GET(PARSE_JSON('[-0e0]'), 0) < 0 AS v"));
        assertEquals("DOUBLE", cellOf("SELECT TYPEOF(GET(PARSE_JSON('[-0e0]'), 0)) AS v"));
        // The stored FLOAT beside a second, distinct value — a lone zero column is served from
        // statistics on the account and prints positive; two values keep the sign.
        assertEquals("-0.0", cellOf("SELECT f FROM nz WHERE i = 1"));
        assertEquals("-0.0", cellOf("SELECT f::VARIANT AS v FROM nz WHERE i = 1"));
        assertEquals("{\"k\":-0.000000000000000e+00}",
            cellOf("SELECT TO_JSON(OBJECT_CONSTRUCT('k', f)) AS v FROM nz WHERE i = 1"));
        assertEquals("-0.000000000000000e+00", cellOf("SELECT TO_JSON(TO_VARIANT(f)) AS v FROM nz WHERE i = 1"));
        // The families without a sign of zero.
        assertEquals("0", cellOf("SELECT GET(PARSE_JSON('[-0]'), 0) AS v"));
        assertEquals("0", cellOf("SELECT PARSE_JSON('-0') AS v"));
        assertEquals("0", cellOf("SELECT GET(PARSE_JSON('[-0.00]'), 0) AS v"));
        assertEquals("[0]", cellOf("SELECT PARSE_JSON('[-0.00]') AS v"));
        // A STRING leaf is its text, unquoted — the sign here is two characters, not a value.
        assertEquals("-0e0", cellOf("SELECT GET(PARSE_JSON('[\"-0e0\"]'), 0) AS v"));
    }

    @Test
    public void jsonExtractPathTextRendersTheDoubleInFloatDisplayText() {
        // Ten significant digits growing one per decade — the FLOAT-to-VARCHAR rule, not Java's
        // shortest round-trip (live-verified across the family).
        assertEquals("1", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.000000000000000e+00}', 'k') AS v"));
        assertEquals("1.5", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.500000000000000e+00}', 'k') AS v"));
        assertEquals("100000", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.000000000000000e+05}', 'k') AS v"));
        assertEquals("1e-05", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.000000000000000e-05}', 'k') AS v"));
        assertEquals("1.23456789", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.234567890123456e+00}', 'k') AS v"));
        assertEquals("1e+300", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.000000000000000e+300}', 'k') AS v"));
        assertEquals("1", cellOf("SELECT JSON_EXTRACT_PATH_TEXT(TO_JSON(OBJECT_CONSTRUCT('k', 1.0::FLOAT)), 'k') AS v"));
    }

    @Test
    public void theIntegerAndDecimalFamiliesPassThroughVerbatim() {
        assertEquals("42", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":42}', 'k') AS v"));
        assertEquals("1.25", cellOf("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":1.25}', 'k') AS v"));
        assertEquals("1", cellOf("SELECT GET(PARSE_JSON('{\"k\":1.000000000000000e+00}'), 'k')::VARCHAR AS v"));
        assertEquals("1", cellOf("SELECT TO_VARCHAR(GET(PARSE_JSON('{\"k\":1.000000000000000e+00}'), 'k')) AS v"));
    }

}
