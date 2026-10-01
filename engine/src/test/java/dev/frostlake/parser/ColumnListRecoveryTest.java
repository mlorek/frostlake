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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The lines live reports for a fault in a CREATE TABLE's column list (live-verified): a column with no type is
 * refused where its type belongs and the list reads on; any other column that is no name or no type is refused where
 * it stands, and the first ')' at or after it ends the list. The statement's next fault after the list is one more
 * line. Frostlake split such a statement at a bracket and named a token inside it instead.
 */
public class ColumnListRecoveryTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void aColumnListFaultThenTheStatementsNextFault() {
        assertEquals(refused(line(1, 28, "'n'"), line(1, 40, "(")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x') (a INT)"));
        assertEquals(refused(line(1, 29, ")"), line(1, 31, "(")),
            refusal("CREATE OR REPLACE TABLE tq (x) (a INT)"));
        assertEquals(refused(line(1, 33, "("), line(1, 38, ")")),
            refusal("CREATE OR REPLACE TABLE tq (UPPER('n')) (a INT)"));
        assertEquals(refused(line(1, 29, ")")),
            refusal("CREATE OR REPLACE TABLE tq (x)"));
        assertEquals(refused(line(1, 29, ")")),
            refusal("CREATE OR REPLACE TABLE tq (x);"));
        assertEquals(refused(line(1, 35, "'b'")),
            refusal("CREATE OR REPLACE TABLE tq (a INT, 'b' INT)"));
        assertEquals(refused(line(1, 35, "'b'")),
            refusal("CREATE OR REPLACE TABLE tq (a INT, 'b' INT) COMMENT = 'x'"));
        assertEquals(refused(line(1, 36, ")"), line(1, 40, "y")),
            refusal("CREATE OR REPLACE TABLE tq (a INT, b) x y"));
        assertEquals(refused(line(1, 29, ")"), line(1, 33, "y")),
            refusal("CREATE OR REPLACE TABLE tq (x) x y"));
        assertEquals(refused(line(1, 28, "'n'"), line(1, 33, "AS")),
            refusal("CREATE OR REPLACE TABLE tq ('n') AS SELECT 1"));
        assertEquals(refused(line(1, 38, ")"), line(1, 40, "(")),
            refusal("CREATE OR REPLACE TABLE IDENTIFIER(\"x\") (a INT)"));
        assertEquals(refused(line(1, 35, "'x'"), line(1, 47, "(")),
            refusal("CREATE OR REPLACE TABLE IDENTIFIER('x' || 'y') (a INT)"));
        assertEquals(refused(line(1, 29, ","), line(1, 32, ")"), line(1, 34, "(")),
            refusal("CREATE OR REPLACE TABLE tq (x, y) (a INT)"));
        assertEquals(refused(line(1, 36, ")"), line(1, 38, "(")),
            refusal("CREATE OR REPLACE TABLE tq (x INT, y) (a INT)"));
        assertEquals(refused(line(1, 28, "1"), line(1, 35, "(")),
            refusal("CREATE OR REPLACE TABLE tq (1 INT) (a INT)"));
        assertEquals(refused(line(1, 30, "1"), line(1, 33, "(")),
            refusal("CREATE OR REPLACE TABLE tq (a 1) (b INT)"));
        assertEquals(refused(line(1, 28, "'n'"), line(1, 42, "y")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x') x y"));
        assertEquals(refused(line(1, 28, "'n'"), line(1, 40, "AS")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x') AS SELECT 1"));
        assertEquals(refused(line(1, 28, "'n'")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x');"));
        assertEquals(refused(line(1, 28, "'n'")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x') COMMENT = 'x'"));
        assertEquals(refused(line(1, 28, "'n'"), line(1, 40, ")")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x') ) x"));
        assertEquals(refused(line(1, 28, "'n'"), line(1, 39, ",")),
            refusal("CREATE OR REPLACE TABLE tq ('n' || 'x'), b INT)"));
        assertEquals(refused(line(1, 35, "'b'"), line(1, 46, "y")),
            refusal("CREATE OR REPLACE TABLE tq (a INT, 'b' INT) x y"));
        assertEquals(refused(line(1, 35, "'b'"), line(1, 44, "(")),
            refusal("CREATE OR REPLACE TABLE tq (a INT, 'b' INT) (c INT)"));
        assertEquals(refused(line(1, 31, "'n'"), line(1, 43, "(")),
            refusal("CREATE TABLE IF NOT EXISTS tq ('n' || 'x') (a INT)"));
        assertEquals(refused(line(1, 27, "'n'"), line(1, 39, "(")),
            refusal("CREATE TEMPORARY TABLE tq ('n' || 'x') (a INT)"));
        assertEquals(refused(line(1, 29, ")"), line(2, 0, "(")),
            refusal("CREATE OR REPLACE TABLE tq (x)\n(a INT)"));
        assertEquals(refused(line(2, 0, "'n'"), line(3, 2, "(")),
            refusal("CREATE OR REPLACE TABLE tq (\n'n' || 'x'\n) (a INT)"));
    }

    @Test
    public void listsThatAlreadyAgree() {
        assertEquals(refused(line(1, 43, "'x'")),
            refusal("CREATE OR REPLACE TABLE tq (a INT NOT NULL 'x')"));
        assertEquals(refused(line(1, 16, "'t'"), line(1, 28, "VALUES")),
            refusal("INSERT INTO t1 ('t' || '1') VALUES (1)"));
        assertEquals(refused(line(1, 18, ")")),
            refusal("INSERT INTO t1 (a)) VALUES (1)"));
        assertEquals(refused(line(1, 16, "'a'"), line(1, 21, "x")),
            refusal("INSERT INTO t1 ('a') x y"));
        assertEquals(refused(line(1, 26, "'x'")),
            refusal("CREATE OR REPLACE VIEW v ('x') AS SELECT 1"));
        assertEquals(refused(line(1, 35, "(")),
            refusal("CREATE OR REPLACE TABLE tq (a INT) (b INT)"));
        assertEquals(refused(line(1, 35, "'x'")),
            refusal("CREATE OR REPLACE TABLE tq (a INT) 'x'"));
        assertEquals(refused(line(1, 43, "'x'")),
            refusal("CREATE OR REPLACE TABLE tq (a INT) COMMENT 'x'"));
        assertEquals(refused(line(1, 35, ")")),
            refusal("CREATE OR REPLACE TABLE tq (a INT) )"));
        assertEquals(refused(line(1, 35, ",")),
            refusal("CREATE OR REPLACE TABLE tq (a INT) , b"));
    }
}
