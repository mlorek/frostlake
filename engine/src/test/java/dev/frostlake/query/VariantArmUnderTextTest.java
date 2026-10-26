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
 * A VARIANT arm after a text arm converts to the text column's type as a cast converts it: equal strings fold
 * into one row, EXCEPT removes them and INTERSECT keeps them, a JSON null is NULL, a temporal takes its output
 * form, and a value whose text is longer than the column is refused, unless it is a number or boolean constant
 * wrapped into the VARIANT (all live-verified).
 */
public class VariantArmUnderTextTest extends MeasuredCellsTestSupport {

    @Test
    public void variantStringsFoldIntoTheTextColumn() {
        assertAnswer("a", "SELECT 'a' v UNION SELECT 'a'::VARIANT");
        assertAnswer("1|1", "SELECT COUNT(DISTINCT v), COUNT(*) FROM (SELECT 'a' v UNION SELECT 'a'::VARIANT)");
        assertAnswer("", "SELECT 'a' v EXCEPT SELECT 'a'::VARIANT");
        assertAnswer("a", "SELECT 'a' v INTERSECT SELECT 'a'::VARIANT");
        assertAnswer("Table TV726 successfully created.",
            "CREATE OR REPLACE TABLE tv726 AS SELECT 'a' v UNION SELECT 'a'::VARIANT");
        assertAnswer("a", "SELECT v FROM tv726");
        engine.execute("DESCRIBE TABLE tv726");
        assertAnswer("V|VARCHAR(1)", "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED String '{\"k\":1}' is too long and would be truncated",
            "SELECT v, SYSTEM$TYPEOF(v) FROM (SELECT 'x' v UNION ALL SELECT PARSE_JSON('{\"k\":1}')) ORDER BY 1");
        assertAnswer("1 ;; x", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT 1::VARIANT) ORDER BY 1");
        assertAnswer("1.5 ;; x", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(1.5)) ORDER BY 1");
        assertAnswer("true ;; x", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(TRUE)) ORDER BY 1");
        assertAnswer("x ;; null", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT PARSE_JSON('null')) ORDER BY 1");
        assertAnswer("REFUSED String '[1,2]' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT PARSE_JSON('[1,2]')) ORDER BY 1");
        assertAnswer("a ;; b", "SELECT v FROM (SELECT 'a' v UNION SELECT 'a'::VARIANT UNION SELECT 'b') ORDER BY 1");
        assertAnswer("REFUSED String 'abc' is too long and would be truncated",
            "SELECT LENGTH(v) FROM (SELECT 'ab' v UNION ALL SELECT 'abc'::VARIANT) ORDER BY 1");
        assertAnswer("a", "SELECT v FROM (SELECT 'a'::VARCHAR(3) v UNION SELECT 'a'::VARIANT) ORDER BY 1");
    }

    @Test
    public void aVariantValueIsHeldToTheTextColumnAsACastHoldsIt() {
        assertAnswer("1.5", "SELECT TO_VARIANT(1.5)::VARCHAR(1)");
        assertAnswer("REFUSED String '1.5' is too long and would be truncated", "SELECT 1.5::VARCHAR(1)");
        assertAnswer("true", "SELECT TO_VARIANT(TRUE)::VARCHAR(1)");
        assertAnswer("REFUSED String '{\"k\":1}' is too long and would be truncated",
            "SELECT PARSE_JSON('{\"k\":1}')::VARCHAR(1)");
        assertAnswer("REFUSED String 'abc' is too long and would be truncated", "SELECT 'abc'::VARIANT::VARCHAR(2)");
        assertAnswer("REFUSED String '[1,2]' is too long and would be truncated",
            "SELECT PARSE_JSON('[1,2]')::VARCHAR(2)");
        assertAnswer("null", "SELECT PARSE_JSON('null')::VARCHAR(1)");
        assertAnswer("REFUSED String '2024-01-01' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT('2024-01-01'::DATE)) ORDER BY 1");
        assertAnswer("12 ;; ab", "SELECT v FROM (SELECT 'ab' v UNION SELECT TO_VARIANT(12)) ORDER BY 1");
        assertAnswer("12", "SELECT v FROM (SELECT '12' v UNION SELECT TO_VARIANT(12)) ORDER BY 1");
        assertAnswer("true", "SELECT v FROM (SELECT 'true' v UNION SELECT TO_VARIANT(TRUE)) ORDER BY 1");
        assertAnswer("x|VARCHAR[LOB] ;; {\"k\":1}|VARCHAR[LOB]",
            "SELECT v, SYSTEM$TYPEOF(v) FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT PARSE_JSON('{\"k\":1}')) ORDER "
                + "BY 1");
        assertAnswer("x ;; {\"a\":[1,2],\"k\":1}",
            "SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT PARSE_JSON('{\"k\": 1, \"a\": [1, 2]}')) ORDER BY "
                + "1");
        assertAnswer("1.5 ;; x",
            "SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT TO_VARIANT(1.50::NUMBER(5,2))) ORDER BY 1");
        assertAnswer("1.0E10 ;; x",
            "SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT TO_VARIANT(1e10::FLOAT)) ORDER BY 1");
        answer("SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT TO_VARIANT('2024-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ)) ORDER BY 1");
        assertAnswer("Table TV727 successfully created.",
            "CREATE OR REPLACE TABLE tv727 AS SELECT 'x'::VARCHAR v UNION ALL SELECT PARSE_JSON('{\"k\":1}')");
        engine.execute("DESCRIBE TABLE tv727");
        assertAnswer("V|VARCHAR(16777216)", "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("a", "SELECT 'a' v INTERSECT SELECT TO_VARIANT('a')");
        assertAnswer("", "SELECT 'a' v EXCEPT SELECT PARSE_JSON('\"a\"')");
        assertAnswer("x ;; y", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT PARSE_JSON('\"y\"')) ORDER BY 1");
        assertAnswer("1.5 ;; abc ;; x",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(1.5) UNION ALL SELECT 'abc') ORDER BY 1");
    }

    @Test
    public void computedAndStoredVariantsAreHeldToTheLength() {
        assertAnswer("REFUSED String '1.414213562' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(SQRT(2))) ORDER BY 1");
        assertAnswer("REFUSED String '12345' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT PARSE_JSON('12345')) ORDER BY 1");
        assertAnswer("REFUSED String 'true' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT PARSE_JSON('true')) ORDER BY 1");
        assertAnswer("Table TV728 successfully created.", "CREATE OR REPLACE TABLE tv728 (v VARIANT)");
        assertAnswer("1", "INSERT INTO tv728 SELECT TO_VARIANT(12345)");
        assertAnswer("1", "INSERT INTO tv728 SELECT PARSE_JSON('true')");
        assertAnswer("REFUSED String '12345' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT v FROM tv728) ORDER BY 1");
        assertAnswer("7 ;; x", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(7)) ORDER BY 1");
        assertAnswer("1.25 ;; x", "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(1.25::FLOAT)) ORDER BY 1");
        assertAnswer("REFUSED String 'ab' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT('ab')) ORDER BY 1");
        assertAnswer("ab ;; xy", "SELECT v FROM (SELECT 'xy' v UNION ALL SELECT TO_VARIANT('ab')) ORDER BY 1");
        assertAnswer("REFUSED String '10:00:00' is too long and would be truncated",
            "SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT('10:00:00'::TIME)) ORDER BY 1");
        answer("SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT TO_VARIANT('2024-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ)) ORDER BY 1");
        assertAnswer("2024-01-01 ;; x",
            "SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT TO_VARIANT('2024-01-01'::DATE)) ORDER BY 1");
        assertAnswer("AB ;; x", "SELECT v FROM (SELECT 'x'::VARCHAR v UNION ALL SELECT TO_VARIANT(X'AB')) ORDER BY 1");
        assertAnswer("REFUSED String '1.414213562' is too long and would be truncated",
            "SELECT 'x' v UNION SELECT TO_VARIANT(SQRT(2))");
    }
}
