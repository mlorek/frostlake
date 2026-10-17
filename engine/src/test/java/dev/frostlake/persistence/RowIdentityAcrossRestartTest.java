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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row keeps its METADATA$ROW_ID across a copy of the engine and a restart from a checkpoint, as the account's
 * immutable row id does, so a stream's pending change still names it; and a row stored afterwards takes an id no row
 * had before, a deleted one's included.
 */
public class RowIdentityAcrossRestartTest {

    @TempDir
    Path dir;

    private static final String CHANGES = "SELECT a, METADATA$ACTION, METADATA$ROW_ID FROM d.PUBLIC.st ORDER BY a, 2";

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
        engine.executeQuery("CREATE TABLE d.PUBLIC.t (a INT)");
        engine.executeQuery("INSERT INTO d.PUBLIC.t VALUES (1), (2), (3)");
        engine.executeQuery("CREATE STREAM d.PUBLIC.st ON TABLE d.PUBLIC.t");
        engine.executeQuery("DELETE FROM d.PUBLIC.t WHERE a = 2");
        engine.executeQuery("INSERT INTO d.PUBLIC.t VALUES (4)");
        return engine;
    }

    /**
     * The copy answers as the source after the same changes: an update of a row the stream already holds folds into
     * the change it holds, which only the row's own id tells, and a row stored next takes the id the source's takes.
     */
    private static void requireKeptIds(final DatabaseEngine source, final DatabaseEngine copy) {
        final String before = answer(source, CHANGES);
        assertTrue(before.contains("2|DELETE|") && before.contains("4|INSERT|"), before);
        assertEquals(before, answer(copy, CHANGES));
        for (final DatabaseEngine engine : new DatabaseEngine[] {source, copy}) {
            engine.executeQuery("UPDATE d.PUBLIC.t SET a = 40 WHERE a = 4");
            engine.executeQuery("UPDATE d.PUBLIC.t SET a = 30 WHERE a = 3");
            engine.executeQuery("INSERT INTO d.PUBLIC.t VALUES (5)");
            engine.executeQuery("CREATE STREAM d.PUBLIC.st2 ON TABLE d.PUBLIC.t SHOW_INITIAL_ROWS = TRUE");
        }
        assertEquals(answer(source, CHANGES), answer(copy, CHANGES));
        final String initial = "SELECT a, METADATA$ROW_ID FROM d.PUBLIC.st2 ORDER BY a";
        assertEquals(answer(source, initial), answer(copy, initial));
    }

    @Test
    public void anEngineCopyKeepsEachRowsId() throws Exception {
        final DatabaseEngine source = source();
        final DatabaseEngine copy = source.cloneInstance();
        try {
            requireKeptIds(source, copy);
        } finally {
            copy.shutdown();
            source.shutdown();
        }
    }

    @Test
    public void aCheckpointRestoreKeepsEachRowsId() throws Exception {
        final DatabaseEngine source = source();
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        try {
            requireKeptIds(source, restored);
        } finally {
            restored.shutdown();
            source.shutdown();
        }
    }
}
