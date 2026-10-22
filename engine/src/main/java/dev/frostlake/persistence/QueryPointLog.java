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
import java.io.ByteArrayOutputStream;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * The time-travel points of the statements the write-ahead log holds no record of — a query, a SHOW — kept in a file
 * beside the log, so a restart still knows the query IDs they answered with. Each entry names the logged statements
 * its places are mapped through: the last logged statement visible by each place, whose replay a restart places anew.
 * A checkpoint, whose snapshot keeps every point, empties the file. An entry is written through to the file but not
 * forced to disk, so a crash may lose the last ones; a torn last entry is dropped when the file is read.
 */
public class QueryPointLog {

    private static final int MAGIC = 0x53465150;   // "SFQP"
    private static final byte VERSION = 1;
    private static final int HEADER_BYTES = Integer.BYTES + 1;
    private static final long NO_TIME = Long.MIN_VALUE;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int MAX_STRING_BYTES = 1 << 20;

    private final Path path;
    private DataOutputStream out;

    /**
     * Opens the file at a path for appending, beginning it when there is none.
     *
     * @param path the file
     * @throws IOException when it cannot be opened
     */
    public QueryPointLog(final Path path) throws IOException {
        this.path = path;
        open();
    }

    private void open() throws IOException {
        final boolean needHeader = !Files.exists(path) || Files.size(path) == 0L;
        out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path.toFile(), true)));
        if (needHeader) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.flush();
        }
    }

    /**
     * Appends one statement's point.
     *
     * @param point the point
     * @throws IOException when the file cannot be written
     */
    public synchronized void append(final QueryPointSnapshot point) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream entry = new DataOutputStream(bytes);
        writeString(entry, point.queryId);
        writeString(entry, point.queryType);
        entry.writeLong(nanos(point.startTime));
        entry.writeLong(nanos(point.endTime));
        entry.writeLong(nanos(point.visibleFrom));
        entry.writeLong(point.startSequence);
        entry.writeLong(point.visibleSequence);
        entry.writeBoolean(point.dynamicTableRefresh);
        writeString(entry, point.loggedAs);
        writeString(entry, point.startThrough);
        writeString(entry, point.visibleThrough);
        entry.flush();
        out.write(bytes.toByteArray());
        out.flush();
    }

    /**
     * Every whole entry, oldest first.
     *
     * @return the points
     * @throws IOException when the file cannot be read
     */
    public synchronized List<QueryPointSnapshot> readAll() throws IOException {
        out.flush();
        final List<QueryPointSnapshot> points = new ArrayList<>();
        if (Files.size(path) < HEADER_BYTES) {
            return points;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(path.toFile()))) {
            if (in.readInt() != MAGIC || in.readByte() != VERSION) {
                return points;
            }
            while (true) {
                final QueryPointSnapshot point = readEntry(in);
                if (point == null) {
                    return points;
                }
                points.add(point);
            }
        }
    }

    /**
     * Rewrites the file to hold exactly these entries: what a restart keeps of it, a torn last entry left out.
     *
     * @param points the entries, oldest first
     * @throws IOException when the file cannot be written
     */
    public synchronized void rewrite(final List<QueryPointSnapshot> points) throws IOException {
        out.close();
        final Path tmp = path.resolveSibling(path.getFileName() + ".compact");
        Files.deleteIfExists(tmp);
        try {
            writeFresh(tmp, points);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            open();
        }
    }

    /**
     * The file's length, at which a checkpoint begun now takes every entry before it into its snapshot.
     *
     * @return the length in bytes
     * @throws IOException when the file cannot be read
     */
    public synchronized long mark() throws IOException {
        out.flush();
        return Files.size(path);
    }

    /**
     * Drops the entries before a mark, which a checkpoint's snapshot keeps; those appended since stay.
     *
     * @param mark a length {@link #mark} answered
     * @throws IOException when the file cannot be rewritten
     */
    public synchronized void dropBefore(final long mark) throws IOException {
        out.flush();
        final byte[] all = Files.readAllBytes(path);
        final int from = (int) Math.max(HEADER_BYTES, Math.min(mark, all.length));
        out.close();
        final Path tmp = path.resolveSibling(path.getFileName() + ".compact");
        try (FileOutputStream tmpOut = new FileOutputStream(tmp.toFile(), false);
             DataOutputStream header = new DataOutputStream(tmpOut)) {
            header.writeInt(MAGIC);
            header.writeByte(VERSION);
            header.write(all, from, all.length - from);
            header.flush();
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            open();
        }
    }

    /** Closes the file. */
    public synchronized void close() {
        try {
            out.close();
        } catch (final IOException ignored) {
            // best-effort on shutdown
        }
    }

    private static void writeFresh(final Path tmp, final List<QueryPointSnapshot> points) throws IOException {
        final QueryPointLog fresh = new QueryPointLog(tmp);
        try {
            for (final QueryPointSnapshot point : points) {
                fresh.append(point);
            }
        } finally {
            fresh.close();
        }
    }

    private static QueryPointSnapshot readEntry(final DataInputStream in) throws IOException {
        final int first = in.read();
        if (first < 0) {
            return null;
        }
        try {
            final QueryPointSnapshot point = new QueryPointSnapshot();
            point.queryId = readString(in, first);
            point.queryType = readString(in, in.read());
            point.startTime = time(in.readLong());
            point.endTime = time(in.readLong());
            point.visibleFrom = time(in.readLong());
            point.startSequence = in.readLong();
            point.visibleSequence = in.readLong();
            point.dynamicTableRefresh = in.readBoolean();
            point.loggedAs = readString(in, in.read());
            point.startThrough = readString(in, in.read());
            point.visibleThrough = readString(in, in.read());
            return point.queryId == null ? null : point;
        } catch (final EOFException torn) {
            return null;
        }
    }

    /** A text as an entry holds it: a mark of whether there is one, then its length and its bytes. */
    private static void writeString(final DataOutputStream o, final String s) throws IOException {
        o.writeBoolean(s != null);
        if (s != null) {
            final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            o.writeInt(bytes.length);
            o.write(bytes);
        }
    }

    /** A text whose presence mark has been read already. */
    private static String readString(final DataInputStream in, final int present) throws IOException {
        if (present < 0) {
            throw new EOFException("torn entry");
        }
        if (present == 0) {
            return null;
        }
        final int len = in.readInt();
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw new EOFException("implausible length " + len + " (torn entry)");
        }
        final byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** A UTC wall clock as epoch nanoseconds, or the mark of none. */
    private static long nanos(final LocalDateTime time) {
        return time == null ? NO_TIME : time.toEpochSecond(ZoneOffset.UTC) * NANOS_PER_SECOND + time.getNano();
    }

    private static LocalDateTime time(final long nanos) {
        return nanos == NO_TIME ? null : LocalDateTime.ofEpochSecond(Math.floorDiv(nanos, NANOS_PER_SECOND),
            (int) Math.floorMod(nanos, NANOS_PER_SECOND), ZoneOffset.UTC);
    }
}
