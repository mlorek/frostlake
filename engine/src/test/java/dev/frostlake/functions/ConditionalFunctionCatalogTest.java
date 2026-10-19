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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The conditional functions the catalog lists — COALESCE, NVL, IFNULL, NVL2, NULLIF, IFF, DECODE, GREATEST,
 * LEAST, EQUAL_NULL and IS_NULL_VALUE — by what they answer over NULLs and mixed types and by the type each
 * declares (live-verified).
 */
public class ConditionalFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void theFirstThatIsNotNull() {
        assertAnswer(
            "2|NUMBER(1,0)[SB1]",
            "SELECT COALESCE(NULL, 2, 3), SYSTEM$TYPEOF(COALESCE(NULL, 2, 3))");
        assertAnswer(
            "a|VARCHAR(134217728)[LOB]",
            "SELECT COALESCE(NULL, 'a'), SYSTEM$TYPEOF(COALESCE(NULL, 'a'))");
        assertAnswer(
            "null|NULL[LOB]",
            "SELECT COALESCE(NULL, NULL), SYSTEM$TYPEOF(COALESCE(NULL, NULL))");
        assertAnswer(
            "1.0|NUMBER(2,1)[SB1]",
            "SELECT COALESCE(1, 2.5), SYSTEM$TYPEOF(COALESCE(1, 2.5))");
        assertAnswer(
            "1.00000|NUMBER(18,5)[SB4]",
            "SELECT COALESCE(1, 'a'), SYSTEM$TYPEOF(COALESCE(1, 'a'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[COALESCE(1)], expected 2, got 1",
            "SELECT COALESCE(1), SYSTEM$TYPEOF(COALESCE(1))");
        assertAnswer(
            "2024-01-02|DATE[SB4]",
            "SELECT COALESCE(NULL::DATE, '2024-01-02'), SYSTEM$TYPEOF(COALESCE(NULL::DATE, '2024-01-02'))");
        assertAnswer(
            "2|NUMBER(1,0)[SB1]",
            "SELECT NVL(NULL, 2), SYSTEM$TYPEOF(NVL(NULL, 2))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT NVL(1, 2), SYSTEM$TYPEOF(NVL(1, 2))");
        assertAnswer(
            "2|NUMBER(1,0)[SB1]",
            "SELECT IFNULL(NULL, 2), SYSTEM$TYPEOF(IFNULL(NULL, 2))");
        assertAnswer(
            "a|VARCHAR(1)[LOB]",
            "SELECT IFNULL('a', 'b'), SYSTEM$TYPEOF(IFNULL('a', 'b'))");
        assertAnswer(
            "null|NULL[LOB]",
            "SELECT NVL(NULL, NULL), SYSTEM$TYPEOF(NVL(NULL, NULL))");
        assertAnswer(
            "1.0|NUMBER(2,1)[SB1]",
            "SELECT NVL(1, 2.5), SYSTEM$TYPEOF(NVL(1, 2.5))");
        assertAnswer(
            "2|NUMBER(1,0)[SB1]",
            "SELECT NVL2(1, 2, 3), SYSTEM$TYPEOF(NVL2(1, 2, 3))");
        assertAnswer(
            "3|NUMBER(1,0)[SB1]",
            "SELECT NVL2(NULL, 2, 3), SYSTEM$TYPEOF(NVL2(NULL, 2, 3))");
        assertAnswer(
            "2.0|NUMBER(2,1)[SB1]",
            "SELECT NVL2('a', 2, 3.5), SYSTEM$TYPEOF(NVL2('a', 2, 3.5))");
        assertAnswer(
            "null|NUMBER(1,0)[SB1]",
            "SELECT NVL2(NULL, 2, NULL), SYSTEM$TYPEOF(NVL2(NULL, 2, NULL))");
    }

    @Test
    public void choicesByEqualityAndCondition() {
        assertAnswer(
            "null|NUMBER(1,0)[SB1]",
            "SELECT NULLIF(1, 1), SYSTEM$TYPEOF(NULLIF(1, 1))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT NULLIF(1, 2), SYSTEM$TYPEOF(NULLIF(1, 2))");
        assertAnswer(
            "a|VARCHAR(1)[LOB]",
            "SELECT NULLIF('a', 'b'), SYSTEM$TYPEOF(NULLIF('a', 'b'))");
        assertAnswer(
            "null|NULL[LOB]",
            "SELECT NULLIF(NULL, 1), SYSTEM$TYPEOF(NULLIF(NULL, 1))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT NULLIF(1, NULL), SYSTEM$TYPEOF(NULLIF(1, NULL))");
        assertAnswer(
            "null|NUMBER(1,0)[SB1]",
            "SELECT NULLIF(1.0, 1), SYSTEM$TYPEOF(NULLIF(1.0, 1))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT IFF(TRUE, 1, 2), SYSTEM$TYPEOF(IFF(TRUE, 1, 2))");
        assertAnswer(
            "2|NUMBER(1,0)[SB1]",
            "SELECT IFF(FALSE, 1, 2), SYSTEM$TYPEOF(IFF(FALSE, 1, 2))");
        assertAnswer(
            "2|NUMBER(1,0)[SB1]",
            "SELECT IFF(NULL, 1, 2), SYSTEM$TYPEOF(IFF(NULL, 1, 2))");
        assertAnswer(
            "a|VARCHAR(2)[LOB]",
            "SELECT IFF(1 = 1, 'a', 'bc'), SYSTEM$TYPEOF(IFF(1 = 1, 'a', 'bc'))");
        assertAnswer(
            "1.0|NUMBER(2,1)[SB1]",
            "SELECT IFF(TRUE, 1, 2.5), SYSTEM$TYPEOF(IFF(TRUE, 1, 2.5))");
        assertAnswer(
            "1.00000|NUMBER(18,5)[SB4]",
            "SELECT IFF(TRUE, 1, 'a'), SYSTEM$TYPEOF(IFF(TRUE, 1, 'a'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF(1, 2, 3), SYSTEM$TYPEOF(IFF(1, 2, 3))");
        assertAnswer(
            "b|VARCHAR(134217728)[LOB]",
            "SELECT DECODE(2, 1, 'a', 2, 'b'), SYSTEM$TYPEOF(DECODE(2, 1, 'a', 2, 'b'))");
        assertAnswer(
            "null|VARCHAR(134217728)[LOB]",
            "SELECT DECODE(3, 1, 'a', 2, 'b'), SYSTEM$TYPEOF(DECODE(3, 1, 'a', 2, 'b'))");
        assertAnswer(
            "z|VARCHAR(1)[LOB]",
            "SELECT DECODE(3, 1, 'a', 2, 'b', 'z'), SYSTEM$TYPEOF(DECODE(3, 1, 'a', 2, 'b', 'z'))");
        assertAnswer(
            "n|VARCHAR(1)[LOB]",
            "SELECT DECODE(NULL, NULL, 'n', 'z'), SYSTEM$TYPEOF(DECODE(NULL, NULL, 'n', 'z'))");
        assertAnswer(
            "10.0|NUMBER(3,1)[SB1]",
            "SELECT DECODE(1, 1, 10, 2, 20.5), SYSTEM$TYPEOF(DECODE(1, 1, 10, 2, 20.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DECODE(1, 1)], expected 3, got 2",
            "SELECT DECODE(1, 1), SYSTEM$TYPEOF(DECODE(1, 1))");
    }

    @Test
    public void extremesAndNullTests() {
        assertAnswer(
            "3|NUMBER(1,0)[SB1]",
            "SELECT GREATEST(1, 3, 2), SYSTEM$TYPEOF(GREATEST(1, 3, 2))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT LEAST(1, 3, 2), SYSTEM$TYPEOF(LEAST(1, 3, 2))");
        assertAnswer(
            "null|NUMBER(1,0)[SB1]",
            "SELECT GREATEST(1, NULL), SYSTEM$TYPEOF(GREATEST(1, NULL))");
        assertAnswer(
            "b|VARCHAR(1)[LOB]",
            "SELECT GREATEST('a', 'b'), SYSTEM$TYPEOF(GREATEST('a', 'b'))");
        assertAnswer(
            "2.5|NUMBER(2,1)[SB1]",
            "SELECT GREATEST(1, 2.5), SYSTEM$TYPEOF(GREATEST(1, 2.5))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT LEAST(1, '2'), SYSTEM$TYPEOF(LEAST(1, '2'))");
        assertAnswer(
            "2024-03-01|DATE[SB4]",
            "SELECT GREATEST('2024-01-02'::DATE, '2024-03-01'::DATE), SYSTEM$TYPEOF(GREATEST('2024-01-02'::DATE, "
                + "'2024-03-01'::DATE))");
        assertAnswer(
            "1|NUMBER(1,0)[SB1]",
            "SELECT GREATEST(1), SYSTEM$TYPEOF(GREATEST(1))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT EQUAL_NULL(1, 1), SYSTEM$TYPEOF(EQUAL_NULL(1, 1))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT EQUAL_NULL(NULL, NULL), SYSTEM$TYPEOF(EQUAL_NULL(NULL, NULL))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT EQUAL_NULL(1, NULL), SYSTEM$TYPEOF(EQUAL_NULL(1, NULL))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT EQUAL_NULL('a', 'A'), SYSTEM$TYPEOF(EQUAL_NULL('a', 'A'))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT EQUAL_NULL(1, 1.0), SYSTEM$TYPEOF(EQUAL_NULL(1, 1.0))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT IS_NULL_VALUE(PARSE_JSON('null')), SYSTEM$TYPEOF(IS_NULL_VALUE(PARSE_JSON('null')))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT IS_NULL_VALUE(PARSE_JSON('1')), SYSTEM$TYPEOF(IS_NULL_VALUE(PARSE_JSON('1')))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT IS_NULL_VALUE(NULL), SYSTEM$TYPEOF(IS_NULL_VALUE(NULL))");
        assertAnswer(
            "true|BOOLEAN[ROWINDEX]",
            "SELECT IS_NULL_VALUE(PARSE_JSON('{\"a\":null}'):a), "
                + "SYSTEM$TYPEOF(IS_NULL_VALUE(PARSE_JSON('{\"a\":null}'):a))");
        assertAnswer(
            "null|BOOLEAN[ROWINDEX]",
            "SELECT IS_NULL_VALUE(PARSE_JSON('{\"a\":null}'):b), "
                + "SYSTEM$TYPEOF(IS_NULL_VALUE(PARSE_JSON('{\"a\":null}'):b))");
        assertAnswer(
            "false|BOOLEAN[ROWINDEX]",
            "SELECT IS_NULL_VALUE(1), SYSTEM$TYPEOF(IS_NULL_VALUE(1))");
    }
}
