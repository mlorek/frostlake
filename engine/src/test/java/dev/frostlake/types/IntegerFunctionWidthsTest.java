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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An integer-returning function declares only as many digits as its result can occupy, and the widths
 * are not one number — the account distinguishes several, and they do not follow from the story the
 * function tells. Frostlake declared a bare {@code INTEGER} for these, which is wrong twice over: the
 * NAME (every integer alias normalises to NUMBER) and the WIDTH.
 *
 * <pre>
 *   ASCII      NUMBER(4,0)    but UNICODE   NUMBER(18,0)   — both name a code point
 *   WEEKOFYEAR NUMBER(2,0)    but YEAROFWEEKISO NUMBER(4,0)
 *   EDITDISTANCE NUMBER(9,0)  but RTRIMMED_LENGTH NUMBER(18,0)
 *   FACTORIAL  NUMBER(37,0)   — one digit short of the widest NUMBER
 *   the BIT family NUMBER(38,0)
 * </pre>
 *
 * <p>Only functions whose width is a CONSTANT are pinned here. The ones that derive it from their
 * argument — the rounding family, SIGN, WIDTH_BUCKET — are tracked separately, because a table of
 * constants is the wrong shape for them.
 */
public class IntegerFunctionWidthsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE iw (v VARCHAR(20), i INT, d DATE, vt VARIANT)");
        engine.execute("INSERT INTO iw SELECT 'hello', 12, '2020-03-04', PARSE_JSON('{\"a\": 7}')");
    }

    /** The declared type of an expression, with the parameters a NUMBER carries. */
    private String typeOf(final String expr) {
        final DataType type = engine.executeQuery("SELECT " + expr + " AS x FROM iw")
            .getColumns().get(0).getDataType();
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + ","
                + ((NumericType) type).getScale() + ")";
        }
        return type == null ? "null" : type.getName();
    }

    /** Two code-point functions, two different widths — the pair that makes the point. */
    @Test
    public void theCodePointFunctionsDisagreeWithEachOther() {
        assertEquals("NUMBER(4,0)", typeOf("ASCII(v)"));
        assertEquals("NUMBER(18,0)", typeOf("UNICODE(v)"), "not four, though it names the same thing");
    }

    /** The string counters and comparators. */
    @Test
    public void theStringCountersTakeTheirOwnWidths() {
        assertEquals("NUMBER(18,0)", typeOf("RTRIMMED_LENGTH(v)"));
        assertEquals("NUMBER(9,0)", typeOf("EDITDISTANCE(v, 'x')"));
        assertEquals("NUMBER(4,0)", typeOf("JAROWINKLER_SIMILARITY(v, 'x')"));
    }

    /** The two date parts that were still declaring a bare integer. */
    @Test
    public void theRemainingDatePartsAreSized() {
        assertEquals("NUMBER(2,0)", typeOf("WEEKOFYEAR(d)"));
        assertEquals("NUMBER(4,0)", typeOf("YEAROFWEEKISO(d)"));
    }

    /** The whole bit family is the widest NUMBER, whatever it is given. */
    @Test
    public void theBitFamilyIsTheWidestNumber() {
        assertEquals("NUMBER(38,0)", typeOf("BITAND(i, 3)"));
        assertEquals("NUMBER(38,0)", typeOf("BITOR(i, 3)"));
        assertEquals("NUMBER(38,0)", typeOf("BITXOR(i, 3)"));
        assertEquals("NUMBER(38,0)", typeOf("BITNOT(i)"));
        assertEquals("NUMBER(38,0)", typeOf("BITSHIFTLEFT(i, 1)"));
        assertEquals("NUMBER(38,0)", typeOf("BITSHIFTRIGHT(i, 1)"));
        assertEquals("NUMBER(38,0)", typeOf("BITCOUNT(i)"));
        assertEquals("NUMBER(38,0)", typeOf("GETBIT(i, 0)"));
    }

    /** A factorial stops one digit short of the widest NUMBER, and a variant read stops at it. */
    @Test
    public void theTwoOutliers() {
        assertEquals("NUMBER(37,0)", typeOf("FACTORIAL(5)"), "37, not 38");
        assertEquals("NUMBER(38,0)", typeOf("AS_INTEGER(vt:a)"));
    }

    /** The widths measured earlier, pinned again so this change cannot disturb them. */
    @Test
    public void theAlreadyMeasuredWidthsAreUnchanged() {
        assertEquals("NUMBER(18,0)", typeOf("LENGTH(v)"));
        assertEquals("NUMBER(9,0)", typeOf("CHARINDEX('l', v)"));
        assertEquals("NUMBER(2,0)", typeOf("MONTH(d)"));
        assertEquals("NUMBER(4,0)", typeOf("YEAR(d)"));
        assertEquals("NUMBER(19,0)", typeOf("HASH(v)"));
        assertEquals("NUMBER(19,0)", typeOf("BIT_LENGTH(v)"));
        assertEquals("NUMBER(9,0)", typeOf("DATEDIFF('day', d, d)"));
        assertEquals("NUMBER(9,0)", typeOf("ARRAY_SIZE(ARRAY_CONSTRUCT(1,2))"));
        assertEquals("NUMBER(2,0)", typeOf("EXTRACT(month FROM d)"));
        assertEquals("NUMBER(4,0)", typeOf("DATE_PART('year', d)"));
    }
}
