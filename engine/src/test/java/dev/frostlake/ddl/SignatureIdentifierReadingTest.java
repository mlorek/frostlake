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

package dev.frostlake.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * After DROP and DESCRIBE, live reads {@code IDENTIFIER(<word>…)} as an object named IDENTIFIER followed by its
 * signature, so a nested call is refused inside the signature — at the string a type's parameters cannot hold —
 * while an IDENTIFIER() whose content opens as a value is the reference, refused where its reading stops. A
 * signature's parameters are numbers and words, nested; TRUE and FALSE are no words there; and a '(' opening the
 * signature's first item is refused right there, a DESCRIBE naming the ')' after its word too. Every cell is
 * live-verified.
 */
public class SignatureIdentifierReadingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
        engine.execute("CREATE VIEW v1 AS SELECT x FROM t1");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String lines(final String... placed) {
        final StringBuilder out = new StringBuilder("SQL compilation error:");
        for (int i = 0; i < placed.length; i += 2) {
            out.append("|syntax error line 1 at position ").append(placed[i]).append(" unexpected '")
                .append(placed[i + 1]).append("'.");
        }
        return out.toString();
    }

    private String tables() {
        return answer("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'T1'");
    }

    @Test
    public void aWordOpensTheSignatureReading() {
        assertEquals(lines("28", "'t1'"), answer("DROP TABLE IDENTIFIER(UPPER('t1'))"));
        assertEquals(lines("32", "'t1'"), answer("DESCRIBE TABLE IDENTIFIER(UPPER('t1'))"));
        assertEquals(lines("29", "'t'", "34", "'1'"), answer("DROP TABLE IDENTIFIER(CONCAT('t', '1'))"));
        assertEquals(lines("33", "'t'", "38", "'1'"), answer("DESCRIBE TABLE IDENTIFIER(CONCAT('t', '1'))"));
        assertEquals(lines("28", "'t1'"), answer("DROP TABLE IDENTIFIER(UPPER('t1', 2))"));
        assertEquals(lines("28", "'t1'"), answer("DROP TABLE IDENTIFIER(UPPER('t1')) CASCADE"));
        assertEquals(lines("38", "'t1'"), answer("DROP TABLE IF EXISTS IDENTIFIER(UPPER('t1'))"));
        assertEquals(lines("32", "'t1'"), answer("DESCRIBE TABLE IDENTIFIER(UPPER('t1')) type = columns"));
        assertEquals(lines("31", "'t1'"), answer("DESCRIBE VIEW IDENTIFIER(UPPER('t1'))"));
        assertEquals(lines("28", "'t1'", "34", "||"), answer("DROP TABLE IDENTIFIER(UPPER('t1') || 'x')"));
        assertEquals(lines("29", "'t'", "40", "'1'"), answer("DROP TABLE IDENTIFIER(CONCAT('t', UPPER('1')))"));
        assertEquals(lines("28", ")"), answer("DROP TABLE IDENTIFIER(UPPER())"));
        assertEquals(lines("30", "INT"), answer("DROP TABLE IDENTIFIER(UPPER(x INT))"));
        assertEquals(lines("22", "-"), answer("DROP TABLE IDENTIFIER(-1)"));
        assertEquals(lines("24", "b"), answer("DROP TABLE IDENTIFIER(a b)"));
        final String noSuchTable = hinted("SQL compilation error:|Table 'TEST_DB.TEST_SCHEMA.IDENTIFIER' does not exist or "
            + "not authorized.");
        assertEquals(noSuchTable, answer("DROP TABLE IDENTIFIER(UPPER(t1))"));
        assertEquals(noSuchTable, answer("DROP TABLE IDENTIFIER(UPPER(1))"));
        assertEquals(noSuchTable, answer("DROP TABLE IDENTIFIER(NUMBER(10, 2))"));
        assertEquals(noSuchTable, answer("DROP TABLE IDENTIFIER(a, b)"));
        assertEquals(noSuchTable, answer("DROP TABLE IDENTIFIER()"));
        assertEquals("1", tables());
    }

    @Test
    public void aValueOpensTheReferenceReading() {
        assertEquals(lines("26", "||"), answer("DROP TABLE IDENTIFIER('t' || '1')"));
        assertEquals(lines("26", "||"), answer("DROP TABLE IDENTIFIER('t' || '1', 2)"));
        assertEquals(lines("30", "||"), answer("DESCRIBE TABLE IDENTIFIER('t' || '1', 2)"));
        assertEquals(lines("24", "+"), answer("DROP TABLE IDENTIFIER(1 + 2)"));
        assertEquals(lines("25", "||"), answer("DROP TABLE IDENTIFIER($v || 'x')"));
        assertEquals(lines("25", "||"), answer("DROP TABLE IDENTIFIER(:b || 'x')"));
    }

    @Test
    public void aParenthesisOpeningTheSignatureIsRefusedInsideIt() {
        assertEquals(lines("19", "(", "21", ")"), answer("DESCRIBE TABLE t1 ((a))"));
        assertEquals(lines("19", "(", "21", ")"), answer("DESCRIBE TABLE t1 ((a)) type = columns"));
        assertEquals(lines("19", "(", "22", ")"), answer("DESCRIBE TABLE t1 (((a)))"));
        assertEquals(lines("18", "(", "20", ")"), answer("DESCRIBE VIEW v1 ((a))"));
        assertEquals(lines("24", "(", "26", ")"), answer("DESCRIBE SCHEMA PUBLIC ((a))"));
        assertEquals(lines("19", "("), answer("DESCRIBE TABLE t1 (())"));
        assertEquals(lines("19", "("), answer("DESCRIBE TABLE t1 ((1))"));
        assertEquals(lines("19", "("), answer("DESCRIBE TABLE t1 (('a'))"));
        assertEquals(lines("15", "("), answer("DROP TABLE t1 ((a))"));
        assertEquals(lines("19", "SELECT"), answer("DESCRIBE TABLE t1 (SELECT 1)"));
        assertEquals(lines("15", "SELECT"), answer("DROP TABLE t1 (SELECT 1)"));
        assertEquals("1", tables());
    }

    @Test
    public void aSignaturesParametersNestNumbersAndWords() {
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(1, 2, 3))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(b, c))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(b(1)))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(b(c)))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(b.c))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(\"b\"))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a(-1))"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a.b)"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (a.b.c)"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (VARCHAR())"));
        assertEquals("X", answer("DESCRIBE TABLE t1 (ARRAY(INT))"));
        assertEquals(lines("21", ")"), answer("DESCRIBE TABLE t1 (a())"));
        assertEquals(lines("22", "b"), answer("DESCRIBE TABLE t1 (a(-b))"));
        assertEquals(lines("21", "1.5"), answer("DESCRIBE TABLE t1 (a(1.5))"));
        assertEquals(lines("23", "2"), answer("DESCRIBE TABLE t1 (a(1 2))"));
        assertEquals(lines("22", "("), answer("DESCRIBE TABLE t1 (\"a\"(1))"));
    }

    @Test
    public void trueAndFalseAreNoWordsThere() {
        assertEquals(lines("15", "TRUE"), answer("DROP TABLE t1 (TRUE)"));
        assertEquals(lines("22", "TRUE"), answer("DROP TABLE IDENTIFIER(TRUE)"));
        assertEquals(lines("22", "FALSE"), answer("DROP TABLE IDENTIFIER(FALSE)"));
        assertEquals(lines("29", "FALSE"), answer("DROP TABLE IF EXISTS nosuch (FALSE)"));
        assertEquals(lines("19", "TRUE", "23", ")"), answer("DESCRIBE TABLE t1 (TRUE)"));
        assertEquals(lines("19", "FALSE", "24", ")"), answer("DESCRIBE TABLE t1 (FALSE)"));
        assertEquals(lines("22", "TRUE"), answer("DESCRIBE TABLE t1 (a, TRUE)"));
        assertEquals(lines("22", "FALSE"), answer("DESCRIBE TABLE t1 (a, FALSE)"));
        assertEquals(lines("21", "TRUE"), answer("DESCRIBE TABLE t1 (a(TRUE))"));
        assertEquals(lines("19", "NULL"), answer("DESCRIBE TABLE t1 (NULL)"));
        assertEquals("1", tables());
    }
}
