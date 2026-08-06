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

package dev.frostlake.executor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The one instant a statement calls "now".
 *
 * <p>Snowflake reads its clock once per STATEMENT, not once per row and not once per transaction —
 * live-verified: an INSERT selecting {@code CURRENT_TIMESTAMP()} over five generated rows stores one
 * distinct value, {@code CURRENT_TIMESTAMP() = CURRENT_TIMESTAMP()} within a statement is TRUE, and
 * two statements inside one BEGIN…COMMIT get DIFFERENT times. Reading the wall clock per call, as the
 * temporal functions used to, produced a different value for every row.
 *
 * <p>Pinning the instant also makes the write-ahead log replayable: the log stores SQL text and
 * recovery re-executes it, so a per-call clock made {@code CURRENT_TIMESTAMP} resolve to the RECOVERY
 * time rather than the original. The log records the pinned instant and {@link #pin} puts it back, so
 * a replayed row keeps the value it was written with.
 *
 * <p>Scoped to the thread because the engine runs a session's statements on the caller's thread; the
 * pin is taken by the OUTERMOST statement only, so a procedural block's body sees the block's instant
 * rather than each inner statement re-reading the clock.
 *
 * <p>What is pinned is an INSTANT, not a wall-clock reading, because one instant has two renderings
 * and Snowflake uses both: CURRENT_TIMESTAMP and LOCALTIMESTAMP answer in the session's zone while
 * SYSDATE answers in UTC. Live-verified with the session on America/Los_Angeles —
 * {@code SYSDATE()} = {@code 2026-08-07 13:57:23.484} against
 * {@code CURRENT_TIMESTAMP()} = {@code 2026-08-07 06:57:23.484 -0700}, the same instant seven hours
 * apart, so {@code SYSDATE() = CURRENT_TIMESTAMP()} is FALSE; set the session to UTC and it is TRUE.
 * SYSDATE is also typed TIMESTAMP_NTZ where CURRENT_TIMESTAMP is TIMESTAMP_LTZ.
 */
public final class StatementClock {

    private static final ThreadLocal<Instant> PINNED = new ThreadLocal<Instant>();

    private StatementClock() {
    }

    /**
     * The current statement's reading in the session's zone — what CURRENT_TIMESTAMP, LOCALTIMESTAMP,
     * CURRENT_DATE, CURRENT_TIME, GETDATE and NOW answer with. Unpinned, the clock is read afresh so a
     * function called outside any statement still answers.
     */
    public static LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), ZoneId.systemDefault());
    }

    /** The same instant in UTC — what SYSDATE answers with. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(instant(), ZoneOffset.UTC);
    }

    /** The pinned instant, or a fresh reading when nothing is pinned. */
    public static Instant instant() {
        final Instant pinned = PINNED.get();
        return pinned != null ? pinned : Instant.now();
    }

    /**
     * Pin this statement's instant, returning what was pinned before so the caller can restore it.
     * A null argument pins the wall clock. Re-entrant callers pass the returned value back to
     * {@link #restore}.
     */
    public static Instant pin(final Instant instant) {
        final Instant previous = PINNED.get();
        PINNED.set(instant != null ? instant : Instant.now());
        return previous;
    }

    /** Whether a statement instant is currently pinned. */
    public static boolean isPinned() {
        return PINNED.get() != null;
    }

    /** Put back whatever {@link #pin} displaced; a null restores the unpinned state. */
    public static void restore(final Instant previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }
}
