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
 * A decimal below a millionth keeps its digits in place wherever it is spelled: its literal's type is the
 * NUMBER its digits ask for - 0.0000001 is NUMBER(8,7), also written 1e-7 - a table made from it declares that
 * type, a refusal echoes it as 0.0000001, and an ARRAY, an OBJECT and TO_JSON write it so. Every answer is
 * live-verified.
 */
public class SmallDecimalTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aSmallLiteralsTypeAndEcho() {
        assertAnswer(
            "NUMBER(7,6)[SB1]|NUMBER(8,7)[SB1]|NUMBER(9,8)[SB1]|NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(0.000001), SYSTEM$TYPEOF(0.0000001), SYSTEM$TYPEOF(0.00000012), "
                + "SYSTEM$TYPEOF(-0.0000001)");
        assertAnswer(
            "NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(a) FROM (SELECT 0.0000001 AS a)");
        assertAnswer(
            "Table LIT successfully created.",
            "CREATE OR REPLACE TABLE lit AS SELECT 0.0000001 AS a, 0.00000012 AS b, -0.0000001 AS c, 1.0000001 AS "
                + "d");
        assertAnswer(
            "A|NUMBER(8,7)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; B|NUMBER(9,"
                + "8)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; C|NUMBER(8,"
                + "7)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; D|NUMBER(8,"
                + "7)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE lit");
        assertAnswer(
            "A|NUMBER(8,7) ;; B|NUMBER(9,8) ;; C|NUMBER(8,7) ;; D|NUMBER(8,7)",
            "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "NUMBER(8,7)[SB1]|NUMBER(9,8)[SB1]|NUMBER(13,12)[SB1]|NUMBER(8,7)[SB4]",
            "SELECT SYSTEM$TYPEOF(0.0000005), SYSTEM$TYPEOF(0.00000001), SYSTEM$TYPEOF(0.000000000012), "
                + "SYSTEM$TYPEOF(1.0000001)");
        assertAnswer(
            "NUMBER(8,7)[SB1]|NUMBER(10,7)[SB4]|NUMBER(8,7)[SB1]|NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(0.00000010), SYSTEM$TYPEOF(100.0000001), SYSTEM$TYPEOF(.0000001), "
                + "SYSTEM$TYPEOF(0.00000010000)");
        assertAnswer(
            "1E-7|1.2E-7|-1E-7|1E-7",
            "SELECT 0.0000001, 0.00000012, -0.0000001, 0.00000010");
        assertAnswer(
            "0.0000001|0.0000001|0.0000001|0.00000012",
            "SELECT 0.0000001::VARCHAR, TO_VARCHAR(0.0000001), 0.0000001 || '', TO_CHAR(0.00000012)");
        answer(
            "SELECT TYPEOF(0.0000001::VARIANT), 0.0000001::VARIANT, TO_JSON(0.0000001::VARIANT)");
        assertAnswer(
            "1E-7|NUMBER(8,7)[SB1]|NUMBER(9,8)[SB1]",
            "SELECT 1e-7, SYSTEM$TYPEOF(1e-7), SYSTEM$TYPEOF(1.2e-7)");
        assertAnswer(
            "NUMBER(10,7)[SB4]|NUMBER(9,7)[SB1]|NUMBER(13,12)[SB4]|NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(0.0000001 + 1), SYSTEM$TYPEOF(0.0000001 * 2), SYSTEM$TYPEOF(0.0000001 / 2), "
                + "SYSTEM$TYPEOF(-(0.0000001))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[BOOLAND_AGG(0.0000001, 1)] expected 1, got 2",
            "SELECT BOOLAND_AGG(0.0000001, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.0000001, 1)] expected 1, got 2",
            "SELECT ABS(0.0000001, 1)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "SELECT 0.0000001 = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[LEFT('abc', 0.0000001, 1)] expected 2, got 3",
            "SELECT LEFT('abc', 0.0000001, 1)");
        answer(
            "SELECT DATEADD(day, 0.0000001)");
        assertAnswer(
            "[0.0000001]|{\"a\":0.0000001}",
            "SELECT ARRAY_CONSTRUCT(0.0000001), OBJECT_CONSTRUCT('a', 0.0000001)");
        assertAnswer(
            "VARIANT[LOB]|NUMBER(8,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CONSTRUCT(0.0000001)[0]), SYSTEM$TYPEOF(IFF(TRUE, 0.0000001, 1))");
        assertAnswer(
            "NUMBER(8,7)[SB1]|NUMBER(8,7)[SB1]|NUMBER(21,20)[SB1]",
            "SELECT SYSTEM$TYPEOF(0.000000100), SYSTEM$TYPEOF(0.0000012), SYSTEM$TYPEOF(0.00000000000000000001)");
        assertAnswer(
            "NUMBER(8,7)[SB1]|NUMBER(9,8)[SB1]|NUMBER(8,7)[SB1]|NUMBER(8,7)[SB4]",
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(c), SYSTEM$TYPEOF(d) FROM lit");
        assertAnswer(
            "1.0000000|2E-7|1E-7|2E-7",
            "SELECT 0.0000001 * 10000000, 0.0000001 + 0.0000001, ROUND(0.0000001, 7), ROUND(0.00000015, 7)");
        assertAnswer(
            "NUMBER(1,0)[SB1]|NUMBER(1,0)[SB1]|NUMBER(1,0)[SB1]|NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(0.0), SYSTEM$TYPEOF(0.00), SYSTEM$TYPEOF(0.0000000), SYSTEM$TYPEOF(0.00000000)");
        assertAnswer(
            "NUMBER(8,7)[SB1]|NUMBER(8,7)[SB1]|NUMBER(9,7)[SB1]",
            "SELECT SYSTEM$TYPEOF(COALESCE(0.0000001, 1)), SYSTEM$TYPEOF(NVL(NULL, 0.0000001)), "
                + "SYSTEM$TYPEOF(ABS(0.0000001))");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM (SELECT 0.0000001 AS a) WHERE a = 0.0000001");
        assertAnswer(
            "1.2E-7|1.0E-7",
            "SELECT GREATEST(0.0000001, 0.00000012), LEAST(0.0000001, 0.00000012)");
        assertAnswer(
            "1.0E-7|1E-7|0.000000",
            "SELECT TO_NUMBER(0.0000001, 10, 8), CAST(0.0000001 AS NUMBER(10,7)), 0.0000001::NUMBER(10,6)");
        assertAnswer(
            "NUMBER(9,8)[SB1]|1.5E-7|1.5E-7",
            "SELECT SYSTEM$TYPEOF(1.5E-7), 1.5E-7, 0.00000015");
    }

    @Test
    public void aSmallDecimalInJsonAndInARefusal() {
        answer(
            "SELECT 0.0000001::VARIANT, 0.00000012::VARIANT, 0.000001::VARIANT, 0.00001::VARIANT, 1.5::VARIANT, "
                + "TO_VARIANT(0.0000001)");
        answer(
            "SELECT PARSE_JSON('0.0000001'), PARSE_JSON('1e-7'), PARSE_JSON('0.00000012'), PARSE_JSON('1.5e-7'), "
                + "PARSE_JSON('0.000001')");
        assertAnswer(
            "[0.000001,0.0000001,0.00000012]|[0.0000001]|0.0000001",
            "SELECT ARRAY_CONSTRUCT(0.000001, 0.0000001, 0.00000012), TO_JSON(ARRAY_CONSTRUCT(0.0000001)), "
                + "0.0000001::VARIANT::VARCHAR");
        assertAnswer(
            "{\"a\":0.0000001,\"b\":0.00000012}|{\"a\":0.0000001}",
            "SELECT OBJECT_CONSTRUCT('a', 0.0000001, 'b', 0.00000012), TO_JSON(OBJECT_CONSTRUCT('a', 0.0000001))");
        assertAnswer(
            "0.0000001|1.000000000000000e-07|[0.0000001,1.000000000000000e-07]|0.0000001",
            "SELECT TO_JSON(PARSE_JSON('0.0000001')), TO_JSON(PARSE_JSON('1e-7')), PARSE_JSON('[0.0000001, "
                + "1e-7]'), TO_JSON(1e-7::VARIANT)");
        assertAnswer(
            "DECIMAL|DECIMAL|DOUBLE|DECIMAL",
            "SELECT TYPEOF(0.0000001::VARIANT), TYPEOF(PARSE_JSON('0.0000001')), TYPEOF(PARSE_JSON('1e-7')), "
                + "TYPEOF(1e-7::VARIANT)");
        assertAnswer(
            "true|1.0E-7|1.0E-7",
            "SELECT 0.0000001::VARIANT = 0.0000001, 0.0000001::VARIANT::NUMBER(10,8), "
                + "ARRAY_CONSTRUCT(0.0000001)[0]::NUMBER(10,8)");
        answer(
            "SELECT ARRAY_CONSTRUCT(0.0000001)[0], OBJECT_CONSTRUCT('a', 0.0000001):a, "
                + "GET(ARRAY_CONSTRUCT(0.00000012), 0)");
        assertAnswer(
            "[0.0000001,0.00000012]",
            "SELECT ARRAY_AGG(x) FROM (SELECT 0.0000001 AS x UNION ALL SELECT 0.00000012) ");
        assertAnswer(
            "{\"k\":0.0000001}",
            "SELECT OBJECT_AGG('k', 0.0000001::VARIANT)");
        assertAnswer(
            "0.0000001|0.00000012|0.0000001",
            "SELECT TO_VARCHAR(0.0000001::VARIANT), TO_CHAR(0.00000012::VARIANT), 0.0000001::VARIANT || ''");
        answer(
            "SELECT -0.0000001::VARIANT, 0.00000010::VARIANT, 100.0000001::VARIANT, 0.000000000012::VARIANT");
        assertAnswer(
            "[-0.0000001,0.0000001,100.0000001,0.000000000012]",
            "SELECT ARRAY_CONSTRUCT(-0.0000001, 0.00000010, 100.0000001, 0.000000000012)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.00000012, 1)] expected 1, got 2",
            "SELECT ABS(0.00000012, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(-0.0000001, 1)] expected 1, got 2",
            "SELECT ABS(-0.0000001, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(1.0000001, 1)] expected 1, got 2",
            "SELECT ABS(1.0000001, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.000001, 1)] expected 1, got 2",
            "SELECT ABS(0.000001, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.0000001, 1)] expected 1, got 2",
            "SELECT ABS(1e-7, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.0000001, 1)] expected 1, got 2",
            "SELECT ABS(0.00000010, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.0000001, 1)] expected 1, got 2",
            "SELECT ABS(.0000001, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(0.00000015, 1)] expected 1, got 2",
            "SELECT ABS(1.5E-7, 1)");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "SELECT 0.0000001 + 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (NUMBER(8,7), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF(0.0000001, 1, 2)");
        assertAnswer(
            "null",
            "SELECT 0.0000001:a");
        assertAnswer(
            "null",
            "SELECT GET(0.0000001, 1)");
        answer(
            "SELECT UPPER(0.0000001), LENGTH(0.0000001), 0.0000001 LIKE '%E%', CONCAT(0.0000001, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(0.0000001)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(0.0000001)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(8,7))",
            "SELECT DATEADD(day, 1, 0.0000001)");
        assertAnswer(
            "null",
            "SELECT ARRAY_SIZE(0.0000001)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SUM(0.0000001, 1)] expected 1, got 2",
            "SELECT SUM(0.0000001, 1)");
        answer(
            "SELECT COUNT(DISTINCT 0.0000001, 1) OVER (ORDER BY 0.0000001 ROWS 1 PRECEDING)");
        assertAnswer(
            "REFUSED SQL compilation error: | [0.0000001] is not a valid group by expression",
            "SELECT x FROM (SELECT 1 AS x) GROUP BY 0.0000001");
        assertAnswer(
            "REFUSED SQL compilation error: | [0.0000001] is not a valid order by expression",
            "SELECT 1 ORDER BY 0.0000001");
        assertAnswer(
            "null|1E-7|1",
            "SELECT CASE 0.0000001 WHEN 1 THEN 2 END, COALESCE(0.0000001, 2), NVL2(0.0000001, 1, 2)");
        assertAnswer(
            "true|true|1.0E-7|1e-07",
            "SELECT HASH(0.0000001) = HASH(1e-7), 0.0000001 = 1e-7, 0.0000001::FLOAT, 0.0000001::FLOAT::VARCHAR");
        assertAnswer(
            "1.0E-7|[1.000000000000000e-07]|1.200000000000000e-07",
            "SELECT TO_VARIANT(0.0000001::FLOAT), ARRAY_CONSTRUCT(0.0000001::FLOAT), "
                + "TO_JSON(TO_VARIANT(0.00000012::FLOAT))");
    }
}
