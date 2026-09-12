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
 * TO_DOUBLE and TRY_TO_DOUBLE: a text source may carry a numeric FORMAT model, whose group separators
 * and currency are accepted only when the model declares them and whose TM form reads any number; a
 * number beside a format is too many arguments, and a format that is no string is refused. TO_NUMBER
 * reads the same model, at its own scale. Every cell is live-verified.
 */
public class ToDoubleTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** The single cell as a double. */
    private double number(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    /** The format model over a text source, the refusals beside it, and the TRY_ twin's NULL. */
    @Test
    public void aTextSourceTakesANumericFormat() {
        assertEquals(1234.5, number("SELECT TO_DOUBLE('1,234.5', '9,999.9')"), 1e-9);
        assertEquals(12, number("SELECT TO_DOUBLE('12', '99')"), 1e-9);
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE('abc', '999')").getRows().get(0).getValue(0));
        assertEquals(1.5, number("SELECT TO_DOUBLE('1.5', 'TM9')"), 1e-9);
        assertRefused("SELECT TO_DOUBLE('1', '9', 'x')",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_DOUBLE('1', '9', 'x')] expected 2, got 3");
        assertRefused("SELECT TO_DOUBLE(1.5, '99')",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_DOUBLE(1.5, '99')] expected 1, got 2");
        assertEquals(1234.5, number("SELECT TRY_TO_DOUBLE('1,234.5', '9,999.9')"), 1e-9);
        assertEquals(12.5, number("SELECT TO_DOUBLE('$12.50', '$99.99')"), 1e-9);
        assertEquals("1235",
            String.valueOf(engine.executeQuery("SELECT TO_NUMBER('1,234.5', '9,999.9')").getRows().get(0).getValue(0)));
        assertRefused("SELECT TO_DOUBLE('1', NULL)",
            "SQL compilation error:\nFormat argument for function 'TO_DOUBLE' needs to be a string");
    }

    /** A text that spells no number is refused with the FLOAT cast's sentence, the text echoed trimmed. */
    @Test
    public void aTextThatIsNoNumberIsRefusedAsTheCastRefusesIt() {
        engine.execute("CREATE OR REPLACE TABLE tx (s VARCHAR)");
        engine.execute("INSERT INTO tx VALUES ('abc')");
        assertRefused("SELECT TO_DOUBLE('abc')", "Numeric value 'abc' is not recognized");
        assertRefused("SELECT TO_DOUBLE('1.5x')", "Numeric value '1.5x' is not recognized");
        assertRefused("SELECT TO_DOUBLE(' ')", "Numeric value '' is not recognized");
        assertRefused("SELECT TO_DOUBLE('')", "Numeric value '' is not recognized");
        assertRefused("SELECT TO_DOUBLE('  abc  ')", "Numeric value 'abc' is not recognized");
        assertRefused("SELECT TO_DOUBLE(' 1.5x ')", "Numeric value '1.5x' is not recognized");
        assertRefused("SELECT TO_DOUBLE(s) FROM tx", "Numeric value 'abc' is not recognized");
        assertRefused("SELECT TO_DOUBLE('1_000')", "Numeric value '1_000' is not recognized");
        assertRefused("SELECT TO_DOUBLE('1,5')", "Numeric value '1,5' is not recognized");
        assertRefused("SELECT TO_DOUBLE('e5')", "Numeric value 'e5' is not recognized");
        assertRefused("SELECT TO_DOUBLE('1e')", "Numeric value '1e' is not recognized");
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE('abc')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE(' ')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE('1.5x')").getRows().get(0).getValue(0));
    }

    /** What the FLOAT cast reads, TO_DOUBLE reads: the non-finite words among it. */
    @Test
    public void aTextIsReadAsTheCastReadsIt() {
        assertEquals(1.5, number("SELECT TO_DOUBLE(' 1.5 ')"), 0.0);
        assertTrue(Double.isNaN(number("SELECT TO_DOUBLE('NaN')")));
        assertTrue(Double.isNaN(number("SELECT TO_DOUBLE('nan')")));
        assertEquals(Double.POSITIVE_INFINITY, number("SELECT TO_DOUBLE('inf')"), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, number("SELECT TO_DOUBLE('-inf')"), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, number("SELECT TO_DOUBLE('Infinity')"), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, number("SELECT TO_DOUBLE('1e400')"), 0.0);
        assertEquals(0.0, number("SELECT TO_DOUBLE('1e-400')"), 0.0);
        assertEquals(1.5, number("SELECT TO_DOUBLE('+1.5')"), 0.0);
        assertEquals(0.5, number("SELECT TO_DOUBLE('.5')"), 0.0);
        assertEquals(5.0, number("SELECT TO_DOUBLE('5.')"), 0.0);
        assertEquals(1.0, number("SELECT TO_DOUBLE('1d')"), 0.0);
        assertEquals(1.0, number("SELECT TO_DOUBLE('1f')"), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, number("SELECT TRY_TO_DOUBLE('inf')"), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, number("SELECT TRY_TO_DOUBLE('-inf')"), 0.0);
        assertTrue(Double.isNaN(number("SELECT TRY_TO_DOUBLE('NaN')")));
    }

    /** A format model is checked before the text, naming REAL, and its width applies. */
    @Test
    public void aFormatModelIsCheckedAndItsWidthApplies() {
        assertRefused("SELECT TO_DOUBLE('1e5', '9e9')",
            "Bad input format model '9e9' for REAL: invalid numeric format keyword: 'e9'");
        assertRefused("SELECT TO_DOUBLE('1', '')",
            "Bad input format model '' for REAL: missing required input format element(s)");
        assertRefused("SELECT TO_DOUBLE('123', '99')", "Can't parse '123' as number with format '99'");
        assertRefused("SELECT TO_DOUBLE('123.4', '99.9')", "Can't parse '123.4' as number with format '99.9'");
        assertRefused("SELECT TO_DOUBLE('  abc  ', '999')", "Can't parse '  abc  ' as number with format '999'");
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE('1e5', '9e9')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE('123', '99')").getRows().get(0).getValue(0));
        assertEquals(100000.0, number("SELECT TO_DOUBLE('1e5', '9EEEE')"), 0.0);
        assertEquals(100000.0, number("SELECT TO_DOUBLE('1e5', 'AUTO')"), 0.0);
        assertEquals(1.5, number("SELECT TO_DOUBLE('1.5', '9.9|99')"), 0.0);
    }
}
