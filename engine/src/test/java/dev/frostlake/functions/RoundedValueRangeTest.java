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
 * A rounded value its own declared type cannot hold.
 *
 * <p>★ ONLY ROUNDING UP CAN REACH IT. {@code CEIL(<NUMBER(38,0)>, -38)} is 10^38 — one digit past what
 * a NUMBER(38,0) holds — and live refuses the value rather than answering it. FLOOR, ROUND and TRUNC
 * over the same input all round toward zero and answer 0, on both engines. Frostlake answered the
 * 39-digit number, which is the wrong side of the fidelity rule: a value no Snowflake column could
 * hold came back as if it could.
 *
 * <p>★ THE PRINTED TYPE IS THE RESULT'S, NOT THE INPUT'S, which is what makes the check "does it fit
 * at all" rather than "does it fit what it came from". {@code CEIL(<NUMBER(21,0)>, -21)} produces
 * 10^21 and is ACCEPTED, because the rounding family's typing rule has already widened the declared
 * type to NUMBER(38,0) and 10^21 fits that. The same column at scale −38 refuses, and the message
 * names NUMBER(38,0) either way.
 *
 * <p>★ THE NULLABILITY IS THE COLUMN'S, spelled into the message — {@code {nullable}} or
 * {@code {not null}}. It is the one part that is not derivable from the value or its type.
 *
 * <p>★ AND IT IS A ROW-TIME REFUSAL: over an EMPTY table the same call returns no rows on both
 * engines, so it fires per value and not while compiling.
 *
 * <p>A SCALED input never gets this far — a scale past the type's own is refused first, as "Scale too
 * large" — which is why the storage class in the message is always the 38-digit one.
 *
 * <p>NOT FIXED HERE, and tracked separately: the same sentence over a CAST and over an INSERT, where
 * the storage class varies and Frostlake has a wording of its own; a 39-digit LITERAL, which live
 * refuses in the reader; and {@code CEIL(<FLOAT>, -38)}, which Frostlake refuses as "Scale too large"
 * where live answers 1.0E38.
 */
public class RoundedValueRangeTest extends BaseDatabaseTest {

    private static final String TEN_TO_38 = "100000000000000000000000000000000000000";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rr (n38 NUMBER(38,0), n382 NUMBER(38,2),"
            + " n21 NUMBER(21,0))");
        engine.execute("INSERT INTO rr VALUES (1, 1.00, 1)");
        engine.execute("CREATE OR REPLACE TABLE rnn (nn NUMBER(38,0) NOT NULL)");
        engine.execute("INSERT INTO rnn VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE rempty (n38 NUMBER(38,0))");
    }

    /** Every row's first column, joined — empty when the query returns none. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(",");
                }
                all.append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String outOfRange(final String nullability) {
        return "Number out of representable range: type FIXED[SB16](38,0){" + nullability
            + "}, value " + TEN_TO_38;
    }

    /** ★ Rounding UP past the type's own width is refused, not answered. */
    @Test
    public void roundingUpPastTheWidthIsRefused() {
        assertEquals(outOfRange("nullable"), answer("SELECT CEIL(n38, -38) FROM rr"));
    }

    /** ★ And the message carries the COLUMN's nullability. */
    @Test
    public void themessageCarriesTheColumnsNullability() {
        assertEquals(outOfRange("not null"), answer("SELECT CEIL(nn, -38) FROM rnn"));
    }

    /** ★ The printed type is the RESULT's — a narrower column reaches the same refusal. */
    @Test
    public void theprintedTypeIsTheResults() {
        assertEquals(outOfRange("nullable"), answer("SELECT CEIL(n21, -38) FROM rr"));
        assertEquals("1000000000000000000000", answer("SELECT CEIL(n21, -21) FROM rr"),
            "and the same column at a scale it CAN reach is answered, because the result type widened");
    }

    /** The rest of the family rounds toward zero and never overflows. */
    @Test
    public void therestOfTheFamilyRoundsTowardZero() {
        assertEquals("0", answer("SELECT FLOOR(n38, -38) FROM rr"));
        assertEquals("0", answer("SELECT ROUND(n38, -38) FROM rr"));
        assertEquals("0", answer("SELECT TRUNC(n38, -38) FROM rr"));
        assertEquals("0", answer("SELECT TRUNCATE(n38, -38) FROM rr"));
    }

    /** ★ It is a ROW-time refusal — no rows, no refusal. */
    @Test
    public void anemptyTableIsNotRefused() {
        assertEquals("", answer("SELECT CEIL(n38, -38) FROM rempty"));
    }

    /** A SCALED input is stopped earlier, by the scale check, and never reaches the range one. */
    @Test
    public void ascaledInputIsStoppedEarlier() {
        assertEquals("Invalid parameter value: -38. Reason: Scale too large",
            answer("SELECT CEIL(n382, -38) FROM rr"));
        assertEquals("Invalid parameter value: -37. Reason: Scale too large",
            answer("SELECT CEIL(n382, -37) FROM rr"));
    }

    /** CEILING is not a Snowflake spelling — the control that keeps the family's membership honest. */
    @Test
    public void ceilingIsNotASpelling() {
        assertEquals("SQL compilation error:|Unknown function CEILING.",
            answer("SELECT CEILING(n38, -38) FROM rr"));
    }
}
