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

package dev.frostlake.dml;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A COPY of a record file that cannot be read at all — a byte that is no UTF-8 in a JSON file — is one error of that
 * file: ON_ERROR = CONTINUE or SKIP_FILE reports it LOAD_FAILED, with the reader's refusal as its first error, and
 * goes on to the next file, as for a file whose every record is rejected (documented); the default aborts the
 * statement with the refusal. Uses a {@code file://} stage over a temp directory.
 */
public class CopyUnreadableFileTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_unreadable_");
        Files.write(stageDir.resolve("a_bad.json"), new byte[] {'{', '"', 'a', '"', ':', '"', 'c', (byte) 0xE9, '"',
            '}', '\n'});
        Files.writeString(stageDir.resolve("b_good.json"), "{\"a\": \"ok\"}\n");
        engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
        engine.execute("CREATE TABLE docs (doc VARIANT)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** Each file's row of a COPY's answer: file, status, rows parsed, rows loaded, errors seen and first error. */
    private String copied(final String onError) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery("COPY INTO docs FROM @st FILE_FORMAT = (TYPE = JSON) ON_ERROR = "
                + onError).getRows()) {
            rows.append(rows.length() > 0 ? " ;; " : "").append(row.getValue(0)).append('|').append(row.getValue(1))
                .append('|').append(row.getValue(2)).append('|').append(row.getValue(3)).append('|')
                .append(row.getValue(5)).append('|').append(String.valueOf(row.getValue(6)).split("\n")[0]);
        }
        return rows.toString();
    }

    @Test
    public void continueReportsTheFileLoadFailedAndLoadsTheNext() {
        assertEquals("st/a_bad.json|LOAD_FAILED|0|0|1|Error parsing JSON: invalid UTF-8 sequence: 0xE9"
            + " ;; st/b_good.json|LOADED|1|1|0|null", copied("CONTINUE"));
        assertEquals(1L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM docs").getRows().get(0).getValue(0))
            .longValue());
    }

    @Test
    public void skipFileReportsTheFileLoadFailed() {
        assertTrue(copied("SKIP_FILE").startsWith("st/a_bad.json|LOAD_FAILED|0|0|1|Error parsing JSON"));
    }

    @Test
    public void theDefaultAbortsTheStatementWithTheRefusal() {
        String refusal = null;
        try {
            engine.execute("COPY INTO docs FROM @st FILE_FORMAT = (TYPE = JSON)");
        } catch (final RuntimeException refused) {
            refusal = refused.getMessage();
        }
        assertTrue(refusal != null && refusal.contains("Error parsing JSON: invalid UTF-8 sequence: 0xE9"),
            String.valueOf(refusal));
        assertEquals(0L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM docs").getRows().get(0).getValue(0))
            .longValue());
    }
}
