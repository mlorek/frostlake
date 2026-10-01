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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A LANGUAGE the account does not know is refused by name, the word echoed as written and "function" said for a
 * procedure too, before anything else about the routine is judged. The name is an unquoted word: a quoted name,
 * a string and a reserved word are syntax errors. Every cell is live-verified.
 */
public class UnknownFunctionLanguageTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String unknown(final String language) {
        return "SQL compilation error:\nUnknown function language: " + language + ".";
    }

    @Test
    public void anUnknownLanguageIsRefusedByNameAsWritten() {
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l1() RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE PROCEDURE l2() RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals(unknown("cobol"), refusalOf("CREATE FUNCTION l3() RETURNS INT language cobol AS 'x'"));
        assertEquals(unknown("R"), refusalOf("CREATE FUNCTION l4() RETURNS INT LANGUAGE R AS 'x'"));
        assertEquals(unknown("Cobol"), refusalOf("CREATE FUNCTION l19() RETURNS INT LANGUAGE Cobol STRICT IMMUTABLE "
            + "AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l12() RETURNS INT LANGUAGE COBOL"));
    }

    @Test
    public void itIsJudgedBeforeTheSignatureThePropertiesAndAnExistingRoutine() {
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l7(v VECTOR(INT,2)) RETURNS INT LANGUAGE COBOL "
            + "AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l8(a INT, a INT) RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE PROCEDURE l37(a INT, a INT) RETURNS INT LANGUAGE COBOL "
            + "AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l9() RETURNS INT LANGUAGE COBOL "
            + "RUNTIME_VERSION='3.11' AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l11() RETURNS INT LANGUAGE COBOL MEMOIZABLE AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l14() RETURNS INT LANGUAGE COBOL "
            + "IMPORTS=('@nostage/x.jar') AS 'x'"));
        engine.execute("CREATE OR REPLACE FUNCTION l15() RETURNS INT AS '1'");
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION IF NOT EXISTS l15() RETURNS INT LANGUAGE COBOL "
            + "AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION nodb.noschema.l25() RETURNS INT LANGUAGE COBOL "
            + "AS 'x'"));
        assertEquals(unknown("COBOL"), refusalOf("CREATE FUNCTION l35() RETURNS INT LANGUAGE COBOL "
            + "AS 'SELECT FROM WHERE'"));
    }

    @Test
    public void onlyAnUnquotedWordNamesALanguage() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 42 unexpected '\"cobol\"'.",
            refusalOf("CREATE FUNCTION l5() RETURNS INT LANGUAGE \"cobol\" AS 'x'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 42 unexpected ''COBOL''.",
            refusalOf("CREATE FUNCTION l6() RETURNS INT LANGUAGE 'COBOL' AS 'x'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 43 unexpected 'SELECT'.",
            refusalOf("CREATE FUNCTION l22() RETURNS INT LANGUAGE SELECT AS 'x'"));
    }

    @Test
    public void theOptionOrderIsJudgedFirst() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 58 unexpected 'SQL'.",
            refusalOf("CREATE FUNCTION l34() RETURNS INT LANGUAGE COBOL LANGUAGE SQL AS 'x'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 68 unexpected 'AS'.",
            refusalOf("CREATE FUNCTION l36() RETURNS INT LANGUAGE COBOL COMMENT='c' STRICT AS 'x'"));
    }
}
