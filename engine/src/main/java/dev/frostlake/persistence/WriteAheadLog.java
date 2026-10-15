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
import java.util.List;

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
 * back where the log can carry it: each statement's instant, the session it ran in and that session's context
 * (see {@link WalStatementContext}), the values it drew from IDENTITY columns and sequences, and the stream its
 * RANDOM, UUID_STRING and SAMPLE draw from, which the instant seeds. What a statement READ is read again: a
 * transaction that committed after another session's writes reads them on replay.
 */
public class WriteAheadLog {

    private static final Logger logger = LoggerFactory.getLogger(WriteAheadLog.class);

    private static final int MAGIC = 0x53465731;          // "SFW1"
    private static final byte VERSION = 4;                // v4: v3 + what each statement ran under
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
    private static final byte REC_CHECKPOINT = 2;
    private static final int MAX_STRING_BYTES = 1 << 28;  // 256 MB sanity cap — a larger length means a torn record

    private final Path path;
    private FileOutputStream fileOut;
    private DataOutputStream out;

    public WriteAheadLog(final Path path) throws IOException {
        this.path = path;
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        openForAppend();
        // A log of an earlier version is rewritten in this one, so the records appended to it read as written.
        if (writtenVersion() < VERSION) {
            rewrite(readAll());
        }
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
    }

    private static WalStatementContext readContext(final DataInputStream in) throws IOException {
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
        return new WalStatementContext(session, user, role, activeRoles, database, schema, queryId, draws);
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
            // v2 records carry no instant; v3 onwards prefix each statement with one, and v4 with its context.
            final byte version = in.readByte();
            readsInstants = version >= VERSION_WITH_INSTANT;
            readsContexts = version >= VERSION;
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
                    contexts.add(readsContexts ? readContext(in) : null);
                    statements.add(readString(in));
                }
                return WalRecord.transaction(statements, instants, contexts);
            } else if (type == REC_CHECKPOINT) {
                return WalRecord.checkpoint(readString(in));
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
            for (final WalRecord rec : keep) {
                if (rec.getType() == WalRecordType.TRANSACTION) {
                    // In this version's layout, which the header names: the instants and contexts kept too.
                    writeTransaction(tmpOut, rec);
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
