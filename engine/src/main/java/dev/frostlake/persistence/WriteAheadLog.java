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

package dev.frostlake.persistence;

import dev.frostlake.metastore.CounterDraw;
import dev.frostlake.storage.RecordedResult;
import dev.frostlake.transaction.WalStatementContext;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only write-ahead log — a logical (redo) log of committed transactions. Each committed transaction
 * is appended as ONE record (its mutating statements) and fsync'd before COMMIT returns, so a committed
 * transaction is durable atomically: a crash mid-append leaves a torn trailing record that replay discards,
 * so a transaction is recovered only when its whole record reached disk. On startup the engine replays the
 * records to rebuild state. This covers both autocommit statements (a one-statement record) and explicit
 * {@code BEGIN…COMMIT} blocks (a multi-statement record); a rolled-back transaction is never written.
 *
 * <p>A {@link WalRecordType#CHECKPOINT} marker records that a full state snapshot is durable; every record
 * before the last marker folds into that snapshot, so recovery loads the snapshot and replays only the
 * records after it, and the log can be {@link #truncateToCheckpoint truncated} to the marker to bound its
 * growth. The marker is the atomic pivot: a crash before it leaves an ignored orphan snapshot (no
 * double-apply), a crash after it recovers from the snapshot. See {@code docs/acid-snowflake-plan.md}.
 *
 * <p>Logical (statement) logging means replay RE-EXECUTES the SQL. What a statement computes at run time is put
 * back where the log can carry it: each statement's instant, the session it ran in and that session's context,
 * variables and parameters (see {@link WalStatementContext}), the values it drew from IDENTITY columns and
 * sequences, the stream its RANDOM, UUID_STRING and SAMPLE draw from, which the instant seeds, the rows it read by
 * RESULT_SCAN and from the query-history listings, and what CURRENT_SESSION, CURRENT_TRANSACTION and LAST_QUERY_ID
 * answered. A transaction's record is appended when it commits, after what other sessions committed while it was
 * open; each of its statements keeps how many records the log took between the statement and the commit, and the
 * replay runs it in that place, so it reads what it read when it first ran. Work a statement commits in a session of
 * its own — a task's run, an alert's evaluation, a pipe's load — is no part of its transaction: a statement a block
 * or a procedure runs to start it is logged at once in a record of its own, and the enclosing statement's replay does
 * not run it again.
 *
 * <p>A {@link WalRecordType#COUNTERS} record keeps where the sequences and IDENTITY columns a statement drew from
 * stood when it ended, for a statement whose draws no committed record may carry: one inside a transaction, which a
 * shutdown or a crash ends without a record, and one the log keeps no record of at all. The replay moves each counter
 * at least that far, so no value a client saw is handed out again.
 */
public class WriteAheadLog {

    private static final Logger logger = LoggerFactory.getLogger(WriteAheadLog.class);

    private static final int MAGIC = 0x53465731;          // "SFW1"
    private static final byte VERSION = 9;                // v9: v8 + the precisions of time and timestamp variables
    private static final byte VERSION_WITH_DECFLOATS = 8;    // still readable; its temporal variables replay at 9
    private static final byte VERSION_WITH_FIRST_START = 7;   // still readable; its DECFLOATs replay as NUMBERs
    private static final byte VERSION_WITH_ANSWERS = 6;   // still readable; its first start is its first record's
    private static final byte VERSION_WITH_STATE = 5;     // still readable; its identities answer as they do now
    private static final byte VERSION_WITH_CONTEXT = 4;   // still readable; its statements replay where logged
    private static final byte VERSION_WITH_INSTANT = 3;   // still readable; its records replay in one session
    private static final byte VERSION_WITHOUT_INSTANT = 2;   // still readable; those records replay at the wall clock
    private static final byte REC_TRANSACTION = 1;
    /** Sentinel for "this record recorded no instant" — replay falls back to the wall clock. */
    private static final long NO_INSTANT = Long.MIN_VALUE;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    /** False while reading a v2 log, whose transaction records have no instant field. */
    private boolean readsInstants = true;
    /** False while reading a log before v4, whose statements carry no context. */
    private boolean readsContexts = true;
    /** False while reading a log before v5, whose contexts carry no session state, results or place. */
    private boolean readsStatementState = true;
    /** False while reading a log before v6, whose contexts carry no identity answers. */
    private boolean readsAnswers = true;
    /** False while reading a log before v8, whose contexts name no variable a SET gave a DECFLOAT. */
    private boolean readsDecfloatVariables = true;
    /** False while reading a log before v9, whose contexts keep no precision of a time or a timestamp variable. */
    private boolean readsVariablePrecisions = true;
    private static final byte REC_CHECKPOINT = 2;
    /** Where counters stood once a statement drew from them (see {@link CounterMark}). */
    private static final byte REC_COUNTERS = 3;
    private static final int MAX_STRING_BYTES = 1 << 28;  // 256 MB sanity cap — a larger length means a torn record

    private final Path path;
    private FileOutputStream fileOut;
    private DataOutputStream out;
    /**
     * When the engine that began the log first started, which its header keeps: the instant the engine's built-in
     * objects were created at, which a restart with no checkpoint to read stamps them with again.
     */
    private Instant firstStart;

    public WriteAheadLog(final Path path) throws IOException {
        this(path, Instant.now());
    }

    /**
     * Opens the log at a path, beginning it when there is none.
     *
     * @param path        the log's file
     * @param engineStart the opening engine's start, which a log begun now keeps as its first start
     * @throws IOException when the log cannot be read or written
     */
    public WriteAheadLog(final Path path, final Instant engineStart) throws IOException {
        this.path = path;
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        final Instant written = writtenFirstStart();
        this.firstStart = written != null ? written : engineStart;
        openForAppend();
        // A log of an earlier version is rewritten in this one, so the records appended to it read as written. Its
        // first start, which it did not keep, is taken from its first record's instant, the earliest it tells.
        if (writtenVersion() < VERSION) {
            final List<WalRecord> records = readAll();
            if (written == null) {
                firstStart = firstRecordInstant(records, engineStart);
            }
            rewrite(records);
        }
    }

    /**
     * When the engine that began the log first started: what a restart with no checkpoint stamps the built-in objects
     * with.
     *
     * @return the instant
     */
    public synchronized Instant firstStart() {
        return firstStart;
    }

    /** The first start a header from v7 on keeps, or null for a log with no such header. */
    private Instant writtenFirstStart() throws IOException {
        if (!Files.exists(path) || Files.size(path) < Integer.BYTES + 1L + Long.BYTES) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(path.toFile()))) {
            if (in.readInt() != MAGIC || in.readByte() < VERSION_WITH_FIRST_START) {
                return null;
            }
            return instantFrom(in.readLong());
        }
    }

    /** The earliest instant an older log's records tell, or the fallback when none tells one. */
    private static Instant firstRecordInstant(final List<WalRecord> records, final Instant fallback) {
        for (final WalRecord rec : records) {
            if (rec.getType() == WalRecordType.TRANSACTION && rec.getStatementInstant(0) != null) {
                return rec.getStatementInstant(0);
            }
        }
        return fallback;
    }

    /** The version the log's header names, the current one for a log with no header yet. */
    private byte writtenVersion() throws IOException {
        if (!Files.exists(path) || Files.size(path) < Integer.BYTES + 1L) {
            return VERSION;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(path.toFile()))) {
            return in.readInt() == MAGIC ? in.readByte() : VERSION;
        }
    }

    private void openForAppend() throws IOException {
        final boolean needHeader = !Files.exists(path) || Files.size(path) == 0L;
        this.fileOut = new FileOutputStream(path.toFile(), true);   // append, never truncate
        this.out = new DataOutputStream(new BufferedOutputStream(fileOut));
        if (needHeader) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeLong(toNanos(firstStart));
            out.flush();
            fileOut.getFD().sync();
        }
    }

    /**
     * Append one committed transaction's statements as a single record and force it to disk — the fsync is
     * the durability point, and writing the whole record under one fsync makes the transaction atomic on
     * recovery. No-op for a null/empty statement list (a read-only transaction logs nothing).
     */
    public synchronized void appendTransaction(final List<String> statements) throws IOException {
        appendTransaction(statements, Collections.<Instant>emptyList());
    }

    /**
     * As {@link #appendTransaction(List)}, recording what these statements called "now" so replay can
     * put it back instead of resolving CURRENT_TIMESTAMP to the recovery time.
     */
    public synchronized void appendTransaction(final List<String> statements,
                                               final List<Instant> statementInstants)
            throws IOException {
        appendTransaction(statements, statementInstants, Collections.<WalStatementContext>emptyList());
    }

    /**
     * As {@link #appendTransaction(List, List)}, recording what each statement ran under so replay can put it back
     * (see {@link WalStatementContext}).
     */
    public synchronized void appendTransaction(final List<String> statements,
                                               final List<Instant> statementInstants,
                                               final List<WalStatementContext> statementContexts)
            throws IOException {
        if (statements == null || statements.isEmpty()) {
            return;
        }
        writeTransaction(out, WalRecord.transaction(statements,
            statementInstants == null ? Collections.<Instant>emptyList() : statementInstants,
            statementContexts == null ? Collections.<WalStatementContext>emptyList() : statementContexts));
        out.flush();
        fileOut.getFD().sync();
    }

    /** One transaction record in the current version's layout: per statement its instant, context and text. */
    private static void writeTransaction(final DataOutputStream o, final WalRecord rec) throws IOException {
        o.writeByte(REC_TRANSACTION);
        o.writeInt(rec.getStatements().size());
        for (int i = 0; i < rec.getStatements().size(); i++) {
            final Instant instant = rec.getStatementInstant(i);
            o.writeLong(instant == null ? NO_INSTANT : toNanos(instant));
            writeContext(o, rec.getStatementContext(i));
            writeString(o, rec.getStatements().get(i));
        }
    }

    private static void writeContext(final DataOutputStream o, final WalStatementContext context) throws IOException {
        o.writeBoolean(context != null);
        if (context == null) {
            return;
        }
        writeNullable(o, context.getSession());
        writeNullable(o, context.getUser());
        writeNullable(o, context.getRole());
        o.writeInt(context.getActiveRoles().size());
        for (final String active : context.getActiveRoles()) {
            writeString(o, active);
        }
        writeNullable(o, context.getDatabase());
        writeNullable(o, context.getSchema());
        writeNullable(o, context.getQueryId());
        o.writeInt(context.getDraws().size());
        for (final CounterDraw draw : context.getDraws()) {
            writeString(o, draw.getCounter());
            o.writeLong(draw.getValue());
        }
        o.writeInt(context.getLag());
        writeState(o, context.getVariables(), context.getUnkeptVariables());
        writeState(o, context.getParameters(), context.getUnkeptParameters());
        final List<RecordedResult> results = context.getResults();
        o.writeInt(results.size());
        for (final RecordedResult result : results) {
            writeValue(o, result == null ? null : WalValues.bytesOf(result));
        }
        final Map<String, List<String>> answers = context.getAnswers();
        o.writeInt(answers.size());
        for (final Map.Entry<String, List<String>> identity : answers.entrySet()) {
            writeString(o, identity.getKey());
            o.writeInt(identity.getValue().size());
            for (final String value : identity.getValue()) {
                writeNullable(o, value);
            }
        }
        o.writeBoolean(context.isRunsLoggedApart());
        final Set<String> decfloats = context.getDecfloatVariables();
        o.writeBoolean(decfloats != null);
        if (decfloats != null) {
            o.writeInt(decfloats.size());
            for (final String name : decfloats) {
                writeString(o, name);
            }
        }
        final Map<String, Integer> precisions = context.getVariablePrecisions();
        o.writeBoolean(precisions != null);
        if (precisions != null) {
            o.writeInt(precisions.size());
            for (final Map.Entry<String, Integer> precision : precisions.entrySet()) {
                writeString(o, precision.getKey());
                o.writeInt(precision.getValue().intValue());
            }
        }
    }

    /**
     * A session's variables or parameters, each by name with its value, or as one the log cannot keep: a value
     * serialization does not write, or one the context itself could not keep.
     */
    private static void writeState(final DataOutputStream o, final Map<String, Object> values,
                                   final Set<String> unkept) throws IOException {
        o.writeBoolean(values != null);
        if (values == null) {
            return;
        }
        o.writeInt(values.size() + unkept.size());
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            writeString(o, entry.getKey());
            writeValue(o, WalValues.bytesOf(entry.getValue()));
        }
        for (final String name : unkept) {
            writeString(o, name);
            writeValue(o, null);
        }
    }

    /** A serialized value, or the mark of one not kept. */
    private static void writeValue(final DataOutputStream o, final byte[] bytes) throws IOException {
        o.writeBoolean(bytes != null);
        if (bytes != null) {
            o.writeInt(bytes.length);
            o.write(bytes);
        }
    }

    /** A serialized value's bytes, or null for one not kept. */
    private static byte[] readValue(final DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        final byte[] bytes = new byte[readCount(in)];
        in.readFully(bytes);
        return bytes;
    }

    private static WalStatementContext readContext(final DataInputStream in, final boolean withState,
                                                   final boolean withAnswers, final boolean withDecfloats,
                                                   final boolean withPrecisions)
            throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        final String session = readNullable(in);
        final String user = readNullable(in);
        final String role = readNullable(in);
        final List<String> activeRoles = new ArrayList<>();
        final int roles = readCount(in);
        for (int i = 0; i < roles; i++) {
            activeRoles.add(readString(in));
        }
        final String database = readNullable(in);
        final String schema = readNullable(in);
        final String queryId = readNullable(in);
        final List<CounterDraw> draws = new ArrayList<>();
        final int drawn = readCount(in);
        for (int i = 0; i < drawn; i++) {
            final String counter = readString(in);
            draws.add(new CounterDraw(counter, in.readLong()));
        }
        if (!withState) {
            return new WalStatementContext(session, user, role, activeRoles, database, schema, queryId, draws);
        }
        final int lag = in.readInt();
        final Set<String> unkeptVariables = new HashSet<>();
        final Map<String, Object> variables = readState(in, new LinkedHashMap<String, Object>(), unkeptVariables);
        final Set<String> unkeptParameters = new HashSet<>();
        final Map<String, Object> parameters = readState(in, new HashMap<String, Object>(), unkeptParameters);
        final List<RecordedResult> results = new ArrayList<>();
        final int read = readCount(in);
        for (int i = 0; i < read; i++) {
            final byte[] bytes = readValue(in);
            results.add(bytes == null ? null : recordedResult(bytes));
        }
        final Map<String, List<String>> answers = new LinkedHashMap<>();
        boolean runsLoggedApart = false;
        if (withAnswers) {
            final int identities = readCount(in);
            for (int i = 0; i < identities; i++) {
                final String identity = readString(in);
                final List<String> values = new ArrayList<>();
                final int answered = readCount(in);
                for (int j = 0; j < answered; j++) {
                    values.add(readNullable(in));
                }
                answers.put(identity, values);
            }
            runsLoggedApart = in.readBoolean();
        }
        Set<String> decfloats = null;
        if (withDecfloats && in.readBoolean()) {
            decfloats = new HashSet<>();
            final int named = readCount(in);
            for (int i = 0; i < named; i++) {
                decfloats.add(readString(in));
            }
        }
        Map<String, Integer> precisions = null;
        if (withPrecisions && in.readBoolean()) {
            precisions = new HashMap<>();
            final int kept = readCount(in);
            for (int i = 0; i < kept; i++) {
                final String name = readString(in);
                precisions.put(name, Integer.valueOf(in.readInt()));
            }
        }
        return new WalStatementContext(session, user, role, activeRoles, database, schema, queryId, draws,
            variables, parameters, unkeptVariables, unkeptParameters, results, answers, runsLoggedApart, -1L, lag)
            .withDecfloatVariables(decfloats).withVariablePrecisions(precisions);
    }

    /** A session's variables or parameters, into {@code into}, the names of those not kept into {@code unkept}. */
    private static Map<String, Object> readState(final DataInputStream in, final Map<String, Object> into,
                                                 final Set<String> unkept) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        final int count = readCount(in);
        for (int i = 0; i < count; i++) {
            final String name = readString(in);
            final byte[] bytes = readValue(in);
            if (bytes == null) {
                unkept.add(name);
                continue;
            }
            try {
                into.put(name, WalValues.valueOf(bytes));
            } catch (final IOException unreadable) {
                logger.debug("A write-ahead log value of {} does not read back: {}", name, unreadable.getMessage());
                unkept.add(name);
            }
        }
        return into;
    }

    /** A result a statement read by RESULT_SCAN, or null when it does not read back. */
    private static RecordedResult recordedResult(final byte[] bytes) {
        try {
            final Object value = WalValues.valueOf(bytes);
            return value instanceof RecordedResult ? (RecordedResult) value : null;
        } catch (final IOException unreadable) {
            logger.debug("A write-ahead log result does not read back: {}", unreadable.getMessage());
            return null;
        }
    }

    private static void writeNullable(final DataOutputStream o, final String s) throws IOException {
        o.writeBoolean(s != null);
        if (s != null) {
            writeString(o, s);
        }
    }

    private static String readNullable(final DataInputStream in) throws IOException {
        return in.readBoolean() ? readString(in) : null;
    }

    private static int readCount(final DataInputStream in) throws IOException {
        final int count = in.readInt();
        if (count < 0 || count > MAX_STRING_BYTES) {
            throw new EOFException("implausible count " + count + " (torn record)");
        }
        return count;
    }

    /**
     * Append where counters stood once a statement drew from them, and force it to disk before the statement's
     * client sees what it drew. No-op for an empty list.
     *
     * @param marks the counters' marks
     * @throws IOException when the log cannot be written
     */
    public synchronized void appendCounters(final List<CounterMark> marks) throws IOException {
        if (marks == null || marks.isEmpty()) {
            return;
        }
        writeCounters(out, marks);
        out.flush();
        fileOut.getFD().sync();
    }

    private static void writeCounters(final DataOutputStream o, final List<CounterMark> marks) throws IOException {
        o.writeByte(REC_COUNTERS);
        o.writeInt(marks.size());
        for (final CounterMark mark : marks) {
            writeString(o, mark.getKind());
            writeString(o, mark.getDatabase());
            writeString(o, mark.getSchema());
            writeString(o, mark.getName());
            o.writeLong(mark.getNext());
        }
    }

    /** Append a checkpoint marker referencing a durable state snapshot, and force it to disk. */
    public synchronized void appendCheckpoint(final String checkpointRef) throws IOException {
        out.writeByte(REC_CHECKPOINT);
        writeString(out, checkpointRef);
        out.flush();
        fileOut.getFD().sync();
    }

    /**
     * All records in order (for replay); empty if the log doesn't exist yet or has only the header. A torn
     * trailing record left by a crash mid-append is discarded, so only fully-written, fsync'd records are
     * returned.
     */
    public synchronized List<WalRecord> readAll() throws IOException {
        final List<WalRecord> records = new ArrayList<>();
        if (!Files.exists(path) || Files.size(path) == 0L) {
            return records;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(path.toFile()))) {
            final int magic = in.readInt();
            if (magic != MAGIC) {
                logger.warn("Write-ahead log at {} has an unrecognized header; ignoring it", path);
                return new ArrayList<>();
            }
            // v2 records carry no instant; v3 onwards prefix each statement with one, v4 with its context, v5
            // adds the session state, results and place to the context, and v6 its identities' answers and whether
            // its runs went to records of their own. A v7 header goes on with the engine's first start, v8 names
            // the variables a SET gave a DECFLOAT, and v9 keeps the precision a SET gave each time or timestamp
            // variable.
            final byte version = in.readByte();
            readsInstants = version >= VERSION_WITH_INSTANT;
            readsContexts = version >= VERSION_WITH_CONTEXT;
            readsStatementState = version >= VERSION_WITH_STATE;
            readsAnswers = version >= VERSION_WITH_ANSWERS;
            readsDecfloatVariables = version >= VERSION_WITH_DECFLOATS;
            readsVariablePrecisions = version >= VERSION;
            if (version >= VERSION_WITH_FIRST_START) {
                try {
                    in.readLong();
                } catch (final EOFException torn) {
                    return records;   // a header torn before its first start: nothing was logged after it
                }
            }
            while (true) {
                final WalRecord rec = readRecord(in);
                if (rec == null) {
                    break;   // clean end of log, or torn trailing record discarded
                }
                records.add(rec);
            }
        }
        return records;
    }

    /** Epoch nanoseconds, the form the log stores an instant in. */
    private static long toNanos(final Instant instant) {
        return instant.getEpochSecond() * NANOS_PER_SECOND + instant.getNano();
    }

    /** Reconstruct a recorded instant, or null for a v2 record that carried none. */
    private static Instant instantFrom(final long nanos) {
        if (nanos == NO_INSTANT) {
            return null;
        }
        return Instant.ofEpochSecond(Math.floorDiv(nanos, NANOS_PER_SECOND),
                                    Math.floorMod(nanos, NANOS_PER_SECOND));
    }

    private WalRecord readRecord(final DataInputStream in) throws IOException {
        final int type = in.read();   // -1 at a clean end of file
        if (type < 0) {
            return null;
        }
        try {
            if (type == REC_TRANSACTION) {
                final int count = in.readInt();
                if (count < 0 || count > MAX_STRING_BYTES) {
                    throw new EOFException("implausible statement count " + count + " (torn record)");
                }
                final List<String> statements = new ArrayList<>(count);
                final List<Instant> instants = new ArrayList<>(count);
                final List<WalStatementContext> contexts = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    instants.add(readsInstants ? instantFrom(in.readLong()) : null);
                    contexts.add(readsContexts
                        ? readContext(in, readsStatementState, readsAnswers, readsDecfloatVariables,
                            readsVariablePrecisions) : null);
                    statements.add(readString(in));
                }
                return WalRecord.transaction(statements, instants, contexts);
            } else if (type == REC_CHECKPOINT) {
                return WalRecord.checkpoint(readString(in));
            } else if (type == REC_COUNTERS) {
                final int count = readCount(in);
                final List<CounterMark> marks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    marks.add(new CounterMark(readString(in), readString(in), readString(in), readString(in),
                        in.readLong()));
                }
                return WalRecord.counters(marks);
            } else {
                logger.warn("Unknown write-ahead log record type {}; stopping replay here", type);
                return null;
            }
        } catch (final EOFException eof) {
            // Torn trailing record (crash mid-append): it was never fsync'd whole — discard it.
            logger.debug("Discarding a torn trailing write-ahead log record");
            return null;
        }
    }

    private static void writeString(final DataOutputStream o, final String s) throws IOException {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        o.writeInt(bytes.length);
        o.write(bytes);
    }

    private static String readString(final DataInputStream in) throws IOException {
        final int len = in.readInt();
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw new EOFException("implausible string length " + len + " (torn record)");
        }
        final byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Rewrite the log keeping only the records from the given checkpoint marker onward (inclusive), bounding
     * the log's growth after a checkpoint. The rewrite goes through a temp file + atomic rename so a crash
     * can't leave a half-written log. No-op if the marker isn't found.
     */
    public synchronized void truncateToCheckpoint(final String checkpointRef) throws IOException {
        final List<WalRecord> all = readAll();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            final WalRecord rec = all.get(i);
            if (rec.getType() == WalRecordType.CHECKPOINT && checkpointRef.equals(rec.getCheckpointRef())) {
                idx = i;
            }
        }
        if (idx < 0) {
            return;   // marker not found (shouldn't happen) — leave the log intact
        }
        rewrite(all.subList(idx, all.size()));
    }

    private void rewrite(final List<WalRecord> keep) throws IOException {
        out.close();   // close the current append stream before replacing the file underneath it
        final Path tmp = path.resolveSibling(path.getFileName() + ".compact");
        try (FileOutputStream tmpFileOut = new FileOutputStream(tmp.toFile(), false);
             DataOutputStream tmpOut = new DataOutputStream(new BufferedOutputStream(tmpFileOut))) {
            tmpOut.writeInt(MAGIC);
            tmpOut.writeByte(VERSION);
            tmpOut.writeLong(toNanos(firstStart));
            for (final WalRecord rec : keep) {
                if (rec.getType() == WalRecordType.TRANSACTION) {
                    // In this version's layout, which the header names: the instants and contexts kept too.
                    writeTransaction(tmpOut, rec);
                } else if (rec.getType() == WalRecordType.COUNTERS) {
                    writeCounters(tmpOut, rec.getCounterMarks());
                } else {
                    tmpOut.writeByte(REC_CHECKPOINT);
                    writeString(tmpOut, rec.getCheckpointRef());
                }
            }
            tmpOut.flush();
            tmpFileOut.getFD().sync();
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        openForAppend();   // reopen the (now-compacted) log for subsequent appends
    }

    public synchronized void close() {
        try {
            out.close();
        } catch (final IOException ignored) {
            // best-effort on shutdown
        }
    }
}
