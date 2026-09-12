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
 * WHEN each of BASE64_ENCODE's optional arguments is judged, which is three different answers for
 * three rules that all look like one:
 *
 * <pre>
 *   argument is a column          COMPILE time   argument N to function F needs to be constant,
 *                                                found 'EB.N'
 *   an integral numeric LITERAL   COMPILE time   invalid argument for function [BASE64_ENCODE]
 *   outside [0, 2147483647]                      unexpected argument [maximum line length] at
 *                                                position 2,                    — at the CALL
 *   everything else about the     ROW time       rounding, the int-range check, and the alphabet's
 *   values                                       own validity
 * </pre>
 *
 * <p>Frostlake refused the negative length from {@code evaluate()}, which is a refusal that never
 * fires over an empty table — so a view or a CTAS over one was created rather than refused. It also
 * refused three things live RUNS, because it read the argument's VALUE where live reads the LITERAL.
 *
 * <p>That distinction is the strangest thing measured here and it is not a rounding artefact:
 * {@code BASE64_ENCODE(g, -1)} refuses the statement while {@code BASE64_ENCODE(g, 0 - 1)} runs it,
 * and both mean minus one. A fractional literal is left to the row as well, so {@code -1.5} is
 * accepted where {@code -2.0} is not.
 */
public class Base64ArgumentDecisionTest extends BaseDatabaseTest {

    private static final String BOUND = "invalid argument for function [BASE64_ENCODE]"
        + " unexpected argument [maximum line length] at position 2,";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eb (g VARCHAR, n NUMBER(10,0))");
        engine.execute("CREATE OR REPLACE TABLE ebe (g VARCHAR, n NUMBER(10,0))");
        engine.execute("INSERT INTO eb VALUES ('hello world of base64', -1)");
    }

    /** The first row's first column with newlines made visible, or the refusal, or "accepted". */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)).replace("\n", "\\n") : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The same for a statement that returns no result set. */
    private String statement(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The bound refusal is a COMPILATION error, positioned on the call — so an empty table is refused too. */
    @Test
    public void theBoundIsDecidedWhileCompiling() {
        assertEquals("SQL compilation error: error line 1 at position 7 " + BOUND,
            outcome("SELECT BASE64_ENCODE(g, -1) FROM eb"));
        assertEquals("SQL compilation error: error line 1 at position 7 " + BOUND,
            outcome("SELECT BASE64_ENCODE(g, -1) FROM ebe"),
            "an empty table refuses the same way — nothing is evaluated to find it");
        assertEquals("SQL compilation error: error line 1 at position 10 " + BOUND,
            outcome("SELECT 1, BASE64_ENCODE(g, -1) FROM eb"),
            "the position follows the CALL, not the statement");
        assertEquals("SQL compilation error: error line 1 at position 23 " + BOUND,
            outcome("SELECT g FROM eb WHERE BASE64_ENCODE(g, -1) = 'x'"));
    }

    /** And because it is a compilation error, a view and a CTAS over an empty table refuse as well. */
    @Test
    public void aViewAndACtasRefuseInsteadOfBeingCreated() {
        assertEquals("SQL compilation error: error line 1 at position 40 " + BOUND,
            statement("CREATE OR REPLACE VIEW vnamed AS SELECT BASE64_ENCODE(g, -1) AS c FROM ebe"));
        assertEquals("SQL compilation error: error line 1 at position 39 " + BOUND,
            statement("CREATE OR REPLACE TABLE tneg AS SELECT BASE64_ENCODE(g, -1) AS c FROM ebe"));
    }

    /** The bound applies to an INTEGRAL NUMERIC LITERAL, and to nothing else. */
    @Test
    public void onlyAnIntegralNumericLiteralIsBounded() {
        final String encoded = "aGVsbG8gd29ybGQgb2YgYmFzZTY0";
        assertEquals(encoded, outcome("SELECT BASE64_ENCODE(g, 0 - 1) FROM eb"),
            "a constant EXPRESSION meaning minus one is run, where the literal is refused");
        assertEquals(encoded, outcome("SELECT BASE64_ENCODE(g, -1 + 0) FROM eb"));
        assertEquals(encoded, outcome("SELECT BASE64_ENCODE(g, '-1') FROM eb"),
            "a string literal is not a numeric one");
        assertEquals(encoded, outcome("SELECT BASE64_ENCODE(g, -1::NUMBER(10,2)) FROM eb"),
            "nor is a cast");
        assertEquals(encoded, outcome("SELECT BASE64_ENCODE(g, -1.5) FROM eb"),
            "nor is a fractional literal, however negative");
        assertEquals(encoded, outcome("SELECT BASE64_ENCODE(g, -0.5) FROM eb"));
    }

    /** An integral literal is bounded at BOTH ends, and 2147483647 is the last one accepted. */
    @Test
    public void theBoundRunsFromZeroToTheIntegerMaximum() {
        assertEquals("SQL compilation error: error line 1 at position 7 " + BOUND,
            outcome("SELECT BASE64_ENCODE(g, -2.0) FROM eb"), "integral though it is written 2.0");
        assertEquals("SQL compilation error: error line 1 at position 7 " + BOUND,
            outcome("SELECT BASE64_ENCODE(g, -1e0) FROM eb"));
        assertEquals("SQL compilation error: error line 1 at position 7 " + BOUND,
            outcome("SELECT BASE64_ENCODE(g, 2147483648) FROM eb"));
        assertEquals("SQL compilation error: error line 1 at position 7 " + BOUND,
            outcome("SELECT BASE64_ENCODE(g, 2147483648.0) FROM eb"));
        assertEquals("aGVsbG8gd29ybGQgb2YgYmFzZTY0",
            outcome("SELECT BASE64_ENCODE(g, 2147483647) FROM eb"));
        assertEquals("aGVsbG8gd29ybGQgb2YgYmFzZTY0",
            outcome("SELECT BASE64_ENCODE(g, 16777217) FROM eb"), "no 16MB limit of its own");
    }

    /** A non-constant argument is a different refusal, unpositioned, naming the relation it read. */
    @Test
    public void aColumnArgumentIsRefusedAsNonConstant() {
        assertEquals("SQL compilation error: argument 2 to function BASE64_ENCODE"
            + " needs to be constant, found 'EB.N'", outcome("SELECT BASE64_ENCODE(g, n) FROM eb"));
        assertEquals("SQL compilation error: argument 2 to function BASE64_ENCODE"
            + " needs to be constant, found 'EBE.N'", outcome("SELECT BASE64_ENCODE(g, n) FROM ebe"));
        assertEquals("SQL compilation error: argument 3 to function BASE64_ENCODE"
            + " needs to be constant, found 'EB.G'", outcome("SELECT BASE64_ENCODE(g, 0, g) FROM eb"));
        assertEquals("SQL compilation error: argument 2 to function BASE64_DECODE_STRING"
            + " needs to be constant, found 'EB.G'",
            outcome("SELECT BASE64_DECODE_STRING(BASE64_ENCODE(g), g) FROM eb"));
    }

    /** What reaches the row is ROUNDED, half away from zero — not truncated. */
    @Test
    public void aFractionalLengthRounds() {
        assertEquals("aGVsbG8gd\\n29ybGQgb2\\nYgYmFzZTY\\n0", outcome("SELECT BASE64_ENCODE(g, 8.7) FROM eb"));
        assertEquals("aG\\nVs\\nbG\\n8g\\nd2\\n9y\\nbG\\nQg\\nb2\\nYg\\nYm\\nFz\\nZT\\nY0",
            outcome("SELECT BASE64_ENCODE(g, 1.5) FROM eb"));
        assertEquals("aGV\\nsbG\\n8gd\\n29y\\nbGQ\\ngb2\\nYgY\\nmFz\\nZTY\\n0",
            outcome("SELECT BASE64_ENCODE(g, 2.5) FROM eb"));
        assertEquals("aGVs\\nbG8g\\nd29y\\nbGQg\\nb2Yg\\nYmFz\\nZTY0",
            outcome("SELECT BASE64_ENCODE(g, 3.5) FROM eb"));
        assertEquals("aGVsbG8g\\nd29ybGQg\\nb2YgYmFz\\nZTY0", outcome("SELECT BASE64_ENCODE(g, '8') FROM eb"));
    }

    /** Rounding is what puts a value out of range at row time, and the ROUNDED value is named. */
    @Test
    public void roundingCanCarryAValueOutOfRange() {
        assertEquals("Numeric value '2147483648' is out of range",
            outcome("SELECT BASE64_ENCODE(g, 2147483647.5) FROM eb"));
    }

    /** A NULL length makes the whole call NULL — an ABSENT one is what means "do not wrap". */
    @Test
    public void aNullLengthIsNotTheDefault() {
        assertEquals("null", outcome("SELECT BASE64_ENCODE(g, NULL) FROM eb"));
        assertEquals("aGVsbG8gd29ybGQgb2YgYmFzZTY0", outcome("SELECT BASE64_ENCODE(g) FROM eb"));
        assertEquals("aGVsbG8gd29ybGQgb2YgYmFzZTY0", outcome("SELECT BASE64_ENCODE(g, 0) FROM eb"));
    }

    /** The alphabet is at most three characters, and none of them may already be base64. */
    @Test
    public void anUnusableAlphabetIsRefusedAtRowTime() {
        assertEquals("String '$%^&' is too long and would be truncated in 'Base64 custom characters'",
            outcome("SELECT BASE64_ENCODE(g, 0, '$%^&') FROM eb"));
        assertEquals("Invalid Base64 custom alphabet or padding characters: 'abc'",
            outcome("SELECT BASE64_ENCODE(g, 0, 'abc') FROM eb"));
        assertEquals("Invalid Base64 custom alphabet or padding characters: '12'",
            outcome("SELECT BASE64_ENCODE(g, 0, '12') FROM eb"));
        assertEquals("Invalid Base64 custom alphabet or padding characters: 'AB'",
            outcome("SELECT BASE64_ENCODE(g, 0, 'AB') FROM eb"));
        assertEquals("Invalid Base64 custom alphabet or padding characters: '$$'",
            outcome("SELECT BASE64_ENCODE(g, 0, '$$') FROM eb"), "the two replacements collide");
        assertEquals("Invalid Base64 custom alphabet or padding characters: '=='",
            outcome("SELECT BASE64_ENCODE(g, 0, '==') FROM eb"));
        assertEquals("Invalid Base64 custom alphabet or padding characters: 'abc'",
            outcome("SELECT BASE64_DECODE_STRING(BASE64_ENCODE(g), 'abc') FROM eb"),
            "the decoders read it back through the same rule");
    }

    /** Row time, so the same unusable alphabet over an EMPTY table is not refused at all. */
    @Test
    public void anUnusableAlphabetOverAnEmptyTableIsAccepted() {
        assertEquals("accepted", statement("SELECT BASE64_ENCODE(g, 0, 'abc') FROM ebe"));
    }

    /** A character replacing ITSELF is legal, which is why the standard alphabet spells out. */
    @Test
    public void anAlphabetMayNameTheCharacterItReplaces() {
        assertEquals("accepted", statement("SELECT BASE64_ENCODE(g, 0, '+/') FROM eb"));
        assertEquals("accepted", statement("SELECT BASE64_ENCODE(g, 0, '$%=') FROM eb"));
        assertEquals("accepted", statement("SELECT BASE64_ENCODE(g, 0, '$') FROM eb"));
        assertEquals("accepted", statement("SELECT BASE64_ENCODE(g, 0, '') FROM eb"));
        assertEquals("accepted", statement("SELECT BASE64_ENCODE(g, 0, NULL) FROM eb"));
    }

    /** HEX_ENCODE's own second argument is judged at ROW time, and a column is legal there. */
    @Test
    public void hexEncodeKeepsItsRowTimeFlag() {
        assertEquals("accepted", statement("SELECT HEX_ENCODE(g, n) FROM ebe"));
        assertEquals("accepted", statement("SELECT HEX_ENCODE(g, -1) FROM ebe"));
        assertEquals("Numeric value is out of range, error: -1", outcome("SELECT HEX_ENCODE(g, n) FROM eb"));
        assertEquals("68656C6C6F20776F726C64206F6620626173653634",
            outcome("SELECT HEX_ENCODE(g, 1.4) FROM eb"), "the flag rounds too");
    }
}
