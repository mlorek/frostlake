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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IDENTIFIER() takes a whole object reference and nothing else: a string, a variable, a bind variable or an
 * integer, then its closing parenthesis. Any other argument is a syntax error inside the parentheses, at the
 * first token a whole reference cannot take, in a FROM clause, a select list, a function name, DROP and
 * DESCRIBE alike; a comma after that token adds a line for the first closing parenthesis after the comma,
 * and nothing more of the statement is reported. A statement that creates, fills, changes or uses its object
 * reads the word IDENTIFIER with no whole reference after it as the object's own name instead, so it refuses
 * what follows the word. Every cell is live-verified.
 */
public class IdentifierArgumentSyntaxTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        engine.execute("SET tn = 't1'");
    }

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The refusal's heading and its first line only. */
    private String firstLine(final String sql) {
        final String refusal = answer(sql);
        final int second = refusal.indexOf('|', refusal.indexOf('|') + 1);
        return second < 0 ? refusal : refusal.substring(0, second);
    }

    private static String unexpected(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String refusal(final String... lines) {
        return "SQL compilation error:|" + String.join("|", lines);
    }

    @Test
    public void aWholeReferenceNamesItsObject() {
        final String[] counted = {
            "SELECT COUNT(*) FROM IDENTIFIER('t1')", "SELECT COUNT(*) FROM IDENTIFIER($$t1$$)",
            "SELECT COUNT(*) FROM IDENTIFIER($tn)", "SELECT COUNT(*) FROM IDENTIFIER( 't1' )",
            "SELECT COUNT(*) FROM IDENTIFIER ('t1')", "SELECT COUNT(*) FROM IDENTIFIER /*c*/ ('t1')",
            "SELECT COUNT(*) FROM IDENTIFIER('t1') AS identifier",
            "EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT 't1'; c INT; BEGIN SELECT COUNT(*) INTO :c"
                + " FROM IDENTIFIER(:v); RETURN c; END; $$",
        };
        for (final String sql : counted) {
            assertEquals("1", answer(sql), sql);
        }
        assertEquals("A", answer("SELECT IDENTIFIER('upper')('a')"));
        assertEquals("SQL compilation error: error line 1 at position 32|invalid identifier '1'",
            answer("SELECT COUNT(*) FROM IDENTIFIER(1)"));
    }

    @Test
    public void anyOtherArgumentIsRefusedInsideTheParentheses() {
        final Object[][] cells = {
            {"SELECT COUNT(*) FROM IDENTIFIER('t' || '1')", 36, "||"},
            {"SELECT COUNT(*) FROM IDENTIFIER(\"T1\")", 32, "\"T1\""},
            {"SELECT COUNT(*) FROM IDENTIFIER(t1)", 32, "t1"},
            {"SELECT COUNT(*) FROM IDENTIFIER(NULL)", 32, "NULL"},
            {"SELECT COUNT(*) FROM IDENTIFIER($1)", 32, "$1"},
            {"SELECT COUNT(*) FROM IDENTIFIER(-1)", 32, "-"},
            {"SELECT COUNT(*) FROM IDENTIFIER(TRUE)", 32, "TRUE"},
            {"SELECT COUNT(*) FROM IDENTIFIER(UPPER('t1'))", 32, "UPPER"},
            {"SELECT COUNT(*) FROM IDENTIFIER(('t1'))", 32, "("},
            {"SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t'))", 32, "CONCAT"},
            {"SELECT COUNT(*) FROM IDENTIFIER()", 32, ")"},
            {"SELECT COUNT(*) FROM IDENTIFIER('t1'", 36, "<EOF>"},
            {"SELECT COUNT(*) FROM IDENTIFIER($$t$$ || '1')", 38, "||"},
            {"SELECT COUNT(*) FROM IDENTIFIER(1.5)", 32, "1.5"},
            {"SELECT COUNT(*) FROM IDENTIFIER(X'00')", 32, "X'00'"},
            {"SELECT IDENTIFIER(UPPER('a')) FROM t1", 18, "UPPER"},
            {"SELECT IDENTIFIER(a) FROM t1", 18, "a"},
            {"SELECT IDENTIFIER('a' || '') FROM t1", 22, "||"},
            {"SELECT IDENTIFIER($1) FROM t1", 18, "$1"},
            {"SELECT IDENTIFIER() FROM t1", 18, ")"},
            {"SELECT IDENTIFIER(*) FROM t1", 18, "*"},
            {"SELECT IDENTIFIER(DISTINCT 'a') FROM t1", 18, "DISTINCT"},
            {"SELECT IDENTIFIER($tn || '') FROM t1", 22, "||"},
            {"SELECT IDENTIFIER('up' || 'per')('a')", 23, "||"},
            {"SELECT IDENTIFIER(UPPER('upper'))('a')", 18, "UPPER"},
            {"DROP TABLE IDENTIFIER('n' || 'y')", 26, "||"},
            {"DESCRIBE TABLE IDENTIFIER('t1' || '')", 31, "||"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT 't1'; c INT; BEGIN SELECT COUNT(*) INTO :c"
                + " FROM IDENTIFIER(v); RETURN c; END; $$", 86, "v"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT 't1'; c INT; BEGIN SELECT COUNT(*) INTO :c"
                + " FROM IDENTIFIER(:v || ''); RETURN c; END; $$", 89, "||"},
        };
        for (final Object[] cell : cells) {
            assertEquals(refusal(unexpected((Integer) cell[1], (String) cell[2])), answer((String) cell[0]),
                (String) cell[0]);
        }
    }

    @Test
    public void aCommaAddsTheParenthesisAfterItAndEndsTheReport() {
        assertEquals(refusal(unexpected(32, "CONCAT"), unexpected(47, ")")),
            answer("SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1'))"));
        assertEquals(refusal(unexpected(32, "CONCAT"), unexpected(52, ")")),
            answer("SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1', '2'))"));
        assertEquals(refusal(unexpected(36, ","), unexpected(41, ")")),
            answer("SELECT COUNT(*) FROM IDENTIFIER('t1', 'x')"));
        assertEquals(refusal(unexpected(32, "CONCAT"), unexpected(47, ")")),
            answer("SELECT COUNT(*) FROM IDENTIFIER(CONCAT('t', '1')) x, t1 y"));
        assertEquals(refusal(unexpected(18, "CONCAT"), unexpected(33, ")")),
            answer("SELECT IDENTIFIER(CONCAT('a', 'b')) FROM t1"));
        assertEquals(refusal(unexpected(21, ","), unexpected(26, ")")), answer("SELECT IDENTIFIER('a', 'b') FROM t1"));
        assertEquals(refusal(unexpected(18, "CONCAT"), unexpected(30, ")")),
            answer("SELECT IDENTIFIER(CONCAT('a', )) FROM t1"));
        assertEquals(refusal(unexpected(18, "("), unexpected(27, ")")), answer("SELECT IDENTIFIER(('a', 'b')) FROM t1"));
        assertEquals(refusal(unexpected(18, "("), unexpected(28, ")")), answer("SELECT IDENTIFIER(('a'), 'b') FROM t1"));
        assertEquals(refusal(unexpected(22, "'b'"), unexpected(30, ")")),
            answer("SELECT IDENTIFIER('a' 'b', 'c') FROM t1"));
        assertEquals(refusal(unexpected(18, "CONCAT"), unexpected(33, ")")),
            answer("SELECT IDENTIFIER(CONCAT('a', 'b')) FROM t1 WHERE a = 1 AND"));
    }

    @Test
    public void aFaultAfterTheParenthesesIsStillReported() {
        assertEquals(refusal(unexpected(18, "UPPER"), unexpected(53, "<EOF>")),
            answer("SELECT IDENTIFIER(UPPER('a')) FROM t1 WHERE a = 1 AND"));
        assertEquals(refusal(unexpected(18, "("), unexpected(48, "<EOF>")),
            answer("SELECT IDENTIFIER(('a')) FROM t1 WHERE a = 1 AND"));
        assertEquals(refusal(unexpected(32, "UPPER"), unexpected(60, "<EOF>")),
            answer("SELECT COUNT(*) FROM IDENTIFIER(UPPER('t1')) WHERE a = 1 AND"));
    }

    @Test
    public void whereTheObjectIsCreatedFilledOrChangedTheWordIsItsName() {
        engine.execute("CREATE OR REPLACE TABLE identifier (a INT)");
        engine.execute("INSERT INTO identifier (a) VALUES (1)");
        assertEquals("1", answer("SELECT COUNT(*) FROM identifier"));
        assertEquals("1", answer("SELECT identifier FROM (SELECT 1 AS identifier)"));
        assertEquals("1", answer("SELECT COUNT(*) FROM t1 identifier"));
        assertEquals(refusal(unexpected(17, "(")), answer("UPDATE IDENTIFIER('t' || '1') SET a = 1"));
        assertEquals(refusal(unexpected(35, "'n'")), firstLine("CREATE OR REPLACE TABLE IDENTIFIER('n' || 'x') (a INT)"));
        assertEquals(refusal(unexpected(35, "$tn")), firstLine("CREATE OR REPLACE TABLE IDENTIFIER($tn || '') (a INT)"));
        assertEquals(refusal(unexpected(40, "(")), firstLine("CREATE OR REPLACE TABLE IDENTIFIER(UPPER('n')) (a INT)"));
        assertEquals(refusal(unexpected(35, "'x'")),
            firstLine("CREATE OR REPLACE TABLE IDENTIFIER('x' || 'y') AS SELECT 1 AS a"));
        assertEquals(refusal(unexpected(23, "'t'")), firstLine("INSERT INTO IDENTIFIER('t' || '1') VALUES (1)"));
        assertEquals("SQL compilation error: error line 1 at position 35|invalid identifier '1'",
            answer("CREATE OR REPLACE TABLE IDENTIFIER(1) (a INT)"));
    }
}
