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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Beside a NUMERIC source, TO_TIMESTAMP's second argument is a SCALE: a constant integer from 0 to 9. Each
 * other shape is refused while the statement compiles, unpositioned, naming the flavour the call resolves
 * to - TO_TIMESTAMP and TRY_TO_TIMESTAMP say TO_TIMESTAMP_NTZ:
 *
 * <pre>
 *   a literal that is no integer    argument 2 to function TO_TIMESTAMP_NTZ needs to be an integer,
 *                                   found: 'null' / ''3'' / '1.5' / 'TRUE'
 *   a column or an expression       argument 2 to function TO_TIMESTAMP_NTZ needs to be constant, found 'ST.K'
 *   an integer outside 0..9         Invalid value [12] for function 'TO_TIMESTAMP_NTZ' at position 2
 * </pre>
 *
 * Every cell was measured on a real account.
 */
public class TimestampScaleArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE st (n NUMBER(10,0), k NUMBER(1,0), f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO st VALUES (1631711999, 3, 1631711999.5, '3')");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage()).replace('\n', '|');
    }

    private static String notAnInteger(final String flavour, final String found) {
        return "SQL compilation error:|argument 2 to function " + flavour + " needs to be an integer, found: '"
            + found + "'";
    }

    private static String notConstant(final String found) {
        return "SQL compilation error:|argument 2 to function TO_TIMESTAMP_NTZ needs to be constant, found '"
            + found + "'";
    }

    private static String outOfRange(final String value) {
        return "SQL compilation error:|Invalid value [" + value + "] for function 'TO_TIMESTAMP_NTZ' at position 2";
    }

    @Test
    public void aLiteralThatIsNoIntegerIsRefused() {
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "null"), refusal("SELECT TO_TIMESTAMP(1631711999, NULL)"));
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "'3'"), refusal("SELECT TO_TIMESTAMP(1631711999, '3')"));
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "'YYYY'"), refusal("SELECT TO_TIMESTAMP(n, 'YYYY') FROM st"));
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "1.5"), refusal("SELECT TO_TIMESTAMP(1631711999, 1.5)"));
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "TRUE"), refusal("SELECT TO_TIMESTAMP(1631711999, TRUE)"));
    }

    /** Each flavour names itself; a TRY_ twin names the conversion it tries. */
    @Test
    public void theSentenceNamesTheFlavour() {
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "'3'"), refusal("SELECT TO_TIMESTAMP_NTZ(1631711999, '3')"));
        assertEquals(notAnInteger("TO_TIMESTAMP_LTZ", "'3'"), refusal("SELECT TO_TIMESTAMP_LTZ(1631711999, '3')"));
        assertEquals(notAnInteger("TO_TIMESTAMP_TZ", "'3'"), refusal("SELECT TO_TIMESTAMP_TZ(1631711999, '3')"));
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "'3'"), refusal("SELECT TRY_TO_TIMESTAMP(1631711999, '3')"));
        assertEquals(notAnInteger("TO_TIMESTAMP_LTZ", "null"), refusal("SELECT TRY_TO_TIMESTAMP_LTZ(1631711999, NULL)"));
    }

    @Test
    public void aColumnOrAnExpressionIsNotConstant() {
        assertEquals(notConstant("ST.K"), refusal("SELECT TO_TIMESTAMP(n, k) FROM st"));
        assertEquals(notConstant("ST.S"), refusal("SELECT TO_TIMESTAMP(n, s) FROM st"));
        assertEquals(notConstant("1 + 2"), refusal("SELECT TO_TIMESTAMP(1631711999, 1+2)"));
    }

    @Test
    public void anIntegerOutsideZeroToNineIsAnInvalidValue() {
        assertEquals(outOfRange("12"), refusal("SELECT TO_TIMESTAMP(1631711999, 12)"));
        assertEquals(outOfRange("10"), refusal("SELECT TO_TIMESTAMP(1631711999, 10)"));
        assertEquals(outOfRange("-3"), refusal("SELECT TO_TIMESTAMP(1631711999, -3)"));
        assertEquals("1970-01-01 00:00:01.631", String.valueOf(
            engine.executeQuery("SELECT TO_TIMESTAMP(1631711999, 9)::VARCHAR").getRows().get(0).getValue(0)),
            "nine is the widest scale there is");
    }

    /** Compile-time and unpositioned: an empty table refuses too, and a leading item moves nothing. */
    @Test
    public void theRefusalIsCompileTimeAndUnpositioned() {
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "'3'"), refusal("SELECT 1, TO_TIMESTAMP(1631711999, '3')"));
        assertEquals(notAnInteger("TO_TIMESTAMP_NTZ", "'3'"), refusal("SELECT TO_TIMESTAMP(n, '3') FROM st WHERE 1 = 0"));
        assertEquals("SQL compilation error:|invalid type [TO_TIMESTAMP(ST.F, '3')] for parameter 'TO_TIMESTAMP_NTZ'",
            refusal("SELECT TO_TIMESTAMP(f, '3') FROM st"), "a FLOAT source keeps the conversion sentence");
    }
}
