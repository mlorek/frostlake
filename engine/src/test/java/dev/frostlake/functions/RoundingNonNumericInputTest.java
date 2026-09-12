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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the rounding family declares over an input that is NOT a number. Two different answers, and the
 * split is not where it looks:
 *
 * <pre>
 *   a VARIANT        FLOAT, scale argument or not
 *   a VARCHAR        FLOAT with NO scale — but a real NUMBER once a scale is written
 * </pre>
 *
 * <p>★ AND THE NUMBER IT BECOMES IS NUMBER(18,5). That is derived, not guessed: it is the only (p, si)
 * that reproduces every measured width through the family's existing rule —
 *
 * <pre>
 *   ROUND(&lt;VARCHAR&gt;, 0)  NUMBER(19,0)     ROUND(&lt;VARCHAR&gt;, 4)  NUMBER(19,4)
 *   ROUND(&lt;VARCHAR&gt;, 1)  NUMBER(19,1)     ROUND(&lt;VARCHAR&gt;, 5)  NUMBER(18,5)
 *   ROUND(&lt;VARCHAR&gt;, 2)  NUMBER(19,2)     ROUND(&lt;VARCHAR&gt;, 6)  NUMBER(18,5)
 * </pre>
 *
 * <p>Scale 5 is the cell that pins it. Up to 4 the rule widens by a carry digit, at 5 it reaches the
 * implied scale and the "nothing is being dropped" branch returns the implied type ITSELF — printing
 * its 18 and its 5 outright — and 6 cannot go further. Any other implied pair breaks one of the six.
 *
 * <p>The VARCHAR's own declared LENGTH does not enter: VARCHAR(3) and VARCHAR(10) both give
 * NUMBER(19,1) at scale 1.
 *
 * <p>The declared type is read through a CTAS and DESCRIBE, because the live harness reports every
 * result column as VARCHAR and could not tell these apart.
 */
public class RoundingNonNumericInputTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rnn (s VARCHAR(10), s3 VARCHAR(3), v VARIANT)");
        engine.execute("INSERT INTO rnn SELECT '3', '3', PARSE_JSON('3')");
    }

    /** The declared type of a one-column CTAS over {@code expr}. */
    private String declared(final String expr) {
        engine.execute("CREATE OR REPLACE TABLE rnn_t AS SELECT " + expr + " AS c FROM rnn");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE rnn_t");
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no rows>";
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
    }

    /** With no scale to hold, there is no width to widen from and the answer is FLOAT. */
    @Test
    public void withNoScaleTheAnswerIsFloat() {
        assertEquals("FLOAT", declared("CEIL(s)"));
        assertEquals("FLOAT", declared("FLOOR(s)"));
        assertEquals("FLOAT", declared("ROUND(s)"));
        assertEquals("FLOAT", declared("ABS(s)"), "ABS has no scale argument at all");
        assertEquals("FLOAT", declared("CEIL('3')"), "and a literal string is no different");
    }

    /** A VARIANT is FLOAT whether a scale is written or not. */
    @Test
    public void aVariantIsAlwaysFloat() {
        assertEquals("FLOAT", declared("CEIL(v)"));
        assertEquals("FLOAT", declared("CEIL(v, 1)"));
        assertEquals("FLOAT", declared("ROUND(v, 1)"));
    }

    /** ★ A VARCHAR WITH a scale reads as NUMBER(18,5) and runs the ordinary rule. */
    @Test
    public void aVarcharWithAScaleReadsAsNumberEighteenFive() {
        assertEquals("NUMBER(19,0)", declared("ROUND(s, 0)"));
        assertEquals("NUMBER(19,1)", declared("ROUND(s, 1)"));
        assertEquals("NUMBER(19,2)", declared("ROUND(s, 2)"));
        assertEquals("NUMBER(19,4)", declared("ROUND(s, 4)"));
        assertEquals("NUMBER(18,5)", declared("ROUND(s, 5)"),
            "at the implied scale the rule stops widening and prints the implied type itself");
        assertEquals("NUMBER(18,5)", declared("ROUND(s, 6)"), "and cannot go past it");
        assertEquals("NUMBER(19,0)", declared("ROUND(s, -1)"),
            "a negative scale floors at zero, as it does for a real column");
    }

    /** The whole family follows it, not just ROUND. */
    @Test
    public void theWholeFamilyFollowsIt() {
        assertEquals("NUMBER(19,1)", declared("CEIL(s, 1)"));
        assertEquals("NUMBER(19,3)", declared("FLOOR(s, 3)"));
        assertEquals("NUMBER(19,1)", declared("TRUNC(s, 1)"));
    }

    /** The VARCHAR's declared LENGTH does not enter. */
    @Test
    public void theVarcharLengthDoesNotEnter() {
        assertEquals("NUMBER(19,1)", declared("ROUND(s, 1)"));
        assertEquals("NUMBER(19,1)", declared("ROUND(s3, 1)"), "a VARCHAR(3) gives the same");
    }

    /** And the VALUES follow the declared type. */
    @Test
    public void theValuesFollowTheDeclaredType() {
        assertEquals("3.0", answer("SELECT CEIL(s) FROM rnn"), "a FLOAT answer, not the integer 3");
        assertEquals("3.0", answer("SELECT CEIL(v) FROM rnn"));
        assertEquals("3.0", answer("SELECT ROUND(s, 1) FROM rnn"));
    }
}
