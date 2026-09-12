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
 * How a refusal ECHOES an operator expression. Live prints the outermost operator BARE and keeps the
 * brackets underneath it, so the echo's bracketing follows the plan's tree rather than wrapping
 * everything:
 *
 * <pre>
 *   g || 'x'       HEX_ENCODE(EB.G || 'x', 1, 1)
 *   (i + 1) * 2    HEX_ENCODE(EB.BN, (EB.I + 1) * 2, 1)
 *   i + (1 * 2)    HEX_ENCODE(EB.BN, EB.I + (1 * 2), 1)
 * </pre>
 *
 * <p>The third line is the one that pins the rule: those brackets are REDUNDANT — precedence already
 * groups the multiplication — and live keeps them anyway. So the inner brackets are exactly the ones
 * the ordinary printer writes, and only the outermost wrapper had to go. That makes this a depth test,
 * not a precedence calculation, which is why it is small.
 *
 * <p>Deliberately message-only. The shared printer brackets unconditionally because its output is used
 * as an expression KEY, and there {@code (a + b) * c} and {@code a + (b * c)} must not print alike —
 * two different aggregates would share one key and one would read the other's value.
 *
 * <p>What this does NOT do is reproduce live's other plan rewrites: the implicit CASTs it prints, its
 * CASE_FLATTENED, or its GET() for a colon path. Those remain divergent by decision — see the notes on
 * the task that measured them.
 */
public class RefusalEchoBareOperatorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eb (bn BINARY, n NUMBER(10,2), g VARCHAR,"
            + " i INT, d DATE)");
        engine.execute("INSERT INTO eb SELECT TO_BINARY('AB'), 2.50, 'zz', 3, DATE '2020-01-01'");
    }

    private String refusal(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED: " + (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String tooMany(final String echoedArgs) {
        return "SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [HEX_ENCODE(" + echoedArgs + ")] expected 2, got 3";
    }

    /**
     * The outermost operator is printed BARE.
     *
     * <p>Only operand pairs that need NO implicit cast can be asserted here. A mixed-width pair such as
     * {@code n + i} still diverges — live prints {@code EB.N + (CAST(EB.I AS NUMBER(38,2)))}, a width
     * neither column has — and that is the declined half of this work, not something the bracket rule
     * reaches.
     */
    @Test
    public void theOutermostOperatorIsBare() {
        assertEquals(tooMany("EB.G || 'x', 1, 1"),
            refusal("SELECT HEX_ENCODE(g || 'x', 1, 1) FROM eb"));
    }

    /** ★ The brackets UNDER it are kept — including a redundant pair, which live keeps too. */
    @Test
    public void theBracketsUnderneathAreKept() {
        assertEquals(tooMany("EB.BN, (EB.I + 1) * 2, 1"),
            refusal("SELECT HEX_ENCODE(bn, (i + 1) * 2, 1) FROM eb"));
        assertEquals(tooMany("EB.BN, EB.I + (1 * 2), 1"),
            refusal("SELECT HEX_ENCODE(bn, i + (1 * 2), 1) FROM eb"),
            "precedence makes these brackets unnecessary and the echo prints them anyway");
    }

    /** An expression with no operator at its root is untouched. */
    @Test
    public void anExpressionWithNoOperatorIsUnchanged() {
        assertEquals(tooMany("EB.G, 1, 1"), refusal("SELECT HEX_ENCODE(g, 1, 1) FROM eb"));
        assertEquals(tooMany("UPPER(EB.G), 1, 1"),
            refusal("SELECT HEX_ENCODE(UPPER(g), 1, 1) FROM eb"));
        assertEquals(tooMany("EB.D, 1, 1"), refusal("SELECT HEX_ENCODE(d, 1, 1) FROM eb"));
    }

    /** And the refusals that echo no expression at all are unaffected. */
    @Test
    public void theRefusalsWithNoEchoAreUnaffected() {
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|not enough arguments for function [HEX_ENCODE()], expected 1, got 0",
            refusal("SELECT HEX_ENCODE()"));
        assertEquals("SQL compilation error:|Unknown function NO_SUCH_FN.",
            refusal("SELECT NO_SUCH_FN(g) FROM eb"));
        assertEquals("SQL compilation error: error line 1 at position 18|invalid identifier 'NOPE'",
            refusal("SELECT HEX_ENCODE(nope, 1, 1) FROM eb"));
    }
}
