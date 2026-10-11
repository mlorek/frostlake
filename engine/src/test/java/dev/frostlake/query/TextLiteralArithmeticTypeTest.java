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

import org.junit.jupiter.api.Test;

/**
 * A text literal beside an exact number reads as the plan reads it: one that spells a number with nothing
 * about it at eighteen digits and the scale of its value - '1.50' and '1.5' alike NUMBER(18,1), '1.0' and
 * '1e2' NUMBER(18,0) - and any other text, a blank about it included, as NUMBER(18,5) with its value rounded
 * to five decimals. The remainder reads the value's own digits, the rounding family the same scale, and an
 * IFF or a CASE over text literals the widest scale its results spell. Every answer is live-verified.
 */
public class TextLiteralArithmeticTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theNumberATextLiteralReadsAs() {
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.50' * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.5' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.0' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('1' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('1e2' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.50e1' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB4]",
            "SELECT SYSTEM$TYPEOF(' 1 ' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB4]",
            "SELECT SYSTEM$TYPEOF('1 ' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB4]",
            "SELECT SYSTEM$TYPEOF(' 1' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF('' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF('abc' * 1)");
        assertAnswer(
            "NUMBER(19,6)[SB4]",
            "SELECT SYSTEM$TYPEOF('1.123456' * 1)");
        assertAnswer(
            "NUMBER(19,7)[SB4]",
            "SELECT SYSTEM$TYPEOF('1.1234567' * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('-1.50' * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('+1.5' * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('.5' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('5.' * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('0.10' + 1)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.50' - 1)");
        assertAnswer(
            "NUMBER(24,7)[SB4]",
            "SELECT SYSTEM$TYPEOF('1.50' / 2)");
        assertAnswer(
            "NUMBER(3,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.50' % 2)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(1 * '1.50')");
        assertAnswer(
            "NUMBER(20,3)[SB2]",
            "SELECT SYSTEM$TYPEOF(1.5 * '2.25')");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF('1.50' * '2.5')");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.50' * 1.0)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('100' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.00' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('0' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('0.0' * 1)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF('1.50' + '1')");
        assertAnswer(
            "NUMBER(21,0)[SB16]",
            "SELECT SYSTEM$TYPEOF('12345678901234567890' * 1)");
        assertAnswer(
            "NUMBER(19,3)[SB1]",
            "SELECT SYSTEM$TYPEOF('1e-3' * 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF('1E2' * 1)");
        assertAnswer(
            "NUMBER(19,2)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.5e-1' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF('0x1F' * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF('1_000' * 1)");
        assertAnswer(
            "NUMBER(20,1)[SB1]",
            "SELECT SYSTEM$TYPEOF('1.50' * 1 + 1)");
        assertAnswer(
            "NUMBER(20,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(('1.50' * 1) * 2)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF('1.50'::VARCHAR * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(TO_VARCHAR(1.50) * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB4]",
            "SELECT SYSTEM$TYPEOF(('1.' || '50') * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB4]",
            "SELECT SYSTEM$TYPEOF(UPPER('1.50') * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(IFF(TRUE, '1.50', '2') * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(NVL('1.50', '2') * 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(COALESCE(NULL, '1.50') * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN '1.50' ELSE '2' END * 1)");
        assertAnswer(
            "NUMBER(19,1)[SB16]",
            "SELECT SYSTEM$TYPEOF(IFF(FALSE, '1.50', '2') * 1)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(ABS('1.50'))");
        assertAnswer(
            "NUMBER(18,1)[SB8]",
            "SELECT SYSTEM$TYPEOF(ROUND('1.50', 1))");
        assertAnswer(
            "NUMBER(24,7)[SB4]",
            "SELECT SYSTEM$TYPEOF(DIV0('1.50', 2))");
        assertAnswer(
            "NUMBER(3,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(MOD('1.50', 2))");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(-'1.50')");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(+'1.50')");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF('1.50' * NULL)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(NULL * '1.50')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for function "
                + "'*': (VARCHAR(4), BOOLEAN)",
            "SELECT SYSTEM$TYPEOF('1.50' * TRUE)");
        assertAnswer(
            "NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(n * '1.50') FROM (SELECT 1 n)");
        assertAnswer(
            "",
            "SELECT SYSTEM$TYPEOF(n * '1.50'), SYSTEM$TYPEOF(n + ' 1 '), SYSTEM$TYPEOF(n52 * '1.50'), "
                + "SYSTEM$TYPEOF(f * '1.50') FROM rt");
        assertAnswer(
            "",
            "SELECT SYSTEM$TYPEOF(g * 1), SYSTEM$TYPEOF(g + n), SYSTEM$TYPEOF(g * '1.50') FROM rt");
        assertAnswer(
            "1.5|1.00000|100|2.5",
            "SELECT '1.50' * 1 AS a, ' 1 ' * 1 AS b, '1e2' * 1 AS c, '1.50' + 1 AS d");
        assertShape(
            "A:NUMBER(19,1), B:NUMBER(19,5), C:NUMBER(19,0), D:NUMBER(19,1)",
            "SELECT '1.50' * 1 AS a, ' 1 ' * 1 AS b, '1e2' * 1 AS c, '1.50' + 1 AS d");
        assertAnswer(
            "",
            "SELECT n * '1.50' AS a, n + '1.50' AS b, n * ' 1 ' AS c, n + '1e2' AS d FROM rt");
        assertShape(
            "A:NUMBER(38,1), B:NUMBER(38,1), C:NUMBER(38,5), D:NUMBER(38,0)",
            "SELECT n * '1.50' AS a, n + '1.50' AS b, n * ' 1 ' AS c, n + '1e2' AS d FROM rt");
        assertAnswer(
            "1.123456789|1.12346|0.5000000|0.5|5.0",
            "SELECT '1.123456789' * 1 AS a, ' 1.123456789 ' * 1 AS b, '1.50' / 3 AS c, '1.50' % 1 AS d, '2.5' * 2 "
                + "AS e");
        assertShape(
            "A:NUMBER(19,9), B:NUMBER(19,5), C:NUMBER(24,7), D:NUMBER(3,1), E:NUMBER(19,1)",
            "SELECT '1.123456789' * 1 AS a, ' 1.123456789 ' * 1 AS b, '1.50' / 3 AS c, '1.50' % 1 AS d, '2.5' * 2 "
                + "AS e");
        assertAnswer(
            "0.5|-0.5|1.5|1.875|3.50000",
            "SELECT '1.50' - 1 AS a, 1 - '1.50' AS b, '1.50' * 1.0 AS c, '1.50' * 1.25 AS d, ' 2 ' + 1.5 AS e");
        assertShape(
            "A:NUMBER(19,1), B:NUMBER(19,1), C:NUMBER(19,1), D:NUMBER(21,3), E:NUMBER(19,5)",
            "SELECT '1.50' - 1 AS a, 1 - '1.50' AS b, '1.50' * 1.0 AS c, '1.50' * 1.25 AS d, ' 2 ' + 1.5 AS e");
        assertAnswer(
            "1.999999|2.00000|2.00000|15",
            "SELECT '1.999999' * 1 AS a, ' 1.999999 ' * 1 AS b, ' 1.9999999 ' * 1 AS c, '1.50e1' * 1 AS d");
        assertShape(
            "A:NUMBER(19,6), B:NUMBER(19,5), C:NUMBER(19,5), D:NUMBER(19,0)",
            "SELECT '1.999999' * 1 AS a, ' 1.999999 ' * 1 AS b, ' 1.9999999 ' * 1 AS c, '1.50e1' * 1 AS d");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID('1.50' * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1 AS NUMBER(19,5)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(' 1 ' * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found 'RT.N * "
                + "(TO_NUMBER('1.50', 18, 1))'",
            "SELECT RANDOM(n * '1.50') FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found 'RT.N * "
                + "(TO_NUMBER(' 1 ', 18, 5))'",
            "SELECT RANDOM(n * ' 1 ') FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found 'RT.N + "
                + "(TO_NUMBER('1e2'))'",
            "SELECT RANDOM(n + '1e2') FROM rt");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'(TO_NUMBER(RT.G, 18, 5)) * 1'",
            "SELECT RANDOM(g * 1) FROM rt");
        assertAnswer(
            "REFUSED Numeric value 'abc' is not recognized",
            "SELECT 'abc' * 1");
        assertAnswer(
            "REFUSED Numeric value '' is not recognized",
            "SELECT '' * 1");
        assertAnswer(
            "true|true|true",
            "SELECT '1.50' * 1 = 1.5, ' 1 ' * 1 = 1, '1.50' + 1 > 2");
    }
}
