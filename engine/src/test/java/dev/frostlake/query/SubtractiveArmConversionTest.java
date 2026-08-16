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
 * When a SUBTRACTIVE set operation converts its right branch. A UNION has always converted the later
 * branch to the leading types, so an unconvertible value refuses; EXCEPT, MINUS and INTERSECT compared
 * lazily instead and ANSWERED — inconsistently with each other, which is the tell:
 *
 * <pre>
 *   SELECT bo UNION ALL SELECT bad     both: Boolean value 'ab' is not recognized
 *   SELECT bo EXCEPT    SELECT bad     was [true]   live: Boolean value 'ab' is not recognized
 *   SELECT bo INTERSECT SELECT bad     was []       live: the same refusal
 * </pre>
 *
 * <p>One kept the row and the other dropped it, and neither was live's answer.
 *
 * <p>★ THE EMPTY LEFT SIDE IS THE WHOLE QUESTION, and it was measured rather than reasoned about. A
 * subtractive operator over an empty left side SHORT-CIRCUITS — that is live-verified behaviour, and it
 * means the right branch is never looked at. So "convert the right branch eagerly" would refuse
 * statements live answers. The measurement settles it: live ANSWERS over an empty left
 * ({@code SELECT bo FROM sv WHERE FALSE EXCEPT SELECT bad FROM sv} is zero rows, not a refusal) and
 * REFUSES over a non-empty one. Both behaviours are real, so the conversion goes AFTER the
 * short-circuit and not before it — one line in each operator, on the far side of the early return.
 *
 * <p>An empty RIGHT side is not the same thing and never was: there is nothing to convert, so it
 * answers either way.
 */
public class SubtractiveArmConversionTest extends BaseDatabaseTest {

    private static final String UNCONVERTIBLE = "Boolean value 'ab' is not recognized";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sv (bo BOOLEAN, bad VARCHAR(5), good VARCHAR(5))");
        engine.execute("INSERT INTO sv VALUES (TRUE, 'ab', 'true')");
    }

    /** The rows, joined, or the refusal. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("[");
            while (rs.next()) {
                if (all.length() > 1) {
                    all.append(",");
                }
                all.append(String.valueOf(rs.getValue(0)));
            }
            return all.append("]").toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A NON-EMPTY left side converts the right branch, so an unconvertible value refuses. */
    @Test
    public void aNonEmptyLeftConvertsTheRightBranch() {
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv EXCEPT SELECT bad FROM sv"));
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv MINUS SELECT bad FROM sv"));
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv INTERSECT SELECT bad FROM sv"));
    }

    /** The UNION forms, which already did this and are the shape the others now match. */
    @Test
    public void theUnionFormsAreUnchanged() {
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv UNION SELECT bad FROM sv"));
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv UNION ALL SELECT bad FROM sv"));
    }

    /**
     * ★ An EMPTY left side short-circuits and never looks at the right branch, so the same statement
     * ANSWERS. This is the cell that decides where the conversion belongs, and it must keep answering.
     */
    @Test
    public void anEmptyLeftStillShortCircuits() {
        assertEquals("[]", outcome("SELECT bo FROM sv WHERE FALSE EXCEPT SELECT bad FROM sv"));
        assertEquals("[]", outcome("SELECT bo FROM sv WHERE FALSE MINUS SELECT bad FROM sv"));
        assertEquals("[]", outcome("SELECT bo FROM sv WHERE FALSE INTERSECT SELECT bad FROM sv"));
        assertEquals("[]", outcome("SELECT bo FROM sv WHERE FALSE EXCEPT"
            + " SELECT bad FROM sv WHERE FALSE"), "and with both sides empty");
    }

    /** A UNION has no such short-circuit — an empty left converts the right branch anyway. */
    @Test
    public void aUnionHasNoShortCircuit() {
        assertEquals(UNCONVERTIBLE,
            outcome("SELECT bo FROM sv WHERE FALSE UNION SELECT bad FROM sv"));
        assertEquals(UNCONVERTIBLE,
            outcome("SELECT bo FROM sv WHERE FALSE UNION ALL SELECT bad FROM sv"));
    }

    /** An empty RIGHT side has nothing to convert, so every operator answers. */
    @Test
    public void anEmptyRightSideHasNothingToConvert() {
        assertEquals("[true]", outcome("SELECT bo FROM sv EXCEPT SELECT bad FROM sv WHERE FALSE"));
        assertEquals("[]", outcome("SELECT bo FROM sv INTERSECT SELECT bad FROM sv WHERE FALSE"));
        assertEquals("[true]", outcome("SELECT bo FROM sv UNION SELECT bad FROM sv WHERE FALSE"));
    }

    /** A CONVERTIBLE right side is converted and COMPARED, which is what tells conversion from refusal. */
    @Test
    public void aConvertibleRightSideIsCompared() {
        assertEquals("[]", outcome("SELECT bo FROM sv EXCEPT SELECT good FROM sv"),
            "'true' converts to TRUE and cancels the left row");
        assertEquals("[true]", outcome("SELECT bo FROM sv INTERSECT SELECT good FROM sv"));
        assertEquals("[true]", outcome("SELECT bo FROM sv UNION SELECT good FROM sv"));
    }

    /** With the unconvertible value on the LEFT the leading type is the string, so nothing converts. */
    @Test
    public void anUnconvertibleLeftIsTheLeadingTypeInstead() {
        assertEquals("[ab]", outcome("SELECT bad FROM sv EXCEPT SELECT bo FROM sv"));
        assertEquals("[]", outcome("SELECT bad FROM sv INTERSECT SELECT bo FROM sv"));
        assertEquals("[ab,true]", outcome("SELECT bad FROM sv UNION SELECT bo FROM sv"));
    }

    /** A chain mixing the operators converts at each step, not only when every operator is a UNION. */
    @Test
    public void aMixedChainConvertsAtEveryStep() {
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv UNION ALL SELECT good FROM sv"
            + " EXCEPT SELECT bad FROM sv"));
        assertEquals(UNCONVERTIBLE, outcome("SELECT bo FROM sv EXCEPT SELECT good FROM sv"
            + " UNION ALL SELECT bad FROM sv"));
    }
}
