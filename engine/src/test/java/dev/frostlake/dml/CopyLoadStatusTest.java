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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the per-file {@code status} of {@code COPY INTO <table>} — {@code LOADED},
 * {@code PARTIALLY_LOADED} and {@code LOAD_FAILED} — and the ON_ERROR error budget that decides it.
 *
 * <p>The rule is NOT "were any rows loaded": a header-only file loads zero rows and is {@code LOADED},
 * while a file dropped by ON_ERROR is {@code LOAD_FAILED} even when its records would have loaded. Every
 * case below was live-verified against a real account on each paired with a control run
 * producing the other outcome.
 */
public class CopyLoadStatusTest {

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_status_root_");
        localDir = Files.createTempDirectory("copy_status_local_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(internalRoot.toFile());
        deleteRecursively(localDir.toFile());
    }

    private static final String TWO_GOOD_ROWS = """
        id,name
        1,alice
        2,bob
        """;

    private static final String ONE_GOOD_ONE_BAD_ROW = """
        id,name
        5,eve
        NOTANUM,frank
        """;

    private static final String ALL_BAD_ROWS = """
        id,name
        BAD1,x
        BAD2,y
        """;

    /** Four records, exactly one of them bad — the file that separates a budget of 1 from a budget of 2. */
    private static final String ONE_BAD_OF_FOUR_ROWS = """
        id,name
        10,a
        11,b
        12,c
        BADX,d
        """;

    /** Four records, two of them bad — 50% exactly, the percentage-budget boundary. */
    private static final String TWO_BAD_OF_FOUR_ROWS = """
        id,name
        20,a
        BADY,b
        22,c
        BADZ,d
        """;

    private static final String HEADER_ONLY = "id,name\n";

    /** PUT a CSV written outside every stage root onto the table stage {@code @%t}. */
    private void stage(final String fileName, final String content) throws IOException {
        final Path file = localDir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.executeQuery("PUT file://" + file + " @%t");
    }

    private ResultSet copy(final String options) {
        return engine.executeQuery("COPY INTO t FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) " + options);
    }

    private long count() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM t");
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    /** The per-file row for one staged file, found by name so file listing order cannot matter. */
    private Row fileRow(final ResultSet rs, final String fileName) {
        assertEquals("file", rs.getColumns().get(0).getName());
        assertEquals("status", rs.getColumns().get(1).getName());
        for (final Row row : rs.getRows()) {
            if (fileName.equals(row.getValue(0))) {
                return row;
            }
        }
        fail("no per-file row for " + fileName + " in the COPY result");
        return null;
    }

    /** Assert one file's status, rows_parsed and rows_loaded — the three the status rule is defined over. */
    private void assertFile(final ResultSet rs, final String fileName, final String status,
            final int rowsParsed, final int rowsLoaded) {
        final Row row = fileRow(rs, fileName);
        assertEquals(status, row.getValue(1), "status of " + fileName);
        assertEquals(rowsParsed, ((Number) row.getValue(2)).intValue(), "rows_parsed of " + fileName);
        assertEquals(rowsLoaded, ((Number) row.getValue(3)).intValue(), "rows_loaded of " + fileName);
    }

    /** A clean file is LOADED. */
    @Test
    public void cleanFileIsLoaded() throws IOException {
        stage("good.csv", TWO_GOOD_ROWS);

        assertFile(copy(""), "good.csv", "LOADED", 2, 2);
        assertEquals(2, count());
    }

    /**
     * THE tie-breaker: a header-only file parses zero records, loads zero rows and is still LOADED. Any rule
     * that reads {@code rows_loaded == 0} as a failure gets this case wrong.
     */
    @Test
    public void headerOnlyFileIsLoadedNotFailed() throws IOException {
        stage("headeronly.csv", HEADER_ONLY);

        assertFile(copy("ON_ERROR = CONTINUE"), "headeronly.csv", "LOADED", 0, 0);
        assertEquals(0, count());
    }

    /** Same tie-breaker for a zero-byte file, and under SKIP_FILE rather than CONTINUE. */
    @Test
    public void zeroByteFileIsLoadedNotFailed() throws IOException {
        stage("empty.csv", "");

        assertFile(copy("ON_ERROR = SKIP_FILE"), "empty.csv", "LOADED", 0, 0);
        assertEquals(0, count());
    }

    /** ON_ERROR = CONTINUE with only SOME records rejected: PARTIALLY_LOADED, and the good record lands. */
    @Test
    public void someRecordsRejectedUnderContinueIsPartiallyLoaded() throws IOException {
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);

        assertFile(copy("ON_ERROR = CONTINUE"), "mixed.csv", "PARTIALLY_LOADED", 2, 1);
        assertEquals(1, count());
    }

    /** The headline case: ON_ERROR = CONTINUE with EVERY record rejected is LOAD_FAILED, not PARTIALLY. */
    @Test
    public void everyRecordRejectedUnderContinueIsLoadFailed() throws IOException {
        stage("bad.csv", ALL_BAD_ROWS);

        assertFile(copy("ON_ERROR = CONTINUE"), "bad.csv", "LOAD_FAILED", 2, 0);
        assertEquals(0, count());
    }

    /**
     * The second route into LOAD_FAILED: under SKIP_FILE a SINGLE bad record drops the whole file, so a file
     * that would have contributed a row contributes none. The same file is PARTIALLY_LOADED under CONTINUE
     * (see above) — the file is identical, only the ON_ERROR mode differs.
     */
    @Test
    public void anyRecordRejectedUnderSkipFileIsLoadFailed() throws IOException {
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);

        assertFile(copy("ON_ERROR = SKIP_FILE"), "mixed.csv", "LOAD_FAILED", 2, 0);
        assertEquals(0, count());
    }

    /** In one run the three statuses stand side by side — the status is decided per file. */
    @Test
    public void mixedOutcomesAreReportedPerFile() throws IOException {
        stage("good.csv", TWO_GOOD_ROWS);
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);
        stage("bad.csv", ALL_BAD_ROWS);

        final ResultSet rs = copy("ON_ERROR = CONTINUE");

        assertEquals(3, rs.getRows().size());
        assertFile(rs, "good.csv", "LOADED", 2, 2);
        assertFile(rs, "mixed.csv", "PARTIALLY_LOADED", 2, 1);
        assertFile(rs, "bad.csv", "LOAD_FAILED", 2, 0);
        assertEquals(3, count());
    }

    /** SKIP_FILE_&lt;n&gt;: one rejected record of four stays under a budget of 2. */
    @Test
    public void errorCountBelowTheBudgetIsPartiallyLoaded() throws IOException {
        stage("one_bad.csv", ONE_BAD_OF_FOUR_ROWS);

        assertFile(copy("ON_ERROR = SKIP_FILE_2"), "one_bad.csv", "PARTIALLY_LOADED", 4, 3);
        assertEquals(3, count());
    }

    /** The control for the case above: the same file under a budget of 1 is dropped entirely. */
    @Test
    public void errorCountAtTheBudgetIsLoadFailed() throws IOException {
        stage("one_bad.csv", ONE_BAD_OF_FOUR_ROWS);

        assertFile(copy("ON_ERROR = SKIP_FILE_1"), "one_bad.csv", "LOAD_FAILED", 4, 0);
        assertEquals(0, count());
    }

    /**
     * The two routes are independent: a wholly rejected file is LOAD_FAILED even under a budget it never
     * comes close to reaching (2 rejected records against a budget of 5).
     */
    @Test
    public void everyRecordRejectedBelowTheBudgetIsStillLoadFailed() throws IOException {
        stage("bad.csv", ALL_BAD_ROWS);

        assertFile(copy("ON_ERROR = SKIP_FILE_5"), "bad.csv", "LOAD_FAILED", 2, 0);
        assertEquals(0, count());
    }

    /** A percentage budget: 1 rejected record of 4 is 25%, under a 50% budget of 2. */
    @Test
    public void errorPercentBelowTheBudgetIsPartiallyLoaded() throws IOException {
        stage("one_bad.csv", ONE_BAD_OF_FOUR_ROWS);

        assertFile(copy("ON_ERROR = 'SKIP_FILE_50%'"), "one_bad.csv", "PARTIALLY_LOADED", 4, 3);
        assertEquals(3, count());
    }

    /** Its control: 2 rejected records of 4 reaches the same 50% budget and drops the file. */
    @Test
    public void errorPercentAtTheBudgetIsLoadFailed() throws IOException {
        stage("two_bad.csv", TWO_BAD_OF_FOUR_ROWS);

        assertFile(copy("ON_ERROR = 'SKIP_FILE_50%'"), "two_bad.csv", "LOAD_FAILED", 4, 0);
        assertEquals(0, count());
    }

    /**
     * The percentage budget rounds DOWN: 70% of 4 records is a budget of 2, not 3, so 2 rejected records
     * still drop the file.
     */
    @Test
    public void errorPercentBudgetRoundsDown() throws IOException {
        stage("two_bad.csv", TWO_BAD_OF_FOUR_ROWS);

        assertFile(copy("ON_ERROR = 'SKIP_FILE_70%'"), "two_bad.csv", "LOAD_FAILED", 4, 0);
        assertEquals(0, count());
    }

    /** …but never below 1: a 0% budget over a clean file is a budget of 1, so the file loads normally. */
    @Test
    public void errorPercentBudgetNeverFallsBelowOne() throws IOException {
        stage("good.csv", TWO_GOOD_ROWS);

        assertFile(copy("ON_ERROR = 'SKIP_FILE_0%'"), "good.csv", "LOADED", 2, 2);
        assertEquals(2, count());
    }

    /**
     * The plain count form is NOT floored the same way: SKIP_FILE_0 is a budget of zero rejected records,
     * which even a wholly CLEAN file reaches — it is LOAD_FAILED and loads nothing. The 0% test above is the
     * control that shows the two spellings genuinely differ.
     */
    @Test
    public void aZeroBudgetDropsEvenACleanFile() throws IOException {
        stage("good.csv", TWO_GOOD_ROWS);

        assertFile(copy("ON_ERROR = SKIP_FILE_0"), "good.csv", "LOAD_FAILED", 2, 0);
        assertEquals(0, count());
    }

    /** The budget is per FILE: two files with one rejected record each both survive a budget of 2. */
    @Test
    public void theErrorBudgetIsPerFileNotPerStatement() throws IOException {
        stage("one_bad.csv", ONE_BAD_OF_FOUR_ROWS);
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);

        final ResultSet rs = copy("ON_ERROR = SKIP_FILE_2");

        assertFile(rs, "one_bad.csv", "PARTIALLY_LOADED", 4, 3);
        assertFile(rs, "mixed.csv", "PARTIALLY_LOADED", 2, 1);
        assertEquals(4, count());
    }

    /** ON_ERROR = ABORT_STATEMENT (the default) fails the statement outright — there is no result to report. */
    @Test
    public void abortStatementProducesNoResultAtAll() throws IOException {
        stage("bad.csv", ALL_BAD_ROWS);

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("");
            }
        });
        assertEquals(0, count());
    }

    /**
     * A LOAD_FAILED file is not remembered as loaded: COPYing it again reports LOAD_FAILED a second time
     * rather than skipping it as already done.
     */
    @Test
    public void aLoadFailedFileIsNotRememberedAsLoaded() throws IOException {
        stage("bad.csv", ALL_BAD_ROWS);
        assertFile(copy("ON_ERROR = CONTINUE"), "bad.csv", "LOAD_FAILED", 2, 0);

        assertFile(copy("ON_ERROR = CONTINUE"), "bad.csv", "LOAD_FAILED", 2, 0);
    }

    /**
     * Its control: a PARTIALLY_LOADED file IS remembered, so the second run finds nothing to do and answers
     * with the one-column summary instead.
     */
    @Test
    public void aPartiallyLoadedFileIsRememberedAsLoaded() throws IOException {
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);
        assertFile(copy("ON_ERROR = CONTINUE"), "mixed.csv", "PARTIALLY_LOADED", 2, 1);

        final ResultSet again = copy("ON_ERROR = CONTINUE");

        assertEquals(1, again.getColumns().size());
        assertEquals("Copy executed with 0 files processed.", again.getRows().get(0).getValue(0));
        assertEquals(1, count());
    }

    /** A file the budget dropped is not remembered either — a later, more forgiving run reloads it. */
    @Test
    public void aDroppedFileIsReloadedByALaterRun() throws IOException {
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);
        assertFile(copy("ON_ERROR = SKIP_FILE"), "mixed.csv", "LOAD_FAILED", 2, 0);

        assertFile(copy("ON_ERROR = CONTINUE"), "mixed.csv", "PARTIALLY_LOADED", 2, 1);
        assertEquals(1, count());
    }

    /** The value is spelling-insensitive: quoted or bare, upper or lower case, all reach the same budget. */
    @Test
    public void theOnErrorValueIsSpellingInsensitive() throws IOException {
        stage("mixed.csv", ONE_GOOD_ONE_BAD_ROW);

        assertFile(copy("ON_ERROR = 'skip_file'"), "mixed.csv", "LOAD_FAILED", 2, 0);
    }

    /** A value outside the documented set is rejected rather than silently read as the aborting default. */
    @Test
    public void anUnknownOnErrorValueIsRejected() throws IOException {
        stage("good.csv", TWO_GOOD_ROWS);

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("ON_ERROR = KEEP_GOING");
            }
        });
        assertTrue(e.getMessage().contains("invalid value [KEEP_GOING] for parameter 'ON_ERROR'"),
            "unexpected message: " + e.getMessage());
    }

    /** A bare error count is not a spelling of the budget — SKIP_FILE_&lt;n&gt; is. */
    @Test
    public void aBareErrorCountIsRejected() throws IOException {
        stage("good.csv", TWO_GOOD_ROWS);

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("ON_ERROR = 2");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("ON_ERROR = '2'");
            }
        });
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
