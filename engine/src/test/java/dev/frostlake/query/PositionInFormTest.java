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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * POSITION's IN form, {@code POSITION(<needle> IN <haystack>)}, where the call's first argument parses
 * as a membership test. The test is taken apart into needle and haystack when each side is one value;
 * with more than one value on a side, that side is a ROW and the pair is refused as POSITION's
 * argument types. The form has no NOT IN and takes nothing after it: either is a syntax error, reported
 * at the word the parser did not expect and again at POSITION's closing parenthesis. Each expected answer
 * is the account's own.
 */
public class PositionInFormTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (g VARCHAR(10), n NUMBER(5,0), v VARIANT, b BOOLEAN)");
    }

    /** The answer as one line: the rows a query returns, or its refusal with each line break as |. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** NOT IN inside POSITION, over a subquery or a list, of a value or a tuple. */
    @Test
    public void aNotInIsASyntaxErrorAtNotAndAtTheClosingParenthesis() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 41 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 41 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN (SELECT 'abc')) AS p"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 41 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN (SELECT 'abc')) + 1"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'NOT'.|syntax error line 1 at position 44 unexpected ')'.",
            answer("SELECT 1, POSITION('b' NOT IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 22 unexpected 'NOT'.|syntax error line 1 at position 43 unexpected ')'.",
            answer("SELECT POSITION(('b') NOT IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 49 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN (SELECT 'abc' FROM rt))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 41 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN (SELECT 'abc')) FROM rt"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 22 unexpected 'NOT'.|syntax error line 1 at position 48 unexpected ')'.",
            answer("SELECT POSITION( 'b'  NOT  IN  ( SELECT 'abc' ) )"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 34 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN ('abc'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 39 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN ('abc', 'x'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 27 unexpected 'NOT'.|syntax error line 1 at position 53 unexpected ')'.",
            answer("SELECT POSITION(('b', 'c') NOT IN (SELECT 'abc', 'x'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 27 unexpected 'NOT'.|syntax error line 1 at position 46 unexpected ')'.",
            answer("SELECT POSITION(('b', 'c') NOT IN ('abc', 'x'))"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 44 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN (SELECT 'abc'), 1)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'NOT'.|syntax error line 1 at position 37 unexpected ')'.",
            answer("SELECT POSITION('b' NOT IN ('abc'), 1)"));
    }

    /** Anything after the IN form is refused at the comma that introduces it. */
    @Test
    public void anArgumentAfterTheInFormIsASyntaxErrorAtTheComma() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 37 unexpected ','.|syntax error line 1 at position 40 unexpected ')'.",
            answer("SELECT POSITION('b' IN (SELECT 'abc'), 1)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 30 unexpected ','.|syntax error line 1 at position 33 unexpected ')'.",
            answer("SELECT POSITION('b' IN ('abc'), 1)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 32 unexpected ','.|syntax error line 1 at position 35 unexpected ')'.",
            answer("SELECT POSITION(('b') IN ('abc'), 1)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 35 unexpected ','.|syntax error line 1 at position 38 unexpected ')'.",
            answer("SELECT POSITION('b' IN ('abc', 'x'), 1)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 31 unexpected ','.|syntax error line 1 at position 37 unexpected ')'.",
            answer("SELECT POSITION(1 IN (SELECT 1), 2, 3)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 37 unexpected ','.|syntax error line 1 at position 43 unexpected ')'.",
            answer("SELECT POSITION('b' IN (SELECT 'abc'), 1, 2)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 49 unexpected ','.|syntax error line 1 at position 52 unexpected ')'.",
            answer("SELECT POSITION(('b', 'c') IN (SELECT 'abc', 'x'), 1)"));
    }

    /** A tuple needle, a multi-column subquery or a list of several values is a ROW. */
    @Test
    public void aSideOfSeveralValuesIsARowAndThePairIsRefused() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION(('b', 'c') IN (SELECT 'abc', 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1), VARCHAR(1)))",
            answer("SELECT POSITION(('b', 'c', 'd') IN (SELECT 'abc', 'x', 'y'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), NUMBER(1,0)), ROW(VARCHAR(3), NUMBER(1,0)))",
            answer("SELECT POSITION(('b', 1) IN (SELECT 'abc', 2))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            answer("SELECT POSITION((1, 2) IN (SELECT 1, 2))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), NULL), ROW(VARCHAR(3), NULL))",
            answer("SELECT POSITION(('b', NULL) IN (SELECT 'abc', NULL))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(10), VARCHAR(10)))",
            answer("SELECT POSITION(('b', 'c') IN (SELECT g, g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION(('b', 'c') IN (SELECT 'abc', 'x' FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(10)), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION(('b', g) IN (SELECT 'abc', 'x')) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 10|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT g, POSITION(('b', 'c') IN (SELECT 'abc', 'x')) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(3))",
            answer("SELECT POSITION(('b', 'c') IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION(('b') IN (SELECT 'abc', 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION(('b', 'c') IN ('abc', 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION(('b', 'c') IN (('abc', 'x')))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(3))",
            answer("SELECT POSITION(('b', 'c') IN ('abc'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1)))",
            answer("SELECT POSITION('b' IN ('abc', 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1), VARCHAR(1)))",
            answer("SELECT POSITION('b' IN ('abc', 'x', 'y'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'POSITION': (VARCHAR(1), ROW(VARCHAR(3), VARCHAR(10)))",
            answer("SELECT POSITION('b' IN ('abc', g)) FROM rt"));
    }

    /** One value each side answers as the two-argument call would. */
    @Test
    public void aOneValueFormIsTakenApartIntoNeedleAndHaystack() {
        assertEquals("ACCEPTED: 2",
            answer("SELECT POSITION('b' IN ('abc'))"));
        assertEquals("ACCEPTED: 2",
            answer("SELECT POSITION(('b') IN ('abc'))"));
        assertEquals("ACCEPTED: 2",
            answer("SELECT POSITION('b' IN (('abc')))"));
        assertEquals("ACCEPTED:",
            answer("SELECT POSITION(g IN ('abc')) FROM rt"));
    }
}
