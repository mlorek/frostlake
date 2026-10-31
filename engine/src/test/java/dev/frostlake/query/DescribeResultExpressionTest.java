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
 * DESCRIBE RESULT reads an expression and takes the query ID of a constant one: a text, a number, negated or not,
 * a session variable or a LAST_QUERY_ID call, through any parentheses; a bare name, NULL and any other call or
 * operator are refused while the statement compiles, and a statement not found or that failed is refused as
 * RESULT_SCAN refuses it (all live-verified).
 */
public class DescribeResultExpressionTest extends MeasuredCellsTestSupport {

    @Test
    public void describedStatements() {
        assertAnswer("1", "SELECT 1 AS x");
        assertAnswer("2", "SELECT 2 AS y");
        assertAnswer("X|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID(-2)");
        assertAnswer("name|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; type|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; kind|VARCHAR(16777216)|COLUMN|"
                + "Y|null|N|N|null|null|null|null|null|null ;; null?|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|"
                + "null|null|null|null ;; default|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; primary key|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; unique key|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; check|VARCHAR(16777216)|"
                + "COLUMN|Y|null|N|N|null|null|null|null|null|null ;; expression|VARCHAR(16777216)|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null ;; comment|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; policy name|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "privacy domain|OBJECT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; write default|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID(-1)");
        assertAnswer("REFUSED Division by zero", "SELECT 1 / 0");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed", "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer("REFUSED Division by zero", "SELECT 1 / 0");
        assertAnswer("3", "SELECT 3 AS z");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed", "DESCRIBE RESULT LAST_QUERY_ID(-2)");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "DESCRIBE RESULT LAST_QUERY_ID( - 1 )");
        assertAnswer("REFUSED Statement NULL not found", "DESCRIBE RESULT LAST_QUERY_ID(0)");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed", "DESC RESULT LAST_QUERY_ID(-1)");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "DESCRIBE RESULT LAST_QUERY_ID(-2.0)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'TO_NUMBER('a', 18, 0)'",
            "DESCRIBE RESULT LAST_QUERY_ID('a')");
        assertAnswer("4", "SELECT 4 AS w");
        assertAnswer("5", "SELECT 5 AS v");
        assertAnswer("4", "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))");
        assertAnswer("W|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID(-1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'.",
            "DESCRIBE RESULT LAST_QUERY_ID(-1) x");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'NEGATE(A)'",
            "DESCRIBE RESULT LAST_QUERY_ID(-a)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found 'FOO'",
            "DESCRIBE RESULT FOO()");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found "
                + "'CURRENT_DATE'",
            "DESCRIBE RESULT CURRENT_DATE()");
        assertAnswer("REFUSED Statement not-an-id not found", "DESCRIBE RESULT 'not-an-id'");
        assertShapedAnswer("REFUSED Statement <QID> not found",
            "DESCRIBE RESULT '01234567-0000-0000-0000-000000000000'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for "
                + "function 'LAST_QUERY_ID': (NUMBER(1,0), NUMBER(1,0))",
            "DESCRIBE RESULT LAST_QUERY_ID(-1, 2)");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed", "DESCRIBE RESULT last_query_id(-1)");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "DESCRIBE RESULT \"LAST_QUERY_ID\"()");
        assertAnswer("REFUSED Statement NULL not found", "DESCRIBE RESULT LAST_QUERY_ID(-0)");
        assertAnswer("REFUSED Statement NULL not found", "DESCRIBE RESULT LAST_QUERY_ID(NULL)");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "DESCRIBE RESULT LAST_QUERY_ID(-2) ;");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 20 needs to be constant, found '||'",
            "DESCRIBE RESULT 'a' || 'b'");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "DESCRIBE RESULT (LAST_QUERY_ID(-3))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found 'CONCAT'",
            "DESCRIBE RESULT CONCAT('a', 'b')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | Invalid result query ID, found 'x'",
            "DESCRIBE RESULT x");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: '1'", "DESCRIBE RESULT NULL");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | Invalid result query ID, found "
                + "'LAST_QUERY_ID'",
            "DESCRIBE RESULT LAST_QUERY_ID");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ''b''.",
            "DESCRIBE RESULT 'a' 'b'");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found 'UPPER'",
            "DESCRIBE RESULT UPPER('x')");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 34 needs to be constant, found '||'",
            "DESCRIBE RESULT LAST_QUERY_ID(-3) || ''");
        assertAnswer("Statement executed successfully.", "SET sv = 'nope'");
        assertAnswer("REFUSED Statement nope not found", "DESCRIBE RESULT $sv");
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed", "DESCRIBE RESULT -1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found "
                + "'SYSTEM$TYPEOF'",
            "DESCRIBE RESULT SYSTEM$TYPEOF(1)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found 'foo'",
            "DESCRIBE RESULT foo.bar()");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 34 needs to be constant, found '+'",
            "DESCRIBE RESULT LAST_QUERY_ID(-1) + 1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'TO_NUMBER('a', 18, 0)'",
            "SELECT LAST_QUERY_ID('a')");
        assertAnswer("true", "SELECT LAST_QUERY_ID(+1) IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found '-1 - 1'",
            "SELECT LAST_QUERY_ID(-1 - 1) IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Value for parameter 1 exceeds maximum allowable value (10,000).",
            "DESCRIBE RESULT LAST_QUERY_ID(-10001)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found "
                + "'TO_VARCHAR'",
            "DESCRIBE RESULT TO_VARCHAR(LAST_QUERY_ID(-2))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 16 needs to be constant, found 'IFF'",
            "DESCRIBE RESULT IFF(TRUE, LAST_QUERY_ID(-2), NULL)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function 33 needs to be constant, found '::'",
            "DESCRIBE RESULT LAST_QUERY_ID(-2)::VARCHAR");
        assertAnswer("Statement executed successfully.", "UNSET sv");
    }
}
