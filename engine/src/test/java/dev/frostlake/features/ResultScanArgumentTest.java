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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RESULT_SCAN judges its argument as written, while the statement compiles (live-verified): a whole
 * number names a statement as LAST_QUERY_ID's index does, a NULL, a boolean, a fraction or a binary is
 * refused as not a string, a computed argument as not constant — naming the line and column of its top
 * token — and an index past 10,000 either way for that.
 */
public class ResultScanArgumentTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    private String scanned(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String notConstant(final int line, final int column, final String found) {
        return "SQL compilation error:\nargument " + line + " to function " + column
            + " needs to be constant, found '" + found + "'";
    }

    @Test
    public void aWholeNumberNamesAStatementAsLastQueryIdDoes() {
        engine.executeQuery("SELECT 'older' AS c");
        engine.executeQuery("SELECT 'newer' AS c");
        assertEquals("newer", scanned("SELECT * FROM TABLE(RESULT_SCAN(-1))"));
        engine.executeQuery("SELECT 'newer' AS c");
        assertEquals("newer", scanned("SELECT * FROM TABLE(RESULT_SCAN(-(1)))"));
        engine.executeQuery("SELECT 'older' AS c");
        engine.executeQuery("SELECT 'newer' AS c");
        assertEquals("older", scanned("SELECT * FROM TABLE(RESULT_SCAN(-2.0))"));
        engine.executeQuery("SELECT 'newer' AS c");
        assertEquals("newer", scanned("SELECT * FROM TABLE(RESULT_SCAN((-1E0)))"));
    }

    @Test
    public void aSessionVariableHoldingANumberIsAnIndexToo() {
        engine.executeQuery("SELECT 'marked' AS c");
        engine.execute("SET back = -2");
        assertEquals("marked", scanned("SELECT * FROM TABLE(RESULT_SCAN($back))"));
    }

    @Test
    public void anIndexNamingNoStatementIsStatementNull() {
        assertEquals("Statement NULL not found", refusal("SELECT * FROM TABLE(RESULT_SCAN(0))"));
        assertEquals("Statement NULL not found", refusal("SELECT * FROM TABLE(RESULT_SCAN(0.0))"));
    }

    @Test
    public void anIndexPastTenThousandIsRefused() {
        final String exceeds = "SQL compilation error:\nValue for parameter 1 exceeds maximum allowable value (10,000).";
        assertEquals(exceeds, refusal("SELECT * FROM TABLE(RESULT_SCAN(10001))"));
        assertEquals(exceeds, refusal("SELECT * FROM TABLE(RESULT_SCAN(-10001))"));
        assertEquals(exceeds, refusal("SELECT * FROM TABLE(RESULT_SCAN(1e5))"));
        assertEquals(exceeds, refusal("SELECT * FROM TABLE(RESULT_SCAN(9999999999))"));
    }

    @Test
    public void aValueThatIsNoStringIsRefused() {
        final String notAString = "SQL compilation error:\nargument needs to be a string: '1'";
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(NULL))"));
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(TRUE))"));
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(FALSE))"));
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(1.5))"));
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(-1.5))"));
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(X'AB'))"));
        assertEquals(notAString, refusal("SELECT * FROM TABLE(RESULT_SCAN(NULL)) WHERE FALSE"));
        // The number is the line the argument is written on.
        assertEquals("SQL compilation error:\nargument needs to be a string: '2'",
            refusal("SELECT *\nFROM TABLE(RESULT_SCAN(NULL))"));
    }

    @Test
    public void aComputedArgumentIsRefusedAtItsTopToken() {
        assertEquals(notConstant(1, 36, "::"), refusal("SELECT * FROM TABLE(RESULT_SCAN(NULL::VARCHAR))"));
        assertEquals(notConstant(1, 32, "TO_VARCHAR"), refusal("SELECT * FROM TABLE(RESULT_SCAN(TO_VARCHAR(NULL)))"));
        assertEquals(notConstant(1, 32, "to_varchar"), refusal("SELECT * FROM TABLE(RESULT_SCAN(to_varchar(NULL)))"));
        assertEquals(notConstant(1, 32, "IFF"), refusal("SELECT * FROM TABLE(RESULT_SCAN(IFF(FALSE, 'x', NULL)))"));
        assertEquals(notConstant(1, 71, "||"),
            refusal("SELECT * FROM TABLE(RESULT_SCAN('01b2c3d4-0000-0000-0000-000000000000' || ''))"));
        assertEquals(notConstant(1, 43, "||"), refusal("SELECT * FROM TABLE(RESULT_SCAN('a' || 'b' || 'c'))"));
        assertEquals(notConstant(1, 34, "+"), refusal("SELECT * FROM TABLE(RESULT_SCAN(1 + 1))"));
        assertEquals(notConstant(1, 32, "+"), refusal("SELECT * FROM TABLE(RESULT_SCAN(+3))"));
        assertEquals(notConstant(1, 32, "-"), refusal("SELECT * FROM TABLE(RESULT_SCAN(-(ABS(1))))"));
        assertEquals(notConstant(1, 32, "CAST"), refusal("SELECT * FROM TABLE(RESULT_SCAN(CAST('a' AS VARCHAR)))"));
        assertEquals(notConstant(1, 32, "CASE"), refusal("SELECT * FROM TABLE(RESULT_SCAN(CASE WHEN TRUE THEN 'a' END))"));
        assertEquals(notConstant(1, 34, "="), refusal("SELECT * FROM TABLE(RESULT_SCAN(1 = 1))"));
        assertEquals(notConstant(1, 32, "NOT"), refusal("SELECT * FROM TABLE(RESULT_SCAN(NOT TRUE))"));
        assertEquals(notConstant(1, 36, "IS"), refusal("SELECT * FROM TABLE(RESULT_SCAN('a' IS NULL))"));
        assertEquals(notConstant(1, 32, "[1]"), refusal("SELECT * FROM TABLE(RESULT_SCAN([1]))"));
        assertEquals(notConstant(1, 32, "PUBLIC"), refusal("SELECT * FROM TABLE(RESULT_SCAN(PUBLIC.F(1)))"));
        assertEquals(notConstant(3, 4, "UPPER"), refusal("SELECT *\n  FROM TABLE(RESULT_SCAN(\n    UPPER('x')))"));
    }

    @Test
    public void theOddShapesKeepTheirOwnSentences() {
        assertEquals("SQL compilation error:\nargument 0 to function -1 needs to be constant, found 'TOK_ANSI_LITERAL'",
            refusal("SELECT * FROM TABLE(RESULT_SCAN(DATE '2020-01-01'))"));
        assertEquals("SQL compilation error:\ninvalid function '-'", refusal("SELECT * FROM TABLE(RESULT_SCAN(- -1))"));
        assertEquals("SQL compilation error: error line 1 at position 55\nInvalid result query ID, found 's'",
            refusal("SELECT * FROM (SELECT 'abc' AS c) s, TABLE(RESULT_SCAN(s.c))"));
    }

    @Test
    public void aScalarSubqueryIsReadAsAValue() {
        assertEquals("Statement abc not found", refusal("SELECT * FROM TABLE(RESULT_SCAN((SELECT 'abc')))"));
        assertEquals("Statement abc not found", refusal("SELECT * FROM TABLE(RESULT_SCAN(('abc')))"));
    }
}
