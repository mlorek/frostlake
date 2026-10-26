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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A COPY INTO a table takes the copy options the stage it reads keeps - a named stage's COPY_OPTIONS, a table stage's
 * STAGE_COPY_OPTIONS - for every option it does not write itself, and the options it writes win (the CREATE STAGE and
 * COPY INTO pages). COPY is not run on the account by this suite, so this is the engine's own check.
 */
public class CopyUsesStageCopyOptionsTest {

    @TempDir
    Path dir;

    private DatabaseEngine engine;
    private Path bad;
    private Path ok;

    @BeforeEach
    public void setUp() throws IOException {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, Files.createDirectories(dir.resolve("stages"))
            .toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        final Path local = Files.createDirectories(dir.resolve("local"));
        bad = Files.write(local.resolve("bad.csv"), "1,a\nx,b\n3,c\n".getBytes(StandardCharsets.UTF_8));
        ok = Files.write(local.resolve("ok.csv"), "1,a\n2,b\n".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** Each file's status, in order: "file|status ;; …". */
    private String copy(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(row.getValue(0));
            if (row.getValues().size() > 1) {
                out.append('|').append(row.getValue(1));
            }
        }
        return out.toString();
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void aStagesOnErrorIsTheDefaultOfACopyThatWritesNone() {
        engine.execute("CREATE STAGE st COPY_OPTIONS = (ON_ERROR = CONTINUE) FILE_FORMAT = (TYPE = CSV)");
        engine.execute("PUT 'file://" + bad + "' @st AUTO_COMPRESS = FALSE");
        engine.execute("CREATE TABLE t (id INT, v STRING)");
        assertEquals("st/bad.csv|PARTIALLY_LOADED", copy("COPY INTO t FROM @st"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aStagesSkipFilePercentageIsReadAsAWrittenOneIs() {
        engine.execute("CREATE STAGE sk COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE_10%') FILE_FORMAT = (TYPE = CSV)");
        engine.execute("PUT 'file://" + bad + "' @sk AUTO_COMPRESS = FALSE");
        engine.execute("PUT 'file://" + ok + "' @sk AUTO_COMPRESS = FALSE");
        engine.execute("CREATE TABLE t (id INT, v STRING)");
        assertEquals("sk/bad.csv|LOAD_FAILED ;; sk/ok.csv|LOADED",
            copy("COPY INTO t FROM @sk ->> SELECT \"file\", \"status\" FROM $1 ORDER BY 1"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void theStatementsOwnOptionWinsOverTheStages() {
        engine.execute("CREATE STAGE st COPY_OPTIONS = (ON_ERROR = CONTINUE) FILE_FORMAT = (TYPE = CSV)");
        engine.execute("PUT 'file://" + bad + "' @st AUTO_COMPRESS = FALSE");
        engine.execute("CREATE TABLE t (id INT, v STRING)");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("COPY INTO t FROM @st ON_ERROR = ABORT_STATEMENT");
            }
        });
        assertEquals("Numeric value 'x' is not recognized", refused.getMessage().split("\n")[0]);
        assertEquals(0L, count("SELECT COUNT(*) FROM t"));
    }

    @Test
    public void aStagesPurgeEmptiesItAfterTheLoad() {
        engine.execute("CREATE STAGE sp COPY_OPTIONS = (PURGE = TRUE)");
        engine.execute("PUT 'file://" + ok + "' @sp AUTO_COMPRESS = FALSE");
        engine.execute("CREATE TABLE t1 (id INT, v STRING)");
        assertEquals("sp/ok.csv|LOADED", copy("COPY INTO t1 FROM @sp"));
        assertEquals(0L, count("LIST @sp ->> SELECT COUNT(*) FROM $1"));
    }

    @Test
    public void aTablesStageCopyOptionsApplyToLoadsFromItsStage() {
        engine.execute("CREATE TABLE t2 (id INT, v STRING) STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE)");
        engine.execute("PUT 'file://" + bad + "' @%t2 AUTO_COMPRESS = FALSE");
        assertEquals("bad.csv|PARTIALLY_LOADED", copy("COPY INTO t2"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t2"));
    }
}
