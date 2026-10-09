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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

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
 * <p>The pin is scoped to the thread because the engine runs a session's statements on the caller's thread;
 * what orders statements against each other is process-wide, since one engine serves many sessions on many
 * threads (see {@link #reading} and {@link #claimStart}). The pin is taken by the OUTERMOST statement only. A
 * procedural block's INNER statements each advance
 * the pin to a fresh reading ({@link #advance}) — live gives every statement inside a block its own
 * time — except during write-ahead-log replay, where the whole block keeps its recorded instant.
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
    /** The instant the outermost statement started at, which {@link #advance} leaves alone. */
    private static final ThreadLocal<Instant> STATEMENT = new ThreadLocal<Instant>();
    private static final ThreadLocal<Boolean> REPLAY = new ThreadLocal<Boolean>();
    /** Set once SQL has read the pinned instant of the statement (or of the block's inner statement) running. */
    private static final ThreadLocal<Boolean> READ = new ThreadLocal<Boolean>();
    /** Set once the outermost statement's start was handed out, which fixes it for the rest of the statement. */
    private static final ThreadLocal<Boolean> START_TAKEN = new ThreadLocal<Boolean>();
    /** Set once a procedural block moved the pin off the outermost statement's own instant. */
    private static final ThreadLocal<Boolean> ADVANCED = new ThreadLocal<Boolean>();
    /** The instant of the outermost statement this thread finished last, until it pins the next; see {@link #commitInstant}. */
    private static final ThreadLocal<Instant> FINISHED = new ThreadLocal<Instant>();

    /** The latest millisecond SQL has read on any thread, which every statement started afterwards must follow. */
    private static final AtomicLong LAST_READ_MILLIS = new AtomicLong(Long.MIN_VALUE);
    /** The latest millisecond a table snapshot was stamped with; see {@link #committed}. */
    private static final AtomicLong LAST_COMMIT_MILLIS = new AtomicLong(Long.MIN_VALUE);
    /** The latest statement start handed out, in nanoseconds since the epoch; see {@link #claimStart}. */
    private static final AtomicLong LAST_START_NANOS = new AtomicLong(Long.MIN_VALUE);

    /** How long a repeated reading waits before it reads the clock again. */
    private static final long WAIT_STEP_NANOS = 50_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    /**
     * How many statements can start within one millisecond. A start carries its place in the millisecond below the
     * millisecond, under this bound, which no instant read from the JDK's microsecond clock ever has: that is how
     * {@link #sqlInstant} tells a start this clock drew from an instant recorded before it did.
     */
    private static final long STARTS_PER_MILLI = 1_000L;
    /**
     * How far behind the latest instant read the wall clock may be before a reading stops waiting for it: only a
     * clock set back can be, and a statement must not wait for the time to come round again.
     */
    private static final long MAX_CATCH_UP_MILLIS = 1_000L;

    private StatementClock() {
    }

    /**
     * A fresh statement reading: the wall clock at the account's resolution, milliseconds, and later than the
     * latest instant SQL has read on any thread.
     *
     * <p>The JDK's clock reads microseconds, which a TIMESTAMP_NTZ(3) column cannot hold, so a
     * CURRENT_TIMESTAMP() stored in one no longer equalled the variable it was read into. A procedure that
     * compares exactly that passes on Snowflake, whose CURRENT_TIMESTAMP(9) carries zeros past the third
     * fractional digit.
     *
     * <p>A statement on the account takes at least a millisecond, so a statement never reads the instant one that
     * finished before it read; the engine runs several within one millisecond, where a loader reading the rows
     * stamped after a watermark another session took from CURRENT_TIMESTAMP() would miss them. So when SQL read an
     * instant in this millisecond, on this thread or any other, the next statement waits for the next one. Only
     * then: a statement after statements that read no time does not wait, and the clock never runs ahead of the
     * wall clock, which time travel refuses a point beyond as future data.
     */
    private static long reading() {
        long now = System.currentTimeMillis();
        while (true) {
            final long read = LAST_READ_MILLIS.get();
            if (now > read || read - now > MAX_CATCH_UP_MILLIS) {
                return now;
            }
            LockSupport.parkNanos(WAIT_STEP_NANOS);
            now = System.currentTimeMillis();
        }
    }

    /**
     * A statement start in the given millisecond, unique in the process: the millisecond, and below it the
     * statement's place among the statements that started in it. Whatever a statement derives from its start, the
     * tokens it hands out above all, therefore differs from every other statement's, whichever session ran it.
     * The log records the start, so a replayed statement derives the same. A start follows every start handed out
     * before it, so one claimed after a later millisecond was claimed falls in that later millisecond, which the
     * wall clock has reached; a millisecond whose places are all taken waits for the next.
     *
     * @param millis the statement's reading
     * @return the start, in nanoseconds since the epoch
     */
    private static long claimStart(final long millis) {
        long floor = millis * NANOS_PER_MILLI;
        while (true) {
            final long last = LAST_START_NANOS.get();
            final long next = last < floor ? floor : last + 1;
            if (Math.floorMod(next, NANOS_PER_MILLI) >= STARTS_PER_MILLI) {
                floor = Math.max(floor, (Math.floorDiv(next, NANOS_PER_MILLI) + 1) * NANOS_PER_MILLI);
                while (System.currentTimeMillis() < Math.floorDiv(floor, NANOS_PER_MILLI)) {
                    LockSupport.parkNanos(WAIT_STEP_NANOS);
                }
                continue;
            }
            if (LAST_START_NANOS.compareAndSet(last, next)) {
                return next;
            }
        }
    }

    /** A fresh start for a statement; see {@link #claimStart}. */
    private static Instant freshStart() {
        final long start = claimStart(reading());
        return Instant.ofEpochSecond(Math.floorDiv(start, 1_000_000_000L), Math.floorMod(start, 1_000_000_000L));
    }

    /**
     * The instant SQL reads for a statement that started at {@code start}: a start this clock drew loses its place
     * in the millisecond, which SQL never sees. Any other instant, as a log written before starts carried a place
     * recorded one, is read as it is.
     *
     * @param start the statement's start
     * @return the instant its SQL reads
     */
    static Instant sqlInstant(final Instant start) {
        final long place = Math.floorMod(start.getNano(), NANOS_PER_MILLI);
        return place > 0 && place < STARTS_PER_MILLI ? start.minusNanos(place) : start;
    }

    /** Raise a high-water mark to at least {@code value}. */
    private static void raise(final AtomicLong mark, final long value) {
        long seen = mark.get();
        while (seen < value && !mark.compareAndSet(seen, value)) {
            seen = mark.get();
        }
    }

    /**
     * Move the pinned instant forward to a fresh reading — the boundary between two statements
     * INSIDE an outer one: a procedural block's statements each read their own clock
     * (live-verified). A no-op when nothing is pinned (the fresh-read path already applies) and
     * during write-ahead-log replay, where the outer statement keeps its recorded instant.
     */
    public static void advance() {
        if (PINNED.get() != null && !Boolean.TRUE.equals(REPLAY.get())) {
            PINNED.set(Instant.ofEpochMilli(reading()));
            READ.remove();
            ADVANCED.set(Boolean.TRUE);
        }
    }

    /** Write-ahead-log replay marker: while set, {@link #advance} keeps the recorded instant. */
    public static void setReplay(final boolean replaying) {
        if (replaying) {
            REPLAY.set(Boolean.TRUE);
        } else {
            REPLAY.remove();
        }
    }

    /**
     * Whether the statement running on this thread is the write-ahead log replaying one that already committed
     * ({@link #setReplay}). What such a statement did beyond the catalog and the rows - the stage files it wrote,
     * moved or removed - is on disk as it left them, since the log holds SQL and not files, so the replay does not do
     * it again.
     *
     * @return whether this thread is replaying the log
     */
    public static boolean isReplay() {
        return Boolean.TRUE.equals(REPLAY.get());
    }

    /**
     * The current statement's reading in the session's zone — what CURRENT_TIMESTAMP, LOCALTIMESTAMP,
     * CURRENT_DATE, CURRENT_TIME, GETDATE and NOW answer with. The engine's session zone IS UTC (its
     * TIMEZONE parameter), so this renders the instant in UTC — never the host's zone, whose offset
     * would otherwise leak into every epoch extraction ({@code DATE_PART(EPOCH_SECOND,
     * CURRENT_TIMESTAMP())} answers the true instant on a real account, live-verified). Unpinned, the
     * clock is read afresh so a function called outside any statement still answers.
     */
    public static LocalDateTime now() {
        return LocalDateTime.ofInstant(observed(), ZoneOffset.UTC);
    }

    /** The same instant in UTC — what SYSDATE answers with. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(observed(), ZoneOffset.UTC);
    }

    /**
     * The current statement's instant as SQL reads it: CURRENT_TIMESTAMP and every function that answers the
     * time. Reading it is remembered, so every statement started afterwards, on any thread, takes a later
     * millisecond (see {@link #reading}); the engine's own bookkeeping reads {@link #instant} and is not.
     *
     * <p>The first read of a statement's instant also settles which writes come before it. A table snapshot
     * stamped in the statement's own millisecond (see {@link #committed}) was committed by a statement the clock
     * cannot tell apart from this one, and a later {@code BEFORE(TIMESTAMP => t)} at the instant read here would
     * leave it out although it committed first. So the statement moves to a later millisecond before anything
     * reads its instant, unless replay is restoring a recorded one or the statement already handed out its start.
     *
     * @return the statement's instant, or a fresh reading outside a statement
     */
    public static Instant observed() {
        Instant pinned = PINNED.get();
        if (pinned == null) {
            return Instant.ofEpochMilli(reading());
        }
        final boolean replaying = Boolean.TRUE.equals(REPLAY.get());
        if (!Boolean.TRUE.equals(READ.get())) {
            READ.set(Boolean.TRUE);
            if (!replaying && !Boolean.TRUE.equals(START_TAKEN.get())
                    && pinned.toEpochMilli() <= LAST_COMMIT_MILLIS.get()) {
                pinned = movedPastCommit();
            }
        }
        if (!replaying) {
            raise(LAST_READ_MILLIS, pinned.toEpochMilli());
        }
        return pinned;
    }

    /**
     * Re-pin the statement running in a millisecond after the latest commit; see {@link #observed}. The
     * outermost statement's start moves with it, so the log records the instant its SQL read; an inner statement
     * of a block moves only its own instant.
     */
    private static Instant movedPastCommit() {
        final long commit = LAST_COMMIT_MILLIS.get();
        long millis = reading();
        while (millis <= commit && commit - millis <= MAX_CATCH_UP_MILLIS) {
            LockSupport.parkNanos(WAIT_STEP_NANOS);
            millis = reading();
        }
        if (Boolean.TRUE.equals(ADVANCED.get())) {
            final Instant moved = Instant.ofEpochMilli(millis);
            PINNED.set(moved);
            return moved;
        }
        final long start = claimStart(millis);
        final Instant restart = Instant.ofEpochSecond(Math.floorDiv(start, 1_000_000_000L),
            Math.floorMod(start, 1_000_000_000L));
        STATEMENT.set(restart);
        PINNED.set(sqlInstant(restart));
        return PINNED.get();
    }

    /**
     * Record that a table snapshot was stamped with an instant: the commit it marks comes before every
     * statement that reads the time afterwards (see {@link #observed}).
     *
     * @param stampMillis the snapshot's stamp
     */
    public static void committed(final long stampMillis) {
        raise(LAST_COMMIT_MILLIS, stampMillis);
    }

    /** The pinned instant, or a fresh reading when nothing is pinned. */
    public static Instant instant() {
        final Instant pinned = PINNED.get();
        return pinned != null ? pinned : Instant.ofEpochMilli(reading());
    }

    /**
     * {@link #instant} as the host's wall clock: the form in which streams, tasks, users, warehouses and search
     * services keep their stamps, and from which every reader turns them back into the instant.
     *
     * @return the statement's instant on the host's wall clock
     */
    public static LocalDateTime hostLocal() {
        return LocalDateTime.ofInstant(instant(), ZoneId.systemDefault());
    }

    /**
     * The instant a commit is stamped with: the statement's own. An autocommit statement commits after it returns,
     * once its pin is gone, so the thread keeps the finished statement's instant until it pins the next one; a
     * replay then stamps the commit as the original run did. A thread that has run no statement reads the clock.
     *
     * @return the instant of the statement the commit closes
     */
    public static Instant commitInstant() {
        final Instant pinned = PINNED.get();
        if (pinned != null) {
            return pinned;
        }
        final Instant finished = FINISHED.get();
        return finished != null ? finished : Instant.ofEpochMilli(reading());
    }

    /**
     * Pin this statement's instant, returning what was pinned before so the caller can restore it.
     * A null argument pins a fresh start (see {@link #claimStart}); write-ahead-log replay passes the
     * start the log recorded. Re-entrant callers pass the returned value back to {@link #restore}.
     */
    public static Instant pin(final Instant instant) {
        final Instant previous = PINNED.get();
        final Instant start = instant != null ? instant : freshStart();
        STATEMENT.set(start);
        PINNED.set(sqlInstant(start));
        READ.remove();
        START_TAKEN.remove();
        ADVANCED.remove();
        FINISHED.remove();
        return previous;
    }

    /**
     * The instant the outermost statement started at — what the write-ahead log records for it — or null
     * outside a statement. Unlike {@link #instant}, a procedural block's inner statements do not move it, and it
     * carries the statement's place in its millisecond, which SQL does not see.
     */
    public static Instant statementStart() {
        return STATEMENT.get();
    }

    /**
     * The instant the outermost statement started at, read for something that must differ from one statement to
     * the next: the tokens and the query-id segment a statement derives from it. Every statement's start is its
     * own (see {@link #claimStart}). Handing it out fixes it: the statement no longer moves past a commit when it
     * reads the time (see {@link #observed}), so its replay derives the same tokens.
     *
     * @return the outermost statement's start, or null outside a statement
     */
    public static Instant observedStatementStart() {
        final Instant start = STATEMENT.get();
        if (start != null) {
            START_TAKEN.set(Boolean.TRUE);
        }
        return start;
    }

    /** Whether a statement instant is currently pinned. */
    public static boolean isPinned() {
        return PINNED.get() != null;
    }

    /** Put back whatever {@link #pin} displaced; a null restores the unpinned state. */
    public static void restore(final Instant previous) {
        if (previous == null) {
            final Instant start = STATEMENT.get();
            if (start != null) {
                FINISHED.set(sqlInstant(start));
            }
            PINNED.remove();
            STATEMENT.remove();
        } else {
            PINNED.set(previous);
            STATEMENT.set(previous);
        }
        READ.remove();
        START_TAKEN.remove();
        ADVANCED.remove();
    }
}
