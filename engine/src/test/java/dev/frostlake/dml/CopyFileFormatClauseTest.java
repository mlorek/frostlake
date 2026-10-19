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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY's inline FILE_FORMAT list, on a load and an unload, is judged as CREATE FILE FORMAT judges its options
 * (documented: the options are the same set), a stage's decoded delimiter and its NULL_IF reach a load as a named
 * format's do, and ON_ERROR's share of the records is a number that may carry a fraction, in COPY as in a stage's
 * COPY_OPTIONS. Engine only: the live account allows no COPY INTO.
 */
public class CopyFileFormatClauseTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("copy_ff_clause_");
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the default config refuses.
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.PUBLIC");
        engine.execute("CREATE STAGE data_stage URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        final File[] files = stageDir.toFile().listFiles();
        for (final File file : files != null ? files : new File[0]) {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
        if (!stageDir.toFile().delete()) {
            stageDir.toFile().deleteOnExit();
        }
    }

    private void writeStageFile(final String name, final String content) throws IOException {
        Files.writeString(stageDir.resolve(name), content);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void anInlineListIsJudgedAsACreateFileFormatIs() throws IOException {
        writeStageFile("t.csv", "1,2\n");
        engine.execute("CREATE TABLE t (a INT, b INT)");
        assertEquals("SQL compilation error:\ninvalid parameter 'BOGUS_OPT'",
            refusal("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = CSV BOGUS_OPT = 1)"));
        assertEquals("SQL compilation error:\nOption FIELD_DELIMITER is not valid for file format type JSON.",
            refusal("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = JSON FIELD_DELIMITER = '|')"));
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter 'SKIP_HEADER'",
            refusal("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = CSV SKIP_HEADER = -1)"));
        assertEquals("SQL compilation error:\ninvalid value ['yes'] for parameter 'TRIM_SPACE'",
            refusal("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = CSV TRIM_SPACE = 'yes')"));
        assertEquals("SQL compilation error: conflicting values file format parameter 'FIELD_DELIMITER'",
            refusal("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' FIELD_DELIMITER = ',')"));
        assertEquals("SQL compilation error:\ninvalid parameter 'BOGUS_OPT'",
            refusal("COPY INTO @data_stage/out FROM t FILE_FORMAT = (TYPE = CSV BOGUS_OPT = 1)"));
        assertEquals("", rows("SELECT * FROM t"));
        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = ',' SKIP_HEADER = 0)");
        assertEquals("1, 2", rows("SELECT * FROM t"));
    }

    @Test
    public void aStagesDecodedDelimiterAndNullIfReachALoad() throws IOException {
        writeStageFile("tab.tsv", "1\t2\n");
        engine.execute("CREATE STAGE tabs URL='file://" + stageDir + "' FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '\\t')");
        engine.execute("CREATE TABLE t2 (a INT, b INT)");
        engine.execute("COPY INTO t2 FROM @tabs/tab.tsv");
        assertEquals("1, 2", rows("SELECT * FROM t2"));
        writeStageFile("na.csv", "NA|x,y|1\n");
        engine.execute("CREATE STAGE nulls URL='file://" + stageDir + "'"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' NULL_IF = ('NA', 'x,y'))");
        final String described = rows("DESCRIBE STAGE nulls");
        assertTrue(described.contains("STAGE_FILE_FORMAT, NULL_IF, List, [NA, x,y], [\\\\N]"), described);
        assertTrue(described.contains("STAGE_FILE_FORMAT, FIELD_DELIMITER, String, |, ,"), described);
        engine.execute("CREATE TABLE t3 (a VARCHAR, b VARCHAR, c INT)");
        engine.execute("COPY INTO t3 FROM @nulls/na.csv");
        assertEquals("null, null, 1", rows("SELECT * FROM t3"));
    }

    @Test
    public void aShareOfTheRecordsBudgetsAFilesErrors() throws IOException {
        writeStageFile("mixed.csv", "1\n2\nx\n4\n5\n6\n7\n8\n");
        engine.execute("CREATE TABLE n (a INT)");
        assertEquals("SQL compilation error:\ninvalid value ['SKIP_FILE_150%'] for parameter 'ON_ERROR'",
            refusal("COPY INTO n FROM @data_stage/mixed.csv ON_ERROR = 'SKIP_FILE_150%'"));
        // 12.5% of 8 records is a budget of 1: the one bad record drops the file.
        final Row dropped = engine.executeQuery(
            "COPY INTO n FROM @data_stage/mixed.csv ON_ERROR = 'SKIP_FILE_12.5%'").getRows().get(0);
        assertEquals("LOAD_FAILED", dropped.getValue(1));
        assertEquals(0, ((Number) dropped.getValue(3)).intValue());
        assertEquals(1, ((Number) dropped.getValue(4)).intValue());
        // 25% of 8 is a budget of 2: the file loads its seven clean records.
        final Row kept = engine.executeQuery(
            "COPY INTO n FROM @data_stage/mixed.csv ON_ERROR = 'SKIP_FILE_25%' FORCE = TRUE").getRows().get(0);
        assertEquals("PARTIALLY_LOADED", kept.getValue(1));
        assertEquals(7, ((Number) kept.getValue(3)).intValue());
        assertEquals(2, ((Number) kept.getValue(4)).intValue());
        assertEquals("7", rows("SELECT COUNT(*) FROM n"));
    }
}
