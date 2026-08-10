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

package dev.frostlake.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The guard that keeps two processes off one {@code jdbc:frostlake:file:} data directory.
 *
 * <p>It is an operating-system lock on {@code frostlake.lock}, not the mere existence of that file.
 * That distinction is the whole point: a lock the OS holds is released when the process ends
 * <em>however</em> it ends — cleanly, killed, or crashed — while a sentinel file outlives the process
 * that made it. With a sentinel, force-quitting a SQL client left the directory permanently
 * unopenable, and the only cure was knowing to delete a file nobody told you about.
 *
 * <p>A leftover file from an older run is therefore harmless now: the lock is taken on it and the run
 * continues. The file still records the holder's process id, but only so a refusal can say who has it.
 */
final class DataDirectoryLock {

    private static final Logger logger = LoggerFactory.getLogger(DataDirectoryLock.class);
    static final String LOCK_FILE_NAME = "frostlake.lock";

    private final Path lockFile;
    private final RandomAccessFile handle;
    private final FileChannel channel;
    private final FileLock lock;

    private DataDirectoryLock(final Path lockFile, final RandomAccessFile handle,
                              final FileChannel channel, final FileLock lock) {
        this.lockFile = lockFile;
        this.handle = handle;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Take the directory, or fail with a message naming whoever holds it.
     *
     * @throws IOException if another live process holds the directory, or the lock cannot be taken
     */
    static DataDirectoryLock acquire(final Path directory) throws IOException {
        final Path lockFile = directory.resolve(LOCK_FILE_NAME);
        final RandomAccessFile handle = new RandomAccessFile(lockFile.toFile(), "rw");
        final FileChannel channel = handle.getChannel();
        final FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (final OverlappingFileLockException alreadyOurs) {
            // This JVM already holds it. Connections sharing a directory share one engine, so reaching
            // here means two engines were opened for one directory — a bug worth failing loudly on.
            close(handle, channel);
            throw new IOException("Data directory " + directory
                + " is already open in this process. Reuse the existing connection instead.");
        } catch (final IOException cannotLock) {
            close(handle, channel);
            throw cannotLock;
        }
        if (lock == null) {
            final String holder = describeHolder(lockFile);
            close(handle, channel);
            throw new IOException("Data directory " + directory
                + " is open in another Frostlake process" + holder
                + ". A directory takes one process at a time — close the other one, or point this"
                + " connection at a different directory.");
        }
        writeOwner(handle);
        return new DataDirectoryLock(lockFile, handle, channel, lock);
    }

    /** Release the directory and tidy the lock file away. */
    void release() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (final IOException e) {
            logger.debug("Could not release the lock on {}", lockFile, e);
        }
        close(handle, channel);
        try {
            Files.deleteIfExists(lockFile);
        } catch (final IOException e) {
            // Harmless: the lock is gone, and a leftover file no longer blocks anything.
            logger.debug("Could not remove {}", lockFile, e);
        }
    }

    /** " (process 12345)" when the holder wrote its id, empty when it is unreadable. */
    private static String describeHolder(final Path lockFile) {
        try {
            final String contents = Files.readString(lockFile, StandardCharsets.UTF_8).trim();
            if (contents.startsWith("pid=")) {
                return " (process " + contents.substring("pid=".length()).trim() + ")";
            }
        } catch (final IOException unreadable) {
            // The holder may not have written yet; the message is just less specific.
        }
        return "";
    }

    private static void writeOwner(final RandomAccessFile handle) throws IOException {
        handle.setLength(0);
        handle.write(("pid=" + ProcessHandle.current().pid() + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8));
        handle.getFD().sync();
    }

    private static void close(final RandomAccessFile handle, final FileChannel channel) {
        try {
            channel.close();
        } catch (final IOException ignored) {
            // Closing the handle below releases the descriptor either way.
        }
        try {
            handle.close();
        } catch (final IOException ignored) {
            // Nothing left to do about it.
        }
    }
}
