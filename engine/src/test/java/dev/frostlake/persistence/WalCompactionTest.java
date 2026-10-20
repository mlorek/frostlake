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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A log compacted to its last checkpoint keeps the records written after the marker as they were written: each
 * statement's instant and context survive the rewrite, as they survive a log reopened by a later engine.
 */
public class WalCompactionTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_compaction_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        final File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (final File file : files) {
                Files.deleteIfExists(file.toPath());
            }
        }
        Files.deleteIfExists(dir);
    }

    @Test
    public void aRecordAfterTheCheckpointSurvivesCompaction() throws IOException {
        final Path path = dir.resolve("wal.log");
        final Instant at = Instant.parse("2024-05-01T10:15:30.123456789Z");
        final WalStatementContext context = new WalStatementContext("run:7", "U1", "R1", Arrays.asList("PUBLIC", "R1"),
            "D", "S", "q-1", Collections.singletonList(new CounterDraw("IDENTITY:T", 42L)));
        final WriteAheadLog log = new WriteAheadLog(path);
        try {
            log.appendTransaction(Collections.singletonList("INSERT INTO t VALUES (1)"));
            log.appendCheckpoint("c1");
            log.appendTransaction(Collections.singletonList("INSERT INTO t VALUES (2)"),
                Collections.singletonList(at), Collections.singletonList(context));
            log.truncateToCheckpoint("c1");
        } finally {
            log.close();
        }
        final WriteAheadLog reopened = new WriteAheadLog(path);
        try {
            final List<WalRecord> records = reopened.readAll();
            assertEquals(2, records.size());
            assertEquals("c1", records.get(0).getCheckpointRef());
            final WalRecord kept = records.get(1);
            assertEquals(Collections.singletonList("INSERT INTO t VALUES (2)"), kept.getStatements());
            assertEquals(at, kept.getStatementInstant(0));
            final WalStatementContext read = kept.getStatementContext(0);
            assertEquals("run:7|U1|R1|[PUBLIC, R1]|D|S|q-1|IDENTITY:T=42", read.getSession() + "|" + read.getUser()
                + "|" + read.getRole() + "|" + read.getActiveRoles() + "|" + read.getDatabase() + "|"
                + read.getSchema() + "|" + read.getQueryId() + "|" + read.getDraws().get(0).getCounter() + "="
                + read.getDraws().get(0).getValue());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void aCounterMarkAfterTheCheckpointSurvivesCompaction() throws IOException {
        final Path path = dir.resolve("wal.log");
        final WriteAheadLog log = new WriteAheadLog(path);
        try {
            log.appendCounters(Collections.singletonList(new CounterMark(CounterMark.SEQUENCE, "D", "S", "Q", 5L)));
            log.appendCheckpoint("c1");
            log.appendCounters(Arrays.asList(new CounterMark(CounterMark.SEQUENCE, "D", "S", "Q", 7L),
                new CounterMark(CounterMark.IDENTITY, "D", "S", "T", -3L)));
            log.truncateToCheckpoint("c1");
        } finally {
            log.close();
        }
        final WriteAheadLog reopened = new WriteAheadLog(path);
        try {
            final List<WalRecord> records = reopened.readAll();
            assertEquals(2, records.size());
            assertEquals(WalRecordType.COUNTERS, records.get(1).getType());
            final StringBuilder marks = new StringBuilder();
            for (final CounterMark mark : records.get(1).getCounterMarks()) {
                marks.append(mark.getKind()).append(':').append(mark.getDatabase()).append('.')
                    .append(mark.getSchema()).append('.').append(mark.getName()).append('=').append(mark.getNext())
                    .append(' ');
            }
            assertEquals("SEQUENCE:D.S.Q=7 IDENTITY:D.S.T=-3 ", marks.toString());
        } finally {
            reopened.close();
        }
    }
}
