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
import java.util.ArrayList;
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
 * <p>Logical (statement) logging means replay RE-EXECUTES the SQL, so non-deterministic constructs
 * (CURRENT_TIMESTAMP, RANDOM, sequence NEXTVAL) can differ on recovery — acceptable for this engine's
 * local/CI-testing purpose.
 */
public class WriteAheadLog {

    private static final Logger logger = LoggerFactory.getLogger(WriteAheadLog.class);

    private static final int MAGIC = 0x53465731;          // "SFW1"
    private static final byte VERSION = 2;                // v2: typed, per-transaction records + checkpoints
    private static final byte REC_TRANSACTION = 1;
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
        if (statements == null || statements.isEmpty()) {
            return;
        }
        out.writeByte(REC_TRANSACTION);
        out.writeInt(statements.size());
        for (final String sql : statements) {
            writeString(out, sql);
        }
        out.flush();
        fileOut.getFD().sync();
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
            in.readByte();   // version — only v2 exists
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
                for (int i = 0; i < count; i++) {
                    statements.add(readString(in));
                }
                return WalRecord.transaction(statements);
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
                    tmpOut.writeByte(REC_TRANSACTION);
                    tmpOut.writeInt(rec.getStatements().size());
                    for (final String sql : rec.getStatements()) {
                        writeString(tmpOut, sql);
                    }
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
