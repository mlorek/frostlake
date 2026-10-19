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
 * Two things the account reads and then refuses by name: a doubled '=', which is a call of a function named
 * "==", and a FOR UPDATE clause after a statement's query (live-verified).
 */
public class ForUpdateAndDoubledEqualsTest extends MeasuredCellsTestSupport {

    @Test
    public void aDoubledEqualsIsACall() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        answer(
            "INSERT INTO t VALUES (1, 'x', '2024-01-01')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"==\".",
            "SELECT 1 == 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '='.",
            "SELECT 1 = = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '='.",
            "SELECT 1 === 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"==\".",
            "SELECT a FROM t WHERE a == 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"==\".",
            "SELECT a == 1 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '='.",
            "SELECT 1 !== 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"==\".",
            "SELECT CASE WHEN 1 == 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"==\".",
            "SELECT 1 WHERE 1 == 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'.",
            "SELECT 1 == ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '=='.",
            "SELECT == 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown functions \"==\", \"==\".",
            "SELECT 1 == 1 == TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown functions \"==\", \"==\".",
            "SELECT 'a' == 'a', NULL == NULL");
    }

    @Test
    public void forUpdateIsReadAndRefused() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            answer(
                "INSERT INTO t VALUES (1, 'x', '2024-01-01')");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t FOR UPDATE NOWAIT");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t FOR UPDATE WAIT 5");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'SHARE'.",
                "SELECT 1 FROM t FOR SHARE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'OF'.",
                "SELECT 1 FROM t FOR UPDATE OF t");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t ORDER BY 1 FOR UPDATE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ORDER'.",
                "SELECT 1 FROM t FOR UPDATE ORDER BY 1");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t LIMIT 1 FOR UPDATE");
            assertAnswer(
                "REFUSED A 'FOR UPDATE' clause is not compatible with 'not selecting from a table'.",
                "SELECT 1 FOR UPDATE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
                "SELECT 1 FROM t FOR");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SELECT 1 FROM t FOR UPDATE x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'FOR'. | syntax "
                    + "error line 1 at position 27 unexpected ')'.",
                "(SELECT 1 FROM t FOR UPDATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'FOR'. | syntax "
                    + "error line 1 at position 41 unexpected ')'.",
                "SELECT * FROM (SELECT 1 FROM t FOR UPDATE)");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t WHERE a = 1 FOR UPDATE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SKIP'.",
                "SELECT 1 FROM t FOR UPDATE SKIP LOCKED");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'y'.",
                "SELECT 1 FROM t FOR UPDATE; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'READ'.",
                "SELECT 1 FROM t FOR READ ONLY");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'FOR'.",
                "SELECT 1 FROM t FOR UPDATE FOR UPDATE");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t UNION SELECT 1 FROM t FOR UPDATE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH.",
                "SELECT 1 FROM nosuch FOR UPDATE");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
                "SELECT nosuch FROM t FOR UPDATE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'LIMIT'.",
                "SELECT 1 FROM t FOR UPDATE LIMIT 1");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t GROUP BY 1 FOR UPDATE");
        } finally {
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void theWordsAfterForUpdate() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            answer(
                "INSERT INTO t VALUES (1, 'x', '2024-01-01')");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t FOR UPDATE");
            assertAnswer(
                "REFUSED Unsupported feature 'FOR UPDATE clause'.",
                "SELECT 1 FROM t ORDER BY 1 LIMIT 1 FOR UPDATE NOWAIT");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
                "SELECT 1 FROM t FOR UPDATE WAIT");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '5'.",
                "SELECT 1 FROM t FOR UPDATE NOWAIT 5");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
                "SELECT 1 FROM t FOR UPDATE WAIT x");
            assertAnswer(
                "REFUSED A 'FOR UPDATE' clause is not compatible with 'not selecting from a table'.",
                "WITH c AS (SELECT 1 q) SELECT q FROM c FOR UPDATE");
            assertAnswer(
                "1|2",
                "SELECT wait, nowait FROM (SELECT 1 AS wait, 2 AS nowait)");
        } finally {
            answer(
                "ROLLBACK");
        }
    }
}
