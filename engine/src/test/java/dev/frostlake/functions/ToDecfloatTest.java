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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TO_DECFLOAT is its own conversion, not TO_DOUBLE: it takes a BOOLEAN, reads a VARIANT's number,
 * boolean or numeric text, and words its failures its own way; a temporal, BINARY or structured
 * source is refused while the statement compiles. Every cell is live-verified; the values are compared
 * as numbers, the engine carrying a DECFLOAT as a DOUBLE.
 */
public class ToDecfloatTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ct (d DATE, bn BINARY, o OBJECT, b BOOLEAN, f FLOAT, n NUMBER(10,2), "
            + "v VARCHAR, ts TIMESTAMP_NTZ, tm TIME, va VARIANT, a ARRAY)");
        engine.execute("INSERT INTO ct SELECT '2020-01-02', TO_BINARY('61'), OBJECT_CONSTRUCT('a',1), TRUE, 1.5, 2.25, "
            + "'10:11:12', '2020-01-02 10:11:12', '10:11:12', PARSE_JSON('1.5'), ARRAY_CONSTRUCT(1)");
    }

    private double number(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aBooleanConverts() {
        assertEquals(1.0, number("SELECT TO_DECFLOAT(TRUE)"));
        assertEquals(0.0, number("SELECT TO_DECFLOAT(FALSE)"));
        assertEquals(1.0, number("SELECT TO_DECFLOAT(b) FROM ct"));
        assertEquals(1.0, number("SELECT TO_DECFLOAT(TO_VARIANT(TRUE))"));
        assertEquals(1.0, number("SELECT TO_DECFLOAT(PARSE_JSON('true'))"));
        assertNull(engine.executeQuery("SELECT TO_DECFLOAT(NULL::BOOLEAN)").getRows().get(0).getValue(0));
    }

    @Test
    public void numbersAndTextsConvert() {
        assertEquals(1.5, number("SELECT TO_DECFLOAT(1.5)"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT('1.5')"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT(' 1.5 ')"));
        assertEquals(1.0E10, number("SELECT TO_DECFLOAT('1e10')"));
        assertEquals(1234.5, number("SELECT TO_DECFLOAT('1,234.5', '9,999.9')"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT(f) FROM ct"));
        assertEquals(2.25, number("SELECT TO_DECFLOAT(n) FROM ct"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT(va) FROM ct"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT(PARSE_JSON('1.5'))"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT(PARSE_JSON('\"1.5\"'))"));
        assertNull(engine.executeQuery("SELECT TO_DECFLOAT(NULL)").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TO_DECFLOAT(PARSE_JSON('null'))").getRows().get(0).getValue(0));
    }

    @Test
    public void itsFailuresAreItsOwn() {
        assertRefused("SELECT TO_DECFLOAT('abc')", "Numeric value 'abc' is not recognized");
        assertRefused("SELECT TO_DECFLOAT('')", "Numeric value '' is not recognized");
        assertRefused("SELECT TO_DECFLOAT('NaN')", "Numeric value 'NaN' is not recognized");
        assertRefused("SELECT TO_DECFLOAT('inf')", "Numeric value 'inf' is not recognized");
        assertRefused("SELECT TO_DECFLOAT(v) FROM ct", "Numeric value '10:11:12' is not recognized");
        assertRefused("SELECT TO_DECFLOAT('abc', '999')", "Can't parse 'abc' as number with format '999'");
        assertRefused("SELECT TO_DECFLOAT(PARSE_JSON('\"abc\"'))", "Failed to cast variant value \"abc\" to DECFLOAT");
        assertRefused("SELECT TO_DECFLOAT(PARSE_JSON('\"NaN\"'))", "Failed to cast variant value \"NaN\" to DECFLOAT");
        assertRefused("SELECT TO_DECFLOAT(PARSE_JSON('{\"a\":1}'))",
            "Failed to cast variant value {\"a\":1} to DECFLOAT");
        assertRefused("SELECT TO_DECFLOAT(PARSE_JSON('[1]'))", "Failed to cast variant value [1] to DECFLOAT");
        assertRefused("SELECT TO_DECFLOAT(TO_VARIANT(1.5::FLOAT))", "DecFloat not supported");
        assertRefused("SELECT TO_DECFLOAT(1e308::FLOAT * 10)",
            "Decfloat out of representable range, operation: TO_DECFLOAT(inf)");
        assertRefused("SELECT TO_DECFLOAT(-1e308::FLOAT * 10)",
            "Decfloat out of representable range, operation: TO_DECFLOAT(-inf)");
    }

    @Test
    public void aSourceWithNoDecfloatIsRefusedWhileTheStatementCompiles() {
        final String refusal = "SQL compilation error:\ninvalid type [";
        assertRefused("SELECT TO_DECFLOAT(d) FROM ct", refusal + "TO_DECFLOAT(CT.D)] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(ts) FROM ct", refusal + "TO_DECFLOAT(CT.TS)] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(tm) FROM ct", refusal + "TO_DECFLOAT(CT.TM)] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(bn) FROM ct", refusal + "TO_DECFLOAT(CT.BN)] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(o) FROM ct", refusal + "TO_DECFLOAT(CT.O)] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(a) FROM ct", refusal + "TO_DECFLOAT(CT.A)] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(X'41')", refusal + "TO_DECFLOAT(X'41')] for parameter 'TO_DECFLOAT'");
        assertRefused("SELECT TO_DECFLOAT(TO_DATE('2020-01-01'))",
            refusal + "TO_DECFLOAT(TO_DATE('2020-01-01'))] for parameter 'TO_DECFLOAT'");
    }

    @Test
    public void aPaddedTextIsEchoedTrimmed() {
        assertRefused("SELECT TO_DECFLOAT(' ')", "Numeric value '' is not recognized");
        assertRefused("SELECT TO_DECFLOAT('  abc  ')", "Numeric value 'abc' is not recognized");
    }

    @Test
    public void aFormatModelIsCheckedAndItsWidthApplies() {
        assertRefused("SELECT TO_DECFLOAT('1e5', '9e9')",
            "Bad input format model '9e9' for DECFLOAT: invalid numeric format keyword: 'e9'");
        assertRefused("SELECT TO_DECFLOAT('12', 'abc')",
            "Bad input format model 'abc' for DECFLOAT: invalid numeric format keyword: 'abc'");
        assertRefused("SELECT TO_DECFLOAT('123', '99')", "Can't parse '123' as number with format '99'");
        assertRefused("SELECT TO_DECFLOAT('12.34', '99.9')", "Can't parse '12.34' as number with format '99.9'");
        assertRefused("SELECT TO_DECFLOAT(' 123 ', '99')", "Can't parse ' 123 ' as number with format '99'");
        assertEquals(100000.0, number("SELECT TO_DECFLOAT('1e5', '9EEEE')"));
        assertEquals(123.0, number("SELECT TO_DECFLOAT('123', '999')"));
        assertEquals(1.5, number("SELECT TO_DECFLOAT('1.5', '9.9')"));
        assertEquals(100000.0, number("SELECT TO_DECFLOAT('1e5', 'AUTO')"));
    }

    @Test
    public void aFormatBesideASourceThatIsNoTextIsTooManyArguments() {
        assertRefused("SELECT TO_DECFLOAT(TRUE, 'x')",
            "error line 1 at position 7\ntoo many arguments for function [TO_DECFLOAT(TRUE, 'x')] expected 1, got 2");
        assertRefused("SELECT TO_DECFLOAT(1.5, '9.9')",
            "error line 1 at position 7\ntoo many arguments for function [TO_DECFLOAT(1.5, '9.9')] expected 1, got 2");
    }
}
