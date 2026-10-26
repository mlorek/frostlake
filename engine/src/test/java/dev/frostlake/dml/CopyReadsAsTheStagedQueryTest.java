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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A COPY INTO a table loads a staged CSV file as a query of the file reads it (see StagedCsvEscapesAndEnclosuresTest,
 * live-verified): an escaped RECORD_DELIMITER is the delimiter's character, and a field that does not start with its
 * enclosure keeps it as data unless TRIM_SPACE trims and unquotes it. COPY is not run on the account by this suite,
 * so this is the engine's own check that the two readers agree.
 */
public class CopyReadsAsTheStagedQueryTest {

    @TempDir
    Path dir;

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws IOException {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, Files.createDirectories(dir.resolve("stages"))
            .toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        final Path local = Files.createDirectories(dir.resolve("local"));
        final Path delimited = Files.write(local.resolve("rd2.txt"), "x\\|y,z|p,q|".getBytes(StandardCharsets.UTF_8));
        final Path enclosed = Files.write(local.resolve("esc.csv"),
            "p1,p2\n\"e1\",\"e2\"\n\"q,1\" , \"w\"\n  \"sp\",x\n".getBytes(StandardCharsets.UTF_8));
        engine.execute("CREATE STAGE st");
        engine.execute("PUT 'file://" + delimited + "' @st AUTO_COMPRESS = FALSE");
        engine.execute("PUT 'file://" + enclosed + "' @st AUTO_COMPRESS = FALSE");
        engine.execute("CREATE FILE FORMAT f4 TYPE = CSV RECORD_DELIMITER = '|'");
        engine.execute("CREATE FILE FORMAT f5 TYPE = CSV RECORD_DELIMITER = '|' FIELD_DELIMITER = NONE");
        engine.execute("CREATE FILE FORMAT f1 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE FILE FORMAT f2 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"' TRIM_SPACE = TRUE");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** What a two-column load answers, each row "a|len(a)|b|len(b)", in order of the first column. */
    private String loaded(final String file, final String format) {
        engine.execute("CREATE OR REPLACE TABLE t (a STRING, b STRING)");
        engine.execute("COPY INTO t FROM @st FILES = ('" + file + "') FILE_FORMAT = (FORMAT_NAME = '" + format
            + "') ON_ERROR = CONTINUE");
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT a, LENGTH(a), b, LENGTH(b) FROM t ORDER BY a").getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(row.getValue(0)).append('|').append(row.getValue(1))
                .append('|').append(row.getValue(2)).append('|').append(row.getValue(3));
        }
        return out.toString();
    }

    @Test
    public void anEscapedRecordDelimiterLoadsAsItsCharacter() {
        assertEquals("p|1|q|1 ;; x|y|3|z|1", loaded("rd2.txt", "f4"));
        engine.execute("CREATE OR REPLACE TABLE t1 (a STRING)");
        engine.execute("COPY INTO t1 FROM @st FILES = ('rd2.txt') FILE_FORMAT = (FORMAT_NAME = 'f5')");
        final StringBuilder one = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT a, LENGTH(a) FROM t1 ORDER BY a").getRows()) {
            one.append(one.length() == 0 ? "" : " ;; ").append(row.getValue(0)).append('|').append(row.getValue(1));
        }
        assertEquals("p,q|3 ;; x|y,z|5", one.toString());
    }

    @Test
    public void aFieldOutsideItsEnclosureKeepsTheQuotesUnlessTrimmed() {
        assertEquals("  \"sp\"|6|x|1 ;; e1|2|e2|2 ;; p1|2|p2|2 ;; q,1|3| \"w\"|4", loaded("esc.csv", "f1"));
        assertEquals("e1|2|e2|2 ;; p1|2|p2|2 ;; q,1|3|w|1 ;; sp|2|x|1", loaded("esc.csv", "f2"));
    }
}
