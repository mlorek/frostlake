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
 * A parenthesized list given to a named argument — {@code x => (1, 2)} — is one ROW value, which only
 * INFER_SCHEMA's FILES takes. Every other call refuses it in its own words: a scalar call and a CALL before
 * anything is evaluated, each table function as its parameters are judged.
 */
public class NamedArgumentRowTest extends BaseDatabaseTest {

    private static final String AT_CALL = "SQL compilation error: error line 1 at position 7\n";

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION fx(x INT) RETURNS INT AS 'x'");
        engine.execute("CREATE FUNCTION fv(x VARIANT) RETURNS VARIANT AS 'x'");
        engine.execute("CREATE FUNCTION fxy(x INT, y INT) RETURNS INT AS 'x + y'");
    }

    @Test
    public void testScalarCalls() {
        assertRefused(AT_CALL + "named arguments [X] do not match any signature for function FX", "SELECT fx(x => (1,2))");
        assertRefused(AT_CALL + "named arguments [X] do not match any signature for function FV", "SELECT fv(x => (1,2))");
        assertRefused(AT_CALL + "named arguments [X, Y] do not match any signature for function FXY",
            "SELECT fxy(y => (1,2), x => 1)");
        assertRefused(AT_CALL + "named arguments [Y] do not match any signature for function FXY",
            "SELECT fxy(1, y => (1,2))");
        assertRefused(AT_CALL + "named arguments [X] do not match any signature for function FX",
            "SELECT fx(x => (1,2)) FROM (SELECT 1 AS c) WHERE 1 = 0");
        assertRefused(AT_CALL + "function UPPER does not support named arguments", "SELECT UPPER(x => ('a','b'))");
        assertRefused(AT_CALL + "function CONCAT does not support named arguments", "SELECT CONCAT('a', x => ('a','b'))");
        assertRefused("SQL compilation error:\nUnknown function NOSUCHFN.", "SELECT NOSUCHFN(x => (1,2))");
    }

    @Test
    public void testCall() {
        engine.execute("CREATE PROCEDURE pv(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; END $$");
        engine.execute("CREATE PROCEDURE pab(a VARCHAR, b INT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
        final String unanchored = "SQL compilation error: error line 0 at position -1\n";
        assertRefused(unanchored + "named arguments [X] do not match any signature for function PV",
            "CALL pv(x => ('a','b'))");
        assertRefused(unanchored + "named arguments [A, B] do not match any signature for function PAB",
            "CALL pab(b => (1,2), a => 'x')");
    }

    @Test
    public void testTableFunctions() {
        final String constant = "SQL compilation error:\nargument ";
        assertRefused(constant + "1 to function GENERATOR needs to be constant, found 'ROW(1, 2)'",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => (1,2)))");
        assertRefused(constant + "2 to function GENERATOR needs to be constant, found 'ROW(2, 'ab', null, TRUE)'",
            "SELECT SEQ4() FROM TABLE(GENERATOR(TIMELIMIT => 1, ROWCOUNT => (1 + 1, 'a' || 'b', NULL, TRUE)))");
        assertRefused(constant + "1 to function GENERATOR needs to be constant, found 'ROW(1.5, 100, -1, 'it''s')'",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => (1.50, 1e2, -(1), 'it''s')))");
        assertRefused(constant + "1 to function GENERATOR needs to be constant, found 'ROW(PARSE_JSON('1'), 2)'",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => (PARSE_JSON('1'), 2)))");
        assertRefused(constant + "0 to function -1 needs to be constant, found 'c'",
            "SELECT * FROM TABLE(TO_QUERY(SQL => 'SELECT 1 AS a', b => 'x', c => ('a','b')))");
        assertRefused(constant + "0 to function -1 needs to be constant, found 'SQL'",
            "SELECT * FROM TABLE(TO_QUERY(SQL => ('a','b')))");
        assertRefused("SQL compilation error: error line 1 at position 44\nInvalid result query ID, found '('",
            "SELECT * FROM TABLE(RESULT_SCAN(QUERY_ID => ('a','b')))");
        final String invalid = "SQL compilation error:\ninvalid type [";
        assertRefused(invalid + "ROW(BOOLEAN, BOOLEAN)] for parameter 'OUTER'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), OUTER => (TRUE, FALSE)))");
        assertRefused(invalid + "ROW(VARCHAR(1), VARCHAR(1))] for parameter 'PATH'",
            "SELECT * FROM TABLE(FLATTEN(PARSE_JSON('[1]'), PATH => ('a','b')))");
        assertRefused(invalid + "ROW(VARCHAR(1), VARCHAR(1))] for parameter 'INPUT'",
            "SELECT VALUE FROM LATERAL FLATTEN(INPUT => ('a','b'))");
        assertRefused(invalid + "ROW(NUMBER(1,0), NUMBER(1,0))] for parameter 'RESULT_LIMIT'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY(RESULT_LIMIT => (1,2)))");
    }

    private void assertRefused(final String expected, final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertEquals(expected, refused.getMessage(), sql);
    }
}
