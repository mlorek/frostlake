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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A plain word before a string is a typed literal of a type the account does not know, refused by the pair's text
 * wherever an expression stands — in a query, and in a function or policy body, where the sentence carries the body
 * compiler's own prefix. A type keyword or a quoted name before a string stays a syntax error at the string.
 */
public class UnknownTypedLiteralTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage().replace('\n', '|');
    }

    @Test
    public void aQueryRefusesThePairByItsText() {
        assertEquals("SQL compilation error:|Unsupported data type literal 'val 'x''.", refusal("SELECT val 'x'"));
        assertEquals("SQL compilation error:|Unsupported data type literal 'a 'x''.", refusal("SELECT a 'x' FROM t"));
        assertEquals("SQL compilation error:|Unsupported data type literal 'foo 'x''.",
            refusal("SELECT a FROM t WHERE a = foo 'x'"));
    }

    @Test
    public void aTypeKeywordOrAQuotedNameIsASyntaxError() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 15 unexpected ''x''.",
            refusal("SELECT VARCHAR 'x'"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 13 unexpected ''x''.",
            refusal("SELECT \"val\" 'x'"));
    }

    @Test
    public void aBodyRefusesItWithTheCompilersPrefix() {
        final String refused = "Compilation of SQL UDF failed: SQL compilation error:|Unsupported data type literal "
            + "'val 'x''.";
        assertEquals(refused, refusal("CREATE OR REPLACE FUNCTION f1(val STRING) RETURNS STRING AS 'val ''x'''"));
        assertEquals(refused, refusal("CREATE OR REPLACE FUNCTION f5() RETURNS STRING AS 'SELECT val ''x'''"));
        assertEquals(refused, refusal("CREATE OR REPLACE MASKING POLICY mp1 AS (val STRING) RETURNS STRING -> val 'x'"));
        assertEquals(refused,
            refusal("CREATE OR REPLACE ROW ACCESS POLICY rp1 AS (val STRING) RETURNS BOOLEAN -> val 'x'"));
    }
}
