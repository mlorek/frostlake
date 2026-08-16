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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two rules the comparison had lost, both because a temporal pair fell through to a comparison of the
 * two RENDERINGS as text.
 *
 * <p>First, a DATE beside a TIMESTAMP compares by INSTANT, the date read as its own midnight. Compared
 * as text, "2020-01-01" never matches "2020-01-01T00:00", so the same moment was never equal to itself.
 *
 * <p>Second, a string beside a temporal is READ as that temporal, and text that cannot be read is an
 * ERROR rather than an inequality — exactly as a string that cannot read as a number is. The numeric
 * side already raised {@code Numeric value 'ab' is not recognized}; the temporal side quietly answered
 * false. The sentence names the family of the TEMPORAL operand, so one 'ab' is reported three ways:
 *
 * <pre>
 *   v = d    Date 'ab' is not recognized
 *   v = ts   Timestamp 'ab' is not recognized
 *   v = tm   Time 'ab' is not recognized
 * </pre>
 *
 * <p>Both rules belong to the comparison itself, so every surface that routes through it inherits
 * them: the operator, IN, a simple CASE and DECODE. NULLIF does not yet — it still decides equality
 * with {@link Object#equals}, which is tracked separately.
 */
public class TemporalComparisonTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tc (d DATE, ts TIMESTAMP_NTZ, later TIMESTAMP_NTZ,"
            + " i INT, v VARCHAR(10), tm TIME)");
        engine.execute("INSERT INTO tc SELECT '2020-01-01', '2020-01-01 00:00:00',"
            + " '2020-01-01 10:20:30', 1, 'ab', '10:00:00'");
    }

    /** The expression's answer, or the message of the refusal it raised. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT COALESCE(TO_VARCHAR(" + expr + "), '<NULL>') AS x FROM tc");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Midnight of a day IS that day, from either side. */
    @Test
    public void aDateEqualsATimestampAtTheSameInstant() {
        assertEquals("true", outcome("d = ts"));
        assertEquals("true", outcome("ts = d"));
        assertEquals("false", outcome("d = later"), "a different instant is still unequal");
        assertEquals("true", outcome("d < later"), "and ordering across the pair is unchanged");
    }

    /** Every surface that compares inherits it — not just the operator. */
    @Test
    public void theSameInstantMatchesOnEverySurface() {
        assertEquals("true", outcome("d IN (ts)"));
        assertEquals("eq", outcome("CASE d WHEN ts THEN 'eq' ELSE 'ne' END"));
        assertEquals("eq", outcome("DECODE(d, ts, 'eq', 'ne')"));
    }

    /** Text that cannot read as the temporal beside it raises, naming that temporal's family. */
    @Test
    public void unreadableTextRaisesTheFamilysOwnSentence() {
        assertEquals("Date 'ab' is not recognized", outcome("v = d"));
        assertEquals("Date 'ab' is not recognized", outcome("d = v"));
        assertEquals("Timestamp 'ab' is not recognized", outcome("v = ts"));
        assertEquals("Time 'ab' is not recognized", outcome("v = tm"));
        assertEquals("Date 'ab' is not recognized", outcome("v < d"), "ordering raises it too");
    }

    /** And that refusal reaches the surfaces the same way the equality does. */
    @Test
    public void theRefusalReachesEverySurface() {
        assertEquals("Date 'ab' is not recognized", outcome("d IN (v)"));
        assertEquals("Date 'ab' is not recognized",
            outcome("CASE d WHEN v THEN 'eq' ELSE 'ne' END"));
        assertEquals("Date 'ab' is not recognized", outcome("DECODE(d, v, 'eq', 'ne')"));
    }

    /** The numeric twin, which already behaved, pinned so the two families stay in step. */
    @Test
    public void theNumericTwinRaisesItsOwnSentence() {
        assertEquals("Numeric value 'ab' is not recognized", outcome("v = i"));
        assertEquals("Numeric value 'ab' is not recognized", outcome("i IN (v)"));
        assertEquals("Numeric value 'ab' is not recognized",
            outcome("CASE i WHEN v THEN 'eq' ELSE 'ne' END"));
        assertEquals("Numeric value 'ab' is not recognized", outcome("DECODE(i, v, 'eq', 'ne')"));
    }

    /** A DECODE that matches earlier never reaches the unreadable branch, so it does not raise. */
    @Test
    public void anEarlierDecodeMatchShortCircuitsTheBadBranch() {
        assertEquals("first", outcome("DECODE(i, 1, 'first', v, 'second', 'other')"));
        assertEquals("first", outcome("DECODE(d, d, 'first', v, 'second', 'other')"));
    }
}
