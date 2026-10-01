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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An IDENTIFIER() whose argument is a bind variable nothing binds is refused while the statement compiles,
 * over an empty table too, in a sentence of its own. An object is {@code Bind variable for object "tn" AS tn
 * not set} at the argument, the name spelled as an identifier and then as written or as the FROM alias; an
 * UPDATE's or a DELETE's target at line 0, position -1. A column drops the AS, and a function carries its
 * arguments re-printed, at the IDENTIFIER keyword. A positional {@code ?} is named by its place, a quoted bind
 * name is a syntax error, and a running block names an undeclared variable an invalid identifier. Every cell
 * is live-verified.
 */
public class IdentifierUnboundBindTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
    }

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String unset(final int position, final String object) {
        return "SQL compilation error: error line " + (position < 0 ? 0 : 1) + " at position " + position
            + "|Bind variable for object " + object + " not set";
    }

    @Test
    public void anObjectNamedByAnUnboundBindIsRefusedAtTheArgument() {
        final Object[][] cells = {
            {"SELECT COUNT(*) FROM IDENTIFIER(:tn)", 32, "\"tn\" AS tn"},
            {"SELECT COUNT(*) FROM IDENTIFIER(:tn) AS x", 32, "\"tn\" AS X"},
            {"SELECT COUNT(*) FROM IDENTIFIER(:tn) x", 32, "\"tn\" AS X"},
            {"SELECT COUNT(*) FROM t1 JOIN IDENTIFIER(:tn) ON TRUE", 40, "\"tn\" AS tn"},
            {"SELECT COUNT(*) FROM IDENTIFIER(: tn)", 32, "\"tn\" AS tn"},
            {"SELECT COUNT(*) FROM IDENTIFIER(:TN)", 32, "TN AS TN"},
            {"SELECT COUNT(*) FROM IDENTIFIER(:tn), IDENTIFIER(:tm)", 32, "\"tn\" AS tn"},
            {"SELECT COUNT(*) FROM IDENTIFIER(:tn) WHERE nosuch = 1", 32, "\"tn\" AS tn"},
            {"SELECT nosuch FROM IDENTIFIER(:tn)", 30, "\"tn\" AS tn"},
            {"SELECT COUNT(*) FROM t1, IDENTIFIER(?)", 36, "\"1\" AS 1"},
            {"SELECT COUNT(*) FROM IDENTIFIER(:1)", 32, "\"1\" AS 1"},
            {"DROP TABLE IDENTIFIER(:tn)", 22, "\"tn\" AS tn"},
            {"DROP TABLE IF EXISTS IDENTIFIER(:tn)", 32, "\"tn\" AS tn"},
            {"CREATE TABLE IDENTIFIER(:tn) (a INT)", 24, "\"tn\" AS tn"},
            {"INSERT INTO IDENTIFIER(:tn) VALUES (1)", 23, "\"tn\" AS tn"},
            {"USE SCHEMA IDENTIFIER(:tn)", 22, "\"tn\" AS tn"},
            {"DESCRIBE TABLE IDENTIFIER(:tn)", 26, "\"tn\" AS tn"},
            {"UPDATE IDENTIFIER(:tn) SET a = 1", -1, "\"tn\" AS tn"},
            {"DELETE FROM IDENTIFIER(:tn)", -1, "\"tn\" AS tn"},
        };
        for (final Object[] cell : cells) {
            assertEquals(unset((Integer) cell[1], (String) cell[2]), answer((String) cell[0]), (String) cell[0]);
        }
        assertEquals("SQL compilation error:|syntax error line 1 at position 33 unexpected '\"tn\"'.",
            answer("SELECT COUNT(*) FROM IDENTIFIER(:\"tn\")"));
    }

    @Test
    public void aColumnOrAFunctionNamedByAnUnboundBindIsRefusedToo() {
        final Object[][] cells = {
            {"SELECT IDENTIFIER(:v) FROM t1", 18, "\"v\""},
            {"SELECT 1 FROM t1 WHERE IDENTIFIER(:v) = 1", 34, "\"v\""},
            {"SELECT IDENTIFIER(:v)", 18, "\"v\""},
            {"SELECT IDENTIFIER(:v), IDENTIFIER(:w) FROM t1", 18, "\"v\""},
            {"SELECT UPPER(IDENTIFIER(:v)) FROM t1", 24, "\"v\""},
            {"SELECT IDENTIFIER(:fn)('a')", 7, "\"fn\"('a')"},
            {"SELECT IDENTIFIER(:fn)()", 7, "\"fn\"()"},
            {"SELECT IDENTIFIER(:fn)(1,   'b')", 7, "\"fn\"(1, 'b')"},
            {"SELECT IDENTIFIER(:fn)('a' ,'b')", 7, "\"fn\"('a', 'b')"},
            {"SELECT IDENTIFIER(:fn)(nosuch) FROM t1", 7, "\"fn\"(NOSUCH)"},
        };
        for (final Object[] cell : cells) {
            assertEquals(unset((Integer) cell[1], (String) cell[2]), answer((String) cell[0]), (String) cell[0]);
        }
    }

    @Test
    public void aRunningBlockNamesAnUndeclaredVariableAnInvalidIdentifier() {
        assertEquals("SQL compilation error: error line 1 at position 39|invalid identifier 'NOSUCH'",
            answer("EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM IDENTIFIER(:nosuch); END; $$"));
    }
}
