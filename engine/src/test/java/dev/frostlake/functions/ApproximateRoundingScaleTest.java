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
 * A rounding SCALE limit belongs to the EXACT families only — an approximate value just computes.
 *
 * <p>★ TWO REFUSALS THAT PARTITION THE INPUTS, and Frostlake used to give both to everyone. "Scale
 * too large" exists because a NUMBER(p,s) cannot be rounded to a place its own width does not reach.
 * A FLOAT has no declared scale to exceed, so the limit means nothing there and live simply answers —
 * while the SAME call over a NUMBER(38,0) is refused for the other reason entirely, that the RESULT
 * does not fit. Neither refusal is wrong; each is wrong for the other family.
 *
 * <p>★ WHICH FAMILY IT IS CANNOT BE READ FROM THE VALUE. A FLOAT column carries its value exactly, so
 * the runtime carrier of a FLOAT and of a NUMBER are the same object — only the DECLARED type tells
 * them apart, and a VARIANT tells it a third way, through the node it holds. A VARCHAR is exact: live
 * gives it the scale refusal, which is the cell that stops "approximate" from meaning "not a number".
 *
 * <p>★ AND THE ARITHMETIC IS ORDINARY FLOATING POINT, with no limit bolted on: a scale so large that
 * the power of ten overflows answers NaN — the value over infinity is zero, and zero times infinity
 * is not a number — rather than refusing. That cell is the proof the rule is computed, not tabulated.
 */
public class ApproximateRoundingScaleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rr (f FLOAT, n NUMBER(38,0), d DOUBLE, s VARCHAR,"
            + " v VARIANT)");
        engine.execute("INSERT INTO rr SELECT 1.0, 1, 1.0, '1.0', TO_VARIANT(1.0::FLOAT)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The whole family at a scale no exact type could take — each rounds its own way. */
    @Test
    public void thefamilyAnswersOverAFloat() {
        assertEquals("1.0E38", answer("SELECT CEIL(f, -38) FROM rr"),
            "ceiling to the next multiple of 10^38, which is where 1.0 rounds UP to");
        assertEquals("0.0", answer("SELECT FLOOR(f, -38) FROM rr"),
            "and the same value rounds DOWN to zero — the direction is all that differs");
        assertEquals("0.0", answer("SELECT ROUND(f, -38) FROM rr"));
        assertEquals("0.0", answer("SELECT TRUNC(f, -38) FROM rr"));
        assertEquals("0.0", answer("SELECT TRUNCATE(f, -38) FROM rr"));
    }

    /** ★ The exact half, unchanged — the refusal that is right for a NUMBER stays right. */
    @Test
    public void theexactFamiliesKeepTheirOwnRefusals() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){nullable},"
            + " value 100000000000000000000000000000000000000",
            answer("SELECT CEIL(n, -38) FROM rr"),
            "★ a NUMBER is refused for the RESULT, not the scale — the other of the two refusals");
        assertEquals("Invalid parameter value: -38. Reason: Scale too large",
            answer("SELECT CEIL(s, -38) FROM rr"),
            "★ and a VARCHAR is EXACT: it still gets the scale limit, so approximate is a family and"
                + " not merely 'anything that is not a NUMBER'");
    }

    /** ★ The other two approximate carriers, which say so in two different places. */
    @Test
    public void theotherApproximateCarriers() {
        assertEquals("1.0E38", answer("SELECT CEIL(d, -38) FROM rr"),
            "a DOUBLE column, whose declared type carries the fact");
        assertEquals("1.0E38", answer("SELECT CEIL(v, -38) FROM rr"),
            "★ and a VARIANT, whose declared type can only say VARIANT — the NODE carries it instead");
        assertEquals("1.0E38", answer("SELECT CEIL(1.0::FLOAT, -38)"),
            "a literal cast, with no column in sight");
    }

    /** ★ Ordinary floating-point arithmetic all the way out, including where it stops being a number. */
    @Test
    public void thearithmeticIsPlainFloatingPoint() {
        assertEquals("NaN", answer("SELECT CEIL(f, -400) FROM rr"),
            "★ the power of ten overflows, so the answer is NaN rather than a refusal");
        assertEquals("1.0", answer("SELECT CEIL(f, 40) FROM rr"),
            "a scale past the value's precision is a no-op, which has no exact-numeric analogue");
        assertEquals("10.0", answer("SELECT CEIL(f, -1) FROM rr"),
            "and the ordinary small case still rounds to a power of ten");
    }

    /** The result is a FLOAT, in the select list and through a table it builds. */
    @Test
    public void theresultIsApproximateToo() {
        assertEquals("FLOAT[DOUBLE]", answer("SELECT SYSTEM$TYPEOF(CEIL(f, -38)) FROM rr"));
        engine.execute("CREATE OR REPLACE TABLE rr2 AS SELECT CEIL(f, -38) AS c FROM rr");
        assertEquals("FLOAT[DOUBLE]", answer("SELECT SYSTEM$TYPEOF(c) FROM rr2"),
            "the family survives into a derived column, so nothing downstream re-exacts it");
    }
}
