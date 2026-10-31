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
 * A quantifier's refusal echoes each cast in its call as written, its type spelled out -
 * {@code CAST('5' AS NUMBER(38,0))} for {@code CAST('5' AS INT)}, {@code CAST('5' AS NUMBER(10,2))} for
 * {@code '5'::NUMBER(10,2)}, {@code CAST(null AS NUMBER(38,0))}, {@code CAST(PARSE_JSON('1') AS VARIANT)} - where a
 * conversion's refusal prints the conversion the plan runs, {@code TO_OBJECT(TO_NUMBER('5'))}; LAST_QUERY_ID's index
 * over such casts folds as a block's does (live-verified).
 */
public class QuantifierCastEchoTest extends MeasuredCellsTestSupport {

    /** Quantified calls over casts, and TO_OBJECT over one. */
    @Test
    public void castsAsWritten() {
        assertCells(new String[][] {
            {"SELECT UPPER(DISTINCT 'it''s')",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'it''s')'"},
            {"SELECT ABS(ALL '5'::NUMBER(10,2))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS "
                + "NUMBER(10,2)))'"},
            {"SELECT ABS(ALL TO_NUMBER('5'))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL TO_NUMBER('5'))'"},
            {"SELECT ABS(ALL CAST('5' AS INT))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS "
                + "NUMBER(38,0)))'"},
            {"SELECT UPPER(DISTINCT CAST(5 AS VARCHAR))",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST(5 AS "
                + "VARCHAR(134217728)))'"},
            {"SELECT TO_OBJECT(CAST('5' AS NUMBER(38,0)))",
                "REFUSED SQL compilation error: | invalid type [TO_OBJECT(TO_NUMBER('5'))] for parameter 'TO_OBJECT'"},
            {"SELECT ABS(ALL CAST(NULL AS NUMBER(38,0)))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL CAST(null AS "
                + "NUMBER(38,0)))'"},
            {"SELECT ABS(ALL CAST(PARSE_JSON('1') AS VARIANT))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL CAST(PARSE_JSON('1') AS "
                + "VARIANT))'"},
            {"SELECT UPPER(DISTINCT CAST('true' AS BOOLEAN))",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('true' "
                + "AS BOOLEAN))'"},
            {"SELECT ABS(ALL (CAST('1' AS NUMBER(38,0))) + (CAST('2' AS NUMBER(38,0))))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('1' AS "
                + "NUMBER(38,0))) + (CAST('2' AS NUMBER(38,0))))'"},
            {"SELECT ABS(ALL CAST('5' AS NUMBER(38,0)) + 1)",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS "
                + "NUMBER(38,0))) + 1)'"},
            {"SELECT UPPER(DISTINCT CAST('1.50' AS NUMBER(10,2)))",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.50' "
                + "AS NUMBER(10,2)))'"},
            {"SELECT ABS(ALL (CAST('5' AS NUMBER(38,0))) + 1)",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS "
                + "NUMBER(38,0))) + 1)'"},
            {"SELECT ABS(ALL -CAST('5' AS NUMBER(38,0)))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL NEGATE(CAST('5' AS "
                + "NUMBER(38,0))))'"},
            {"SELECT CASE WHEN TRUE THEN ABS(ALL CAST('5' AS NUMBER(38,0)) + 1) END",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL (CAST('5' AS "
                + "NUMBER(38,0))) + 1)'"},
            {"SELECT UPPER(DISTINCT CAST('2024-01-02' AS DATE))",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT "
                + "CAST('2024-01-02' AS DATE))'"},
            {"SELECT ABS(ALL CAST('1.5' AS FLOAT))",
                "REFUSED SQL compilation error: | invalid use of 'all' for function 'ABS(ALL CAST('1.5' AS FLOAT))'"},
        });
    }

    /** LAST_QUERY_ID's index and RANDOM's seed over casts. */
    @Test
    public void queryIndexOverCasts() {
        assertCells(new String[][] {
            {"SELECT LAST_QUERY_ID(1 + 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 "
                + "+ 1'"},
            {"SELECT LAST_QUERY_ID(1 * 2)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 AS NUMBER(2,0))'"},
            {"SELECT LAST_QUERY_ID(2 * 3)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 "
                + "* 3'"},
            {"SELECT LAST_QUERY_ID(CAST('1' AS NUMBER(38,0)) + 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 "
                + "+ 1'"},
            {"SELECT LAST_QUERY_ID(CAST('1' AS NUMBER(38,0)) * 2)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 AS NUMBER(38,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(38,0)) * 3)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 "
                + "* 3'"},
            {"SELECT LAST_QUERY_ID(-CAST('1' AS NUMBER(38,0)))",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'NEGATE(1)'"},
            {"SELECT LAST_QUERY_ID(CAST('1.50' AS NUMBER(10,2)) + 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.5 + (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('1' AS FLOAT) + 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.0 + 1.0 AS NUMBER(18,0))'"},
            {"SELECT RANDOM(CAST('5' AS NUMBER(38,0)) + 1) = RANDOM(CAST('5' AS NUMBER(38,0)) + 1)",
                "true"},
            {"SELECT LAST_QUERY_ID(CAST('1' AS NUMBER(10,2)) * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1 AS NUMBER(11,2)) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) + 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('1.5' AS NUMBER(10,2)) * 2)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.5 * 2 AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('1' AS NUMBER(38,0)) / 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(SCALED_ROUND_INT_DIVIDE(1, 1) AS NUMBER(32,0))'"},
            {"SELECT LAST_QUERY_ID(CAST(CAST('1' AS NUMBER(38,0)) AS INT) + 0)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(1) + 0'"},
            {"SELECT LAST_QUERY_ID(CAST('3' AS NUMBER(38,0)) * 1) IS NOT NULL",
                "true"},
            {"SELECT LAST_QUERY_ID(CAST('1' AS NUMBER(38,0)) * CAST('1' AS NUMBER(38,0))) IS NOT NULL",
                "true"},
            {"SELECT LAST_QUERY_ID(CAST('0' AS NUMBER(38,0)) * 3) IS NOT NULL",
                "false"},
            {"SELECT LAST_QUERY_ID(ABS(CAST('1' AS NUMBER(38,0)))) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'ABS(1)'"},
            {"SELECT LAST_QUERY_ID((CAST('1' AS NUMBER(38,0)) + 1) * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 "
                + "+ 1'"},
            {"SELECT LAST_QUERY_ID('1' || '') IS NOT NULL",
                "true"},
        });
    }
}
