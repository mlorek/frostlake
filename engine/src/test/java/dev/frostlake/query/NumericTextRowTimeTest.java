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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the NUMERIC row-time family — the five arithmetic operators, unary minus, the math functions
 * and the BIT functions — does with a TEXT operand. Live converts a numeric text in ANY position and
 * refuses anything else PER ROW, with one sentence and no compilation prefix (live-verified):
 *
 * <pre>
 *   '5' + 1   BITAND('5', 1)   ABS(' 5 ')   SQRT('5')   MOD('5', '2')    convert — trimmed, in any
 *                                                                          spelling a literal has
 *   g + 1     BITAND(g, 1)     ABS(g)       -g          SUM(g)           Numeric value 'x' is not recognized
 *   'x' + 'y'                                                            names 'x' — the LEFT operand
 *   ABS(' x ')                                                           names x — the echo is TRIMMED
 *   '' + 1     '0x10' + 1                                                echoed as written
 * </pre>
 *
 * <p>★ IT IS A ROW-TIME RULE: over an empty table every cell ANSWERS, and the sentence carries no
 * prefix. Every member of the family converts — ABS, CEIL, FLOOR, ROUND, TRUNC, SIGN, MOD, SQRT,
 * POWER, EXP, LN, LOG, CBRT, FACTORIAL, SQUARE, DEGREES and the six BIT functions were each measured to
 * take '5' where they take 5 — so the engine reads the text ONCE, at the dispatch, and no member words
 * a refusal of its own.
 *
 * <p>★ THE BIT FAMILY ROUNDS A FRACTION HALF AWAY FROM ZERO before it works, for a NUMBER, a FLOAT
 * and a text alike: BITAND(2.5, 1) is 1, BITAND(2.4, 1) is 0, BITAND(-2.5, 1) is 1, BITNOT(2.5) is
 * -4, BITSHIFTLEFT('2.5', 1) is 6.
 *
 * <p>NOT COVERED HERE: the WIDTHS live declares for text arithmetic (NUMBER(19,5) for '5' + 1, the
 * BIT family's own widths, MOD and DIV0 over text) and ROUND's refusal of an unknown rounding-mode
 * text — each recorded on its own. Values are pinned through TO_VARCHAR or a cast so the two sides
 * compare texts, not carriers.
 */
public class NumericTextRowTimeTest extends BaseDatabaseTest {

    private static final String NOT_A_NUMBER = "Numeric value 'x' is not recognized";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (g VARCHAR(10), n NUMBER(5,0))");
        engine.execute("INSERT INTO rt VALUES ('x', 5)");
        engine.execute("CREATE OR REPLACE TABLE rnum (g VARCHAR(10))");
        engine.execute("INSERT INTO rnum VALUES ('5')");
        engine.execute("CREATE OR REPLACE TABLE rdec (g VARCHAR(10))");
        engine.execute("INSERT INTO rdec VALUES ('2.50')");
        engine.execute("CREATE OR REPLACE TABLE rempty (g VARCHAR(10))");
    }

    /** Every row's first column, or the refusal with its lines joined by '|'. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED");
            while (rs.next()) {
                all.append(' ').append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    void anUnrecognisedTextRefusesPerRowWithOneSentence() {
        assertEquals(NOT_A_NUMBER, outcome("SELECT g + 1 FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT g - 1 FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT g * 2 FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT g / 2 FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT g % 2 FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT -g FROM rt"));
        // Either position, a literal too, and the LEFT operand named when both are unreadable.
        assertEquals(NOT_A_NUMBER, outcome("SELECT 'x' + 1"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT 1 + 'x'"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT n + g FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT 'x' + 'y'"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT -'x'"));
        // The echo is the text as written — an empty one and a hex-looking one included.
        assertEquals("Numeric value '' is not recognized", outcome("SELECT '' + 1"));
        assertEquals("Numeric value '0x10' is not recognized", outcome("SELECT '0x10' + 1"));
    }

    @Test
    void theMathFamilyRefusesTheSameWay() {
        assertEquals(NOT_A_NUMBER, outcome("SELECT ABS(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT CEIL(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT FLOOR(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT ROUND(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT TRUNC(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT SIGN(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT MOD(g, 2) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT MOD(5, g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT SQRT(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT POWER(g, 2) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT POWER(2, g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT EXP(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT LN(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT ABS('x')"));
        // The echo is TRIMMED, so the surrounding blanks are not part of what is named.
        assertEquals(NOT_A_NUMBER, outcome("SELECT ABS(' x ')"));
        assertEquals("Numeric value '' is not recognized", outcome("SELECT ABS('')"));
        // The aggregates read text the same way.
        assertEquals(NOT_A_NUMBER, outcome("SELECT SUM(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT AVG(g) FROM rt"));
    }

    @Test
    void theBitFamilyRefusesAtRowTimeToo() {
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITAND(g, 1) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITOR(g, 1) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITXOR(g, 1) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITNOT(g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITSHIFTLEFT(g, 1) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITSHIFTRIGHT(g, 1) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITAND(1, g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITAND(n, g) FROM rt"));
        assertEquals(NOT_A_NUMBER, outcome("SELECT BITAND('x', 1)"));
    }

    @Test
    void aNumericTextConvertsInEveryPosition() {
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND('5', 1)"));
        assertEquals("ACCEPTED 5", outcome("SELECT BITOR('5', 1)"));
        assertEquals("ACCEPTED 4", outcome("SELECT BITXOR('5', 1)"));
        assertEquals("ACCEPTED -6", outcome("SELECT BITNOT('5')"));
        assertEquals("ACCEPTED 10", outcome("SELECT BITSHIFTLEFT('5', 1)"));
        assertEquals("ACCEPTED 2", outcome("SELECT BITSHIFTRIGHT('5', 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND(g, 1) FROM rnum"));
        assertEquals("ACCEPTED 0", outcome("SELECT BITAND('1e2', 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND(' 5 ', 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT MOD('5', '2')::NUMBER(3,0)"));
        assertEquals("ACCEPTED 2.236067977", outcome("SELECT TO_VARCHAR(SQRT('5'))"));
        assertEquals("ACCEPTED 25", outcome("SELECT TO_VARCHAR(POWER('5', 2))"));
        assertEquals("ACCEPTED 5", outcome("SELECT TO_VARCHAR(ABS(g)) FROM rnum"));
        assertEquals("ACCEPTED 2.5", outcome("SELECT TO_VARCHAR(ABS(g)) FROM rdec"));
        assertEquals("ACCEPTED 5", outcome("SELECT TO_VARCHAR(ABS(' 5 '))"));
        assertEquals("ACCEPTED 100", outcome("SELECT TO_VARCHAR(ABS('1e2'))"));
        assertEquals("ACCEPTED 3", outcome("SELECT TO_VARCHAR(ROUND('2.5'))"));
        assertEquals("ACCEPTED 2", outcome("SELECT TO_VARCHAR(FLOOR('2.5'))"));
        assertEquals("ACCEPTED -1", outcome("SELECT TO_VARCHAR(SIGN('-5'))"));
        assertEquals("ACCEPTED 2", outcome("SELECT TO_VARCHAR(LOG(10, '100'))"));
        assertEquals("ACCEPTED 2", outcome("SELECT TO_VARCHAR(CBRT('8'))"));
        assertEquals("ACCEPTED 6", outcome("SELECT TO_VARCHAR(FACTORIAL('3'))"));
        assertEquals("ACCEPTED 9", outcome("SELECT TO_VARCHAR(SQUARE('3'))"));
        assertEquals("ACCEPTED 57.295779513", outcome("SELECT TO_VARCHAR(DEGREES('1'))"));
        // The text spellings a number literal has all read; the value is pinned through a cast.
        assertEquals("ACCEPTED 6", outcome("SELECT ('5' + 1)::NUMBER(3,0)"));
        assertEquals("ACCEPTED 6", outcome("SELECT (' 5 ' + 1)::NUMBER(3,0)"));
        assertEquals("ACCEPTED 101", outcome("SELECT ('1e2' + 1)::NUMBER(4,0)"));
        assertEquals("ACCEPTED 6", outcome("SELECT ('+5' + 1)::NUMBER(3,0)"));
        assertEquals("ACCEPTED 6", outcome("SELECT ('5.' + 1)::NUMBER(3,0)"));
        assertEquals("ACCEPTED 1.5", outcome("SELECT ('.5' + 1)::NUMBER(3,1)"));
        assertEquals("ACCEPTED -4", outcome("SELECT ('-5' + 1)::NUMBER(3,0)"));
        assertEquals("ACCEPTED 6", outcome("SELECT (g + 1)::NUMBER(3,0) FROM rnum"));
        assertEquals("ACCEPTED 3.5", outcome("SELECT (g + 1)::NUMBER(5,1) FROM rdec"));
        assertEquals("ACCEPTED 1", outcome("SELECT (g % 2)::NUMBER(3,0) FROM rnum"));
        assertEquals("ACCEPTED -5", outcome("SELECT TO_VARCHAR(-g) FROM rnum"));
        assertEquals("ACCEPTED -5", outcome("SELECT TO_VARCHAR(-' 5 ')"));
        // A text the function takes AS text is not read as a number: ROUND's mode still applies.
        assertEquals("ACCEPTED 2", outcome("SELECT TO_VARCHAR(ROUND(2.5, 0, 'HALF_TO_EVEN'))"));
    }

    @Test
    void theBitFamilyRoundsAFractionHalfAwayFromZero() {
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND(2.5, 1)"));
        assertEquals("ACCEPTED 0", outcome("SELECT BITAND(2.4, 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND(-2.5, 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND(2.5::FLOAT, 1)"));
        assertEquals("ACCEPTED -4", outcome("SELECT BITNOT(2.5)"));
        assertEquals("ACCEPTED 6", outcome("SELECT BITSHIFTLEFT(2.5, 1)"));
        assertEquals("ACCEPTED 6", outcome("SELECT BITSHIFTLEFT('2.5', 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND('2.5', 1)"));
        assertEquals("ACCEPTED 1", outcome("SELECT BITAND(g, 1) FROM rdec"));
    }

    @Test
    void anEmptyTableAnswersBecauseTheRuleIsPerRow() {
        assertEquals("ACCEPTED", outcome("SELECT g + 1 FROM rempty"));
        assertEquals("ACCEPTED", outcome("SELECT -g FROM rempty"));
        assertEquals("ACCEPTED", outcome("SELECT ABS(g) FROM rempty"));
        assertEquals("ACCEPTED", outcome("SELECT SQRT(g) FROM rempty"));
        assertEquals("ACCEPTED", outcome("SELECT BITAND(g, 1) FROM rempty"));
        // And a NULL text is NULL, never a refusal.
        assertEquals("ACCEPTED null", outcome("SELECT ABS(NULL::VARCHAR)"));
        assertEquals("ACCEPTED null", outcome("SELECT NULL::VARCHAR + 1"));
    }
}
