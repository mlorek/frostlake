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
package dev.frostlake.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A text's statements, counted from its parse and, where it does not parse or is not parsed, from the lexer's
 * tokens — never from a second reading of its characters.
 */
public class StatementCountTest {

    @Test
    public void aTextThatParsesHoldsItsScriptsStatements() {
        assertEquals(1, StatementCount.countStatements("SELECT 1"));
        assertEquals(1, StatementCount.countStatements("SELECT 1;"));
        assertEquals(2, StatementCount.countStatements("SELECT 1; SELECT 2"));
        assertEquals(2, StatementCount.countStatements("SELECT 1 AS a$$b; DELETE FROM t"));
        assertEquals(2, StatementCount.countStatements("SELECT 1 -- x\r; DELETE FROM t"));
        assertEquals(2, StatementCount.countStatements("SELECT 1 AS a$$b /* $$ */; DELETE FROM t"));
        assertEquals(2, StatementCount.countStatements("SELECT 1 AS begin, 2 AS b; DELETE FROM t"));
        assertEquals(1, StatementCount.countStatements("SELECT 'a;b'"));
        assertEquals(1, StatementCount.countStatements("SELECT $$a;b$$"));
        assertEquals(1, StatementCount.countStatements("SELECT 1 AS \"a;b\""));
        assertEquals(1, StatementCount.countStatements("SELECT 1 -- c; DELETE FROM t"));
        assertEquals(1, StatementCount.countStatements("SELECT 1 ->> SELECT 2"));
        assertEquals(1, StatementCount.countStatements("BEGIN LET x := 1; RETURN x; END;"));
        assertEquals(2, StatementCount.countStatements("BEGIN LET x := 1; RETURN x; END; SELECT 2"));
        assertEquals(1, StatementCount.countStatements("DECLARE x INT DEFAULT 1; BEGIN RETURN x; END;"));
    }

    @Test
    public void aTextThatDoesNotParseIsCountedFromItsTokens() {
        assertEquals(2, StatementCount.countStatements("SELEC 1; SELECT 2"));
        assertEquals(2, StatementCount.countStatements("SELECT 1 AS a$$b; SELEC 2"));
        assertEquals(2, StatementCount.countStatements("SELECT (; SELECT 2"));
        assertEquals(0, StatementCount.countStatements(""));
        assertEquals(0, StatementCount.countStatements("-- only a comment"));
        assertEquals(0, StatementCount.countStatements(" ; ; "));
    }

    @Test
    public void tokensAreTheLexersOwn() {
        assertEquals(2, StatementCount.countTokens("SELECT 1 AS a$$b; DELETE FROM t"));
        assertEquals(2, StatementCount.countTokens("SELECT 1 -- x\r; DELETE FROM t"));
        assertEquals(2, StatementCount.countTokens("SELECT 1 // c\r; DELETE FROM t"));
        assertEquals(1, StatementCount.countTokens("SELECT 'a;b' -- c; DELETE FROM t"));
        assertEquals(1, StatementCount.countTokens("SELECT $$a;b$$;"));
        assertEquals(2, StatementCount.countTokens("SELECT 1 AS begin, 2 AS b; DELETE FROM t"));
        assertEquals(2, StatementCount.countTokens("SELECT begin FROM t; DELETE FROM t"));
        assertEquals(2, StatementCount.countTokens("SELECT t.begin + 1 FROM t; DELETE FROM t"));
        assertEquals(1, StatementCount.countTokens("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS BEGIN LET x := 1; "
            + "IF (x = 1) THEN RETURN CASE WHEN x = 1 THEN 1 ELSE 2 END; END IF; RETURN x; END;"));
        assertEquals(2, StatementCount.countTokens("BEGIN TRANSACTION; SELECT 1"));
        assertEquals(3, StatementCount.countTokens("BEGIN; SELECT 1; COMMIT"));
        assertEquals(1, StatementCount.countTokens("DECLARE c CURSOR FOR SELECT 1; BEGIN RETURN 1; END;"));
    }

    @Test
    public void theSessionsCountIsReadFromTheStatementThatAssignsIt() {
        assertEquals(Integer.valueOf(0), StatementCount.assignedCount("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0"));
        assertEquals(Integer.valueOf(3), StatementCount.assignedCount("alter session set multi_statement_count = 3;"));
        assertEquals(Integer.valueOf(2),
            StatementCount.assignedCount("ALTER /* c */ SESSION SET MULTI_STATEMENT_COUNT = 2 -- c\r"));
        assertEquals(Integer.valueOf(1), StatementCount.assignedCount("ALTER SESSION UNSET MULTI_STATEMENT_COUNT"));
        assertNull(StatementCount.assignedCount("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0, QUERY_TAG = 'x'"));
        assertNull(StatementCount.assignedCount("ALTER SESSION SET QUERY_TAG = 'MULTI_STATEMENT_COUNT = 5'"));
        assertNull(StatementCount.assignedCount("SELECT 1"));
    }
}
