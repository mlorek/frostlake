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
 * What the temporal READER makes of a zone offset written into a timestamp literal. Frostlake did two
 * different wrong things depending on the spelling, and the one that parsed was the worse:
 *
 * <pre>
 *   '2020-01-01 10:00:00 +0300'    would not parse at all
 *   '2020-01-01 10:00:00 +03:00'   parsed, and the offset was SILENTLY DISCARDED — so the value read
 *                                  back as ten o'clock, three hours away from the instant written
 * </pre>
 *
 * <p>The accepted spellings are narrow and were measured one at a time: {@code +HHMM}, {@code +HH:MM},
 * {@code +HH}, {@code +H} and an UPPER-CASE {@code Z}, with or without a space before them. A
 * lower-case {@code z}, a seconds field, and the zone NAMES {@code UTC}, {@code GMT} and
 * {@code America/Los_Angeles} are all refused — the names despite reading like the most obvious
 * spellings there are. The offset's RANGE is not checked: {@code +15:00} is taken.
 *
 * <p>What the offset MEANS depends on the target. A TIMESTAMP_NTZ and a DATE keep the wall clock and
 * drop it; a TIMESTAMP_TZ or _LTZ moves the instant by it. Both are asserted here — the NTZ by its
 * rendering and the TZ by its epoch, which is the instant itself.
 *
 * <p>Not asserted: how a TIMESTAMP_TZ RENDERS afterwards. Live prints the offset it was written with
 * ({@code 2020-01-01 10:00:00.000 +0300}) and Frostlake normalises to UTC, because the offset has
 * nowhere to live while these are held as a naive local date-time. That is the storage question, and
 * it is tracked separately.
 */
public class TemporalOffsetReaderTest extends BaseDatabaseTest {

    /** The answer, or the refusal, for an expression over a literal. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ").trim();
        }
    }

    /** A TIMESTAMP_NTZ keeps the wall clock, whatever offset the text carried. */
    private void wallClockKept(final String text) {
        assertEquals("2020-01-01 10:00:00.000",
            outcome("TO_VARCHAR('" + text + "'::TIMESTAMP_NTZ)"), text);
    }

    /** A TIMESTAMP_TZ moves the instant by the offset — read back as an epoch second. */
    private void instantMovedTo(final String text, final String epochSecond) {
        assertEquals(epochSecond,
            outcome("DATE_PART(epoch_second, '" + text + "'::TIMESTAMP_TZ)"), text);
    }

    /** Every spelling live takes parses, and the wall clock survives into a TIMESTAMP_NTZ. */
    @Test
    public void everyAcceptedSpellingParses() {
        wallClockKept("2020-01-01 10:00:00 +0300");
        wallClockKept("2020-01-01 10:00:00 -0800");
        wallClockKept("2020-01-01 10:00:00 +03:00");
        wallClockKept("2020-01-01 10:00:00 +03");
        wallClockKept("2020-01-01 10:00:00 +3");
        wallClockKept("2020-01-01 10:00:00 -05:30");
        wallClockKept("2020-01-01 10:00:00Z");
        wallClockKept("2020-01-01 10:00:00 Z");
        wallClockKept("2020-01-01 10:00:00+0300");
        wallClockKept("2020-01-01T10:00:00+03:00");
        wallClockKept("2020-01-01 10:00:00 +15:00");
    }

    /** And the offset moves the INSTANT, which is the half that was silently dropped. */
    @Test
    public void theOffsetMovesTheInstant() {
        instantMovedTo("2020-01-01 10:00:00 +0300", "1577862000");
        instantMovedTo("2020-01-01 10:00:00 +03:00", "1577862000");
        instantMovedTo("2020-01-01 10:00:00 +03", "1577862000");
        instantMovedTo("2020-01-01 10:00:00 +3", "1577862000");
        instantMovedTo("2020-01-01 10:00:00+0300", "1577862000");
        instantMovedTo("2020-01-01T10:00:00+03:00", "1577862000");
        instantMovedTo("2020-01-01 10:00:00 -0800", "1577901600");
        instantMovedTo("2020-01-01 10:00:00 -05:30", "1577892600");
        instantMovedTo("2020-01-01 10:00:00Z", "1577872800");
        instantMovedTo("2020-01-01 10:00:00 Z", "1577872800");
        instantMovedTo("2020-01-01 10:00:00 +14:00", "1577822400");
        instantMovedTo("2020-01-01 10:00:00 +15:00", "1577818800",
            "the offset's RANGE is not checked");
    }

    /** Overload carrying a note, so the range case reads as the deliberate cell it is. */
    private void instantMovedTo(final String text, final String epochSecond, final String note) {
        assertEquals(epochSecond,
            outcome("DATE_PART(epoch_second, '" + text + "'::TIMESTAMP_TZ)"), note);
    }

    /** The spellings live refuses, each naming the type it could not read the text as. */
    @Test
    public void theRefusedSpellingsNameTheType() {
        assertEquals("Timestamp '2020-01-01 10:00:00z' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00z'::TIMESTAMP_TZ)"),
            "a LOWER-case z is not a zone designator");
        assertEquals("Timestamp '2020-01-01 10:00:00 +03:00:30' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00 +03:00:30'::TIMESTAMP_TZ)"),
            "and an offset carries no seconds field");
        assertEquals("Timestamp '2020-01-01 10:00:00 UTC' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00 UTC'::TIMESTAMP_TZ)"));
        assertEquals("Timestamp '2020-01-01 10:00:00 GMT' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00 GMT'::TIMESTAMP_TZ)"));
        assertEquals("Timestamp '2020-01-01 10:00:00 America/Los_Angeles' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00 America/Los_Angeles'::TIMESTAMP_TZ)"),
            "a named zone is not a spelling the reader takes at all");
    }

    /** A DATE names ITSELF in the same sentence, and drops any offset the text carried. */
    @Test
    public void aDateNamesItselfAndDropsTheOffset() {
        assertEquals("2020-01-01", outcome("TO_VARCHAR('2020-01-01 10:00:00 +0300'::DATE)"));
        assertEquals("2020-01-01", outcome("TO_VARCHAR('2020-01-01 10:00:00 -05:30'::DATE)"));
        assertEquals("Date '2020-01-01 10:00:00 UTC' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00 UTC'::DATE)"));
        assertEquals("Date '2020-01-01 10:00:00z' is not recognized",
            outcome("TO_VARCHAR('2020-01-01 10:00:00z'::DATE)"));
    }

    /** The TO_ functions split the same way the casts do. */
    @Test
    public void theToTimestampFunctionsSplitTheSameWay() {
        assertEquals("2020-01-01 10:00:00.000",
            outcome("TO_VARCHAR(TO_TIMESTAMP_NTZ('2020-01-01 10:00:00 +0300'))"));
        assertEquals("1577862000",
            outcome("DATE_PART(epoch_second, TO_TIMESTAMP_TZ('2020-01-01 10:00:00 +0300'))"));
        assertEquals("1577862000",
            outcome("DATE_PART(epoch_second, TO_TIMESTAMP_LTZ('2020-01-01 10:00:00 +0300'))"));
        assertEquals("Timestamp '2020-01-01 10:00:00z' is not recognized",
            outcome("TO_VARCHAR(TO_TIMESTAMP_TZ('2020-01-01 10:00:00z'))"));
    }

    /**
     * A text with NO offset is left where it stands, which is the case that must not move.
     *
     * <p>The TIMESTAMP_TZ reading of the same text is NOT asserted: with no offset written, live
     * reads it in the SESSION zone and Frostlake at UTC, so the two differ by the session parameter
     * rather than by anything the reader does. That belongs to the timestamp-rendering work.
     */
    @Test
    public void noOffsetLeavesTheValueAlone() {
        assertEquals("2020-01-01 10:00:00.000",
            outcome("TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_NTZ)"));
        assertEquals("2020-01-01", outcome("TO_VARCHAR('2020-01-01'::DATE)"));
        assertEquals("2020-01-01 10:00:00.000",
            outcome("TO_VARCHAR(TO_TIMESTAMP_NTZ('2020-01-01 10:00:00'))"));
    }
}
