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
 * The echo of a text conditional read as a number beside an exact one. An IFF or a CASE over text literals
 * reads at the widest scale its results spell, TO_NUMBER(IFF(…), 18, 1), where NVL and any other text value
 * read at NUMBER(18,5); a product by one folds into a cast to the product's type; a term of a smaller scale is
 * raised as beside a number of the width the results spell, CAST(0 AS NUMBER(3,1)), and beside a term of a
 * larger scale the conditional reads at the term's; a CASE matching one constant against others folds to the
 * result it picks, and a text a call made reads as a text value does (live-verified).
 */
public class TextConditionalNumberEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aProductASumAndAQuotient() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1) AS NUMBER(19,1)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) * 2 AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) + (CAST(0 AS NUMBER(3,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 0)'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(NVL('1.50', '2'), 18, 5) AS NUMBER(19,5)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(NVL('1.50', '2') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1) AS NUMBER(19,1)) "
                + "AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN '1.50' ELSE '2' END * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID('1.50' * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1) AS NUMBER(24,7))) / 1 AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') / 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) - (CAST(0 AS NUMBER(3,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') - 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1) AS NUMBER(19,1)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(1 * IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2.25'), 18, 2) AS NUMBER(19,2)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2.25') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '15', '2'), 18, 0) AS NUMBER(19,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '15', '2') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(IFF(CAST(TRUE AS BOOLEAN), 1.5, CAST(2 AS NUMBER(2,1))) AS NUMBER(3,1)) AS NUMBER(18,"
                + "0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, 1.50, 2) * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'T.A * 1'",
            "SELECT LAST_QUERY_ID(a * 1) FROM t");
    }

    @Test
    public void theTermsWidth() {
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2.25'), 18, 2)) + (CAST(0 AS NUMBER(4,2))) "
                + "AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2.25') + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) + (CAST(5 AS NUMBER(3,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) + (CAST(10 AS NUMBER(3,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + 10)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) + (CAST(123 AS NUMBER(4,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + 123)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '123.50', '2'), 18, 1)) + (CAST(0 AS NUMBER(4,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '123.50', '2') + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(FALSE AS BOOLEAN), '1.50', '22222.5'), 18, 1)) + (CAST(0 AS NUMBER(6,"
                + "1))) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(FALSE, '1.50', '22222.5') + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 2)) + 0.25 AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + 0.25)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) + 1.5 AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + 1.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '15', '2'), 18, 0)) + 0'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '15', '2') + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(0 AS NUMBER(3,1))) + (TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(0 + IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) - (CAST(1 AS NUMBER(3,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') - 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(3,1))) - (TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(1 - IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1) AS NUMBER(19,1)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * 1.0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) * 1.5 AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * 1.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1) AS NUMBER(24,7))) / 2 AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') / 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,7))) / (TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(2 / IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) % (CAST(2 AS NUMBER(3,1))) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') % 2)");
    }

    @Test
    public void aCaseAndOtherTextValues() {
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) + (CAST(0 AS "
                + "NUMBER(3,1))) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN '1.50' ELSE '2' END + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 1)) * 2 AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN '1.50' ELSE '2' END * 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, 2) AS NUMBER(2,0))'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN 1 ELSE 2 END * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, 2)'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN 1 ELSE 2 END)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'TO_NUMBER(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), '1.50', '2'), 18, 0)'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN '1.50' ELSE '2' END)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(CASE 1 WHEN 1 THEN '1.50' ELSE '2' END * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(NVL('1.50', '2'), 18, 5)) + (CAST(0 AS NUMBER(18,5))) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(NVL('1.50', '2') + 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFNULL('1.50', '2'), 18, 5) AS NUMBER(19,5)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(COALESCE('1.50', '2') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), 'abc', '2'), 18, 5) AS NUMBER(19,5)) AS NUMBER(18,"
                + "0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, 'abc', '2') * 1)");
        answer(
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', NULL) * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), ' 1.50 ', '2'), 18, 5) AS NUMBER(19,5)) AS "
                + "NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, ' 1.50 ', '2') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1.5 AS NUMBER(19,5)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(UPPER('1.50') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(TRIM('1.50'), 18, 5) AS NUMBER(19,5)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(TRIM('1.50') * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((TO_NUMBER(SUBSTR('1.50', 1), 18, 5)) * 2 AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(SUBSTR('1.50', 1) * 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(0 AS NUMBER(19,1)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * 0)");
        answer(
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN '1.50' END * 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, 2)) + 1'",
            "SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN 1 ELSE 2 END + 1)");
        answer(
            "SELECT GETVARIABLE(CASE WHEN TRUE THEN 'a' ELSE 'b' END)");
        answer(
            "SELECT GETVARIABLE(IFF(TRUE, 'a', 'b'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2') AS FLOAT)) + (CAST(IFF(CAST(TRUE AS BOOLEAN), "
                + "'1.50', '2') AS FLOAT)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') + IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2') AS FLOAT)) * (CAST(IFF(CAST(TRUE AS BOOLEAN), "
                + "'1.50', '2') AS FLOAT)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(NEGATE(CAST(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2') AS FLOAT)) AS NUMBER(18,0))'",
            "SELECT LAST_QUERY_ID(-IFF(TRUE, '1.50', '2'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LAST_QUERY_ID': (BOOLEAN)",
            "SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2') = 1)");
    }
}
