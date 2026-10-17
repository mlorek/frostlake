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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table's keys come back from an engine copy and from a checkpoint as they were declared: the primary key in its own
 * order with its own RELY, each UNIQUE with its own, and a column's own UNIQUE beside a table-level one over it.
 */
public class KeysAcrossRestartTest {

    @TempDir
    Path dir;

    private static final String[] READS = {
        "SELECT GET_DDL('TABLE', 'd.PUBLIC.k')",
        "SELECT CONSTRAINT_TYPE, RELY FROM d.INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'K' ORDER BY 1, 2",
    };

    /** A statement's rows on one line, "a|b ;; c|d". */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private static DatabaseEngine source() {
        final DatabaseEngine engine = new DatabaseEngine();
        engine.executeQuery("CREATE DATABASE d");
        engine.executeQuery("CREATE TABLE d.PUBLIC.k (a INT UNIQUE RELY, b INT, c INT, PRIMARY KEY (c, b) RELY, "
            + "UNIQUE (a, b), UNIQUE (b) RELY)");
        return engine;
    }

    private static void requireKeys(final DatabaseEngine source, final DatabaseEngine copy) {
        assertEquals("create or replace TABLE K (\n\tA NUMBER(38,0),\n\tB NUMBER(38,0) NOT NULL,\n\tC NUMBER(38,0) NOT "
            + "NULL,\n\tunique (A) rely ,\n\tunique (A, B),\n\tunique (B) rely ,\n\tprimary key (C, B) rely \n);",
            answer(source, READS[0]));
        for (final String read : READS) {
            assertEquals(answer(source, read), answer(copy, read), read);
        }
    }

    @Test
    public void anEngineCopyKeepsEachKey() throws Exception {
        final DatabaseEngine source = source();
        final DatabaseEngine copy = source.cloneInstance();
        try {
            requireKeys(source, copy);
        } finally {
            copy.shutdown();
            source.shutdown();
        }
    }

    @Test
    public void aCheckpointRestoreKeepsEachKey() throws Exception {
        final DatabaseEngine source = source();
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        try {
            requireKeys(source, restored);
        } finally {
            restored.shutdown();
            source.shutdown();
        }
    }
}
