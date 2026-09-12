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
 * What the LOGICAL OPERATORS — AND, OR and NOT — do with a STRING operand. Live converts it through
 * the TO_BOOLEAN text forms and refuses anything else at ROW time, naming the text with no
 * compilation prefix:
 *
 * <pre>
 *   'true' AND TRUE     true
 *   'x' AND TRUE        Boolean value 'x' is not recognized
 *   '5' AND TRUE        Boolean value '5' is not recognized
 *   NOT 'x'             Boolean value 'x' is not recognized
 * </pre>
 *
 * <p>★ DIGITS ARE NOT READ AS NUMBERS HERE. A NUMBER value in boolean position is zero/non-zero, but a
 * STRING of digits goes through the text forms, where only '1' and '0' are words — so '5' refuses.
 *
 * <p>★ IT IS A ROW-TIME REFUSAL: over an empty table every one of these ANSWERS, and the sentence
 * carries no prefix. The empty-table cells are what pins the phase.
 *
 * <p>★ THE PREDICATE POSITION IS A DIFFERENT RULE ENTIRELY. {@code WHERE g} over a VARCHAR is refused
 * at COMPILE time — "Invalid data type [VARCHAR(10)] for predicate [RT.G]", prefixed, empty table or
 * not — so the operators' strictness never reaches WHERE, and the lenient filtering the predicate path
 * keeps for VARIANT and computed values stays as it was.
 *
 * <p>★ THE BOOLEAN POSITIONS ARE A THIRD RULE: a searched CASE's WHEN and IFF's condition refuse a
 * VARCHAR at COMPILE time on its declared type, each with a sentence of its own — so the text forms
 * that convert under AND do not save them either. Their full surface, with the predicate rule's HAVING
 * and QUALIFY spellings, is {@link BooleanPositionStrictTypeTest}.
 *
 * <p>The NUMERIC row-time family — arithmetic, unary minus, the math and BIT functions over a text —
 * has a rule of its own shape (one sentence, any position, the echo trimmed): {@link NumericTextRowTimeTest}.
 */
public class LogicalOperatorStrictTextTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (g VARCHAR(10), n NUMBER(10,2))");
        engine.execute("INSERT INTO rt VALUES ('x', 1.00)");
        engine.execute("CREATE OR REPLACE TABLE rtrue (g VARCHAR(10))");
        engine.execute("INSERT INTO rtrue VALUES ('true')");
        engine.execute("CREATE OR REPLACE TABLE rempty (g VARCHAR(10))");
    }

    /** Every row's first column, or the refusal. */
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
    void anUnrecognisedStringRefusesAtRowTime() {
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT g AND TRUE FROM rt"));
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT g OR FALSE FROM rt"));
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT NOT g FROM rt"));
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT 'x' AND TRUE FROM rt"));
    }

    @Test
    void digitsAreWordsNotNumbers() {
        engine.execute("CREATE OR REPLACE TABLE rnum (g VARCHAR(10))");
        engine.execute("INSERT INTO rnum VALUES ('5')");
        assertEquals("Boolean value '5' is not recognized", outcome("SELECT g AND TRUE FROM rnum"));
        assertEquals("Boolean value '5' is not recognized", outcome("SELECT NOT g FROM rnum"));
    }

    @Test
    void theTextFormsConvert() {
        assertEquals("ACCEPTED true", outcome("SELECT g AND TRUE FROM rtrue"));
        assertEquals("ACCEPTED true", outcome("SELECT g OR FALSE FROM rtrue"));
        assertEquals("ACCEPTED false", outcome("SELECT NOT g FROM rtrue"));
    }

    @Test
    void anEmptyTableAnswersBecauseTheRefusalIsPerRow() {
        assertEquals("ACCEPTED", outcome("SELECT g AND TRUE FROM rempty"));
        assertEquals("ACCEPTED", outcome("SELECT g OR FALSE FROM rempty"));
        assertEquals("ACCEPTED", outcome("SELECT NOT g FROM rempty"));
    }

    @Test
    void theBooleanPositionsRefuseAtCompileTimeWhereTheOperatorsConvert() {
        // The same 'true' the operators convert is refused by its DECLARED type in a boolean position.
        assertEquals("ACCEPTED true", outcome("SELECT g AND TRUE FROM rtrue"));
        assertEquals("SQL compilation error:|Can not convert parameter 'RTRUE.G' of type [VARCHAR(10)]"
            + " into expected type [BOOLEAN]", outcome("SELECT CASE WHEN g THEN 1 ELSE 2 END FROM rtrue"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function"
            + " 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))", outcome("SELECT IFF(g, 1, 2) FROM rtrue"));
        // And the operators INSIDE a boolean position keep their row-time rule.
        assertEquals("ACCEPTED 1", outcome("SELECT CASE WHEN g AND TRUE THEN 1 ELSE 2 END FROM rtrue"));
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT IFF(g OR FALSE, 1, 2) FROM rt"));
    }

    @Test
    void thePredicatePositionRefusesAtCompileTimeInstead() {
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(10)] for predicate [RT.G]",
            outcome("SELECT 1 FROM rt WHERE g"));
        // Compile-time, so the empty table changes nothing.
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(10)] for predicate"
            + " [REMPTY.G]", outcome("SELECT 1 FROM rempty WHERE g"));
        // And the text forms do NOT save it — the rule is the declared type, not the value.
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(10)] for predicate"
            + " [RTRUE.G]", outcome("SELECT 1 FROM rtrue WHERE g"));
    }
}
