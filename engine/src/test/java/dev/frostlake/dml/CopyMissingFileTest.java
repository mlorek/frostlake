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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@code COPY INTO <table> … FILES = (…)} naming a file the stage does not hold.
 *
 * <p>The rule under test is an ASYMMETRY, and the tests here exist to keep the two halves of it apart:
 * a {@code PATTERN} matching nothing is a legitimate no-op that answers with the "0 files processed"
 * summary, while a {@code FILES} list naming an absent file is an ERROR — the caller asserted those files
 * exist. A pattern merely filters what is on the stage; a FILES list promises.
 *
 * <p>Every case below was live-verified against a real account on each paired with a control
 * run producing the opposite outcome. The account answers an aborting COPY with SQLSTATE 22000, error
 * 91016; Frostlake carries no SQLSTATE on its exceptions, so the message is what is asserted here.
 */
public class CopyMissingFileTest {

    /** The summary a genuine no-op answers with — the shape a missing file must NOT collapse to. */
    private static final String NO_FILES = "Copy executed with 0 files processed.";

    private static final String TWO_GOOD_ROWS = """
        id,name
        1,alice
        2,bob
        """;

    private static final String TWO_MORE_GOOD_ROWS = """
        id,name
        3,carol
        4,dave
        """;

    private DatabaseEngine engine;
    private Path internalRoot;
    private Path namedStageDir;
    private Path localDir;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("copy_missing_root_");
        namedStageDir = Files.createTempDirectory("copy_missing_stage_");
        localDir = Files.createTempDirectory("copy_missing_local_");
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
        deleteRecursively(namedStageDir.toFile());
        deleteRecursively(localDir.toFile());
    }

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

    /** The per-file row for one file, found by name so result order cannot matter. */
    private Row fileRow(final ResultSet rs, final String fileName) {
        assertEquals("file", rs.getColumns().get(0).getName());
        for (final Row row : rs.getRows()) {
            if (fileName.equals(row.getValue(0))) {
                return row;
            }
        }
        fail("no per-file row for " + fileName + " in the COPY result");
        return null;
    }

    /** Assert the COPY threw, and that it named the missing file the way the account does. */
    private void assertNotFound(final String fileName, final Executable copy) {
        final RuntimeException e = assertThrows(RuntimeException.class, copy);
        assertTrue(e.getMessage().contains("Remote file"),
            "the failure must name the missing file, not merely fail: " + e.getMessage());
        assertTrue(e.getMessage().contains(fileName),
            "the failure must name " + fileName + ": " + e.getMessage());
        assertTrue(e.getMessage().contains("was not found"),
            "the account's wording, verbatim: " + e.getMessage());
    }

    // ── the aborting default: the statement fails ──────────────────────────────────────────────────

    /** The headline defect: a named file that is not on a NON-empty stage fails the statement. */
    @Test
    public void namingAnAbsentFileFailsTheStatement() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv')");
            }
        });
        assertEquals(0, count());
    }

    /** The exact message, verbatim from the account but for the stage-relative file reference. */
    @Test
    public void theFailureCarriesTheAccountsWording() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv')");
            }
        });

        assertTrue(e.getMessage().contains("Remote file '@%T/nosuch.csv' was not found."
            + " If you are running a copy command, please make sure files are not deleted when they are being"
            + " loaded or files are not being loaded into two different tables concurrently with auto purge"
            + " option."), e.getMessage());
    }

    /**
     * THE mixed-list question: naming one file that IS there beside one that is not fails the WHOLE
     * statement and loads nothing. The check precedes the load, so the good file does not land either.
     */
    @Test
    public void aMixedListLoadsNothingAtAll() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('good1.csv','nosuch.csv')");
            }
        });
        assertEquals(0, count(), "the present file must not have loaded");

        // Control: the same two names, both present, load both files.
        stage("good2.csv", TWO_MORE_GOOD_ROWS);
        final ResultSet both = copy("FILES = ('good1.csv','good2.csv')");
        assertEquals(2, both.getRows().size());
        assertEquals(4, count());
    }

    /** List order does not rescue it — the absent name may come first. */
    @Test
    public void aMixedListFailsInEitherOrder() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv','good1.csv')");
            }
        });
        assertEquals(0, count());
    }

    /** An EMPTY stage plus a FILES list errors: the promise outranks the "0 files processed" summary. */
    @Test
    public void namingAFileOnAnEmptyStageFailsRatherThanSummarising() {
        assertNotFound("x.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('x.csv')");
            }
        });

        // Control: the very same empty stage with no FILES list is the legitimate no-op.
        final ResultSet summary = copy("");
        assertEquals(1, summary.getColumns().size());
        assertEquals(NO_FILES, summary.getRows().get(0).getValue(0));
    }

    /** Every name absent, not just one of them. */
    @Test
    public void namingSeveralAbsentFilesFails() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("amissing.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('amissing.csv','zmissing.csv')");
            }
        });
    }

    /** FORCE = TRUE has nothing to re-load and does not excuse the missing file. */
    @Test
    public void forceDoesNotSuppressTheFailure() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv') FORCE = TRUE");
            }
        });
    }

    /** Spelling out the default ON_ERROR changes nothing — it really is the default that aborts. */
    @Test
    public void anExplicitAbortStatementFailsToo() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv') ON_ERROR = ABORT_STATEMENT");
            }
        });
    }

    /** A named stage reports the miss the same way — this is not a table-stage quirk. */
    @Test
    public void aNamedStageFailsTheSameWay() throws IOException {
        engine.execute("CREATE STAGE st URL = 'file://" + namedStageDir + "'");
        Files.write(namedStageDir.resolve("good1.csv"), TWO_GOOD_ROWS.getBytes(StandardCharsets.UTF_8));

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("COPY INTO t FROM @st FILES = ('nosuch.csv')"
                    + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
            }
        });
        assertTrue(e.getMessage().contains("Remote file '@ST/nosuch.csv' was not found."), e.getMessage());

        // Control: the file that IS on that stage loads.
        final ResultSet loaded = engine.executeQuery("COPY INTO t FROM @st FILES = ('good1.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertEquals("LOADED", loaded.getRows().get(0).getValue(1));
    }

    /** The user stage {@code @~} likewise. */
    @Test
    public void theUserStageFailsTheSameWay() {
        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("COPY INTO t FROM @~/nothinghere FILES = ('nosuch.csv')"
                    + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
            }
        });
    }

    /** A COPY transformation source is checked too — the miss is about the stage, not the projection. */
    @Test
    public void aTransformationSourceFailsTheSameWay() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("COPY INTO t FROM (SELECT $1, $2 FROM @%t) FILES = ('nosuch.csv')"
                    + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
            }
        });
    }

    // ── ON_ERROR tolerates the miss and reports it as a row ────────────────────────────────────────

    /**
     * The result that most needed probing rather than assuming: ON_ERROR = CONTINUE does NOT merely
     * change how many bad records are tolerated — it suppresses the failure outright and reports the
     * missing file as its own LOAD_FAILED row.
     */
    @Test
    public void continueReportsTheMissAsALoadFailedRowInsteadOfThrowing() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("FILES = ('nosuch.csv') ON_ERROR = CONTINUE");

        assertEquals(10, rs.getColumns().size(), "the miss keeps the wide per-file shape");
        final Row row = fileRow(rs, "nosuch.csv");
        assertEquals("LOAD_FAILED", row.getValue(1));
        assertEquals(0, ((Number) row.getValue(2)).intValue(), "rows_parsed");
        assertEquals(0, ((Number) row.getValue(3)).intValue(), "rows_loaded");
        assertEquals(1, ((Number) row.getValue(4)).intValue(), "error_limit");
        assertEquals(1, ((Number) row.getValue(5)).intValue(), "errors_seen");
        assertTrue(((String) row.getValue(6)).contains("was not found"), "first_error names the miss");
        assertNull(row.getValue(7), "a miss has no line");
        assertNull(row.getValue(8), "a miss has no character");
        assertNull(row.getValue(9), "a miss has no column");
    }

    /**
     * The reported wording is the LONGER of the account's two texts — the thrown one names no candidate
     * causes, the reported one names three. Keeping them apart is deliberate.
     */
    @Test
    public void theReportedWordingIsLongerThanTheThrownOne() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final Row row = fileRow(copy("FILES = ('nosuch.csv') ON_ERROR = CONTINUE"), "nosuch.csv");

        assertEquals("Remote file '@%T/nosuch.csv' was not found. There are several potential causes."
            + " The file might not exist. The required credentials may be missing or invalid. If you are"
            + " running a copy command, please make sure files are not deleted when they are being loaded or"
            + " files are not being loaded into two different tables concurrently with auto purge option.",
            row.getValue(6));
    }

    /** Under CONTINUE the files that ARE there load normally, beside the miss. */
    @Test
    public void continueLoadsThePresentFilesBesideTheMiss() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("FILES = ('good1.csv','nosuch.csv') ON_ERROR = CONTINUE");

        assertEquals(2, rs.getRows().size());
        assertEquals("LOADED", fileRow(rs, "good1.csv").getValue(1));
        assertEquals("LOAD_FAILED", fileRow(rs, "nosuch.csv").getValue(1));
        assertEquals(2, count(), "the present file really loaded");
    }

    /** …and the present files come first, the misses after them. */
    @Test
    public void theMissIsReportedAfterTheFilesThatWereRead() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("FILES = ('nosuch.csv','good1.csv') ON_ERROR = CONTINUE");

        assertEquals("good1.csv", rs.getRows().get(0).getValue(0));
        assertEquals("nosuch.csv", rs.getRows().get(1).getValue(0));
    }

    /** Every SKIP_FILE spelling suppresses it as well, so this is not a CONTINUE special case. */
    @Test
    public void skipFileReportsTheMissAsARowToo() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertEquals("LOAD_FAILED",
            fileRow(copy("FILES = ('nosuch.csv') ON_ERROR = SKIP_FILE"), "nosuch.csv").getValue(1));
    }

    /**
     * The reported {@code error_limit} is ON_ERROR's own budget read over a file of zero records — the same
     * function every other per-file row uses. All four values below came back off the account.
     */
    @Test
    public void theReportedErrorLimitIsOnErrorsBudgetForAnEmptyFile() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertEquals(1, errorLimitUnder("CONTINUE"));
        assertEquals(1, errorLimitUnder("SKIP_FILE"));
        assertEquals(5, errorLimitUnder("SKIP_FILE_5"));
        assertEquals(0, errorLimitUnder("SKIP_FILE_0"));
        assertEquals(1, errorLimitUnder("'SKIP_FILE_10%'"));
    }

    private int errorLimitUnder(final String onError) {
        final ResultSet rs = copy("FILES = ('nosuch.csv') ON_ERROR = " + onError);
        return ((Number) fileRow(rs, "nosuch.csv").getValue(4)).intValue();
    }

    /** A miss is not remembered: once the file is staged, a later COPY loads it. */
    @Test
    public void aMissIsNotRecordedInTheLoadHistory() throws IOException {
        assertEquals("LOAD_FAILED",
            fileRow(copy("FILES = ('later.csv') ON_ERROR = CONTINUE"), "later.csv").getValue(1));

        stage("later.csv", TWO_GOOD_ROWS);

        assertEquals("LOADED",
            fileRow(copy("FILES = ('later.csv') ON_ERROR = CONTINUE"), "later.csv").getValue(1));
        assertEquals(2, count());
    }

    // ── the load-history skip is NOT reclassified ──────────────────────────────────────────────────

    /** A named file that IS there but was already loaded keeps its LOAD_SKIPPED row, not a miss. */
    @Test
    public void anAlreadyLoadedNamedFileIsStillLoadSkipped() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);
        copy("FILES = ('good1.csv')");

        final Row again = fileRow(copy("FILES = ('good1.csv')"), "good1.csv");

        assertEquals("LOAD_SKIPPED", again.getValue(1));
        assertEquals("File was loaded before.", again.getValue(6));
        assertEquals(2, count());
    }

    /** Naming one of each under the aborting default: the miss wins over the skip. */
    @Test
    public void aMissBeatsAnAlreadyLoadedFileUnderTheDefault() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);
        copy("FILES = ('good1.csv')");

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('good1.csv','nosuch.csv')");
            }
        });
    }

    /** …and under CONTINUE both rows come back, each with its own wording. */
    @Test
    public void aMissAndASkipAreReportedSideBySideUnderContinue() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);
        copy("FILES = ('good1.csv')");

        final ResultSet rs = copy("FILES = ('good1.csv','nosuch.csv') ON_ERROR = CONTINUE");

        assertEquals(2, rs.getRows().size());
        assertEquals("LOAD_SKIPPED", fileRow(rs, "good1.csv").getValue(1));
        assertEquals("LOAD_FAILED", fileRow(rs, "nosuch.csv").getValue(1));
    }

    // ── PATTERN: the half of the asymmetry that must NOT become an error ───────────────────────────

    /**
     * THE guard for this whole task: a PATTERN matching nothing on a non-empty stage stays the "0 files
     * processed" summary. If this ever starts throwing, the two paths have been wrongly unified.
     */
    @Test
    public void aPatternMatchingNothingIsStillANoOp() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("PATTERN = '.*nosuchfile.*'");

        assertEquals(1, rs.getColumns().size(), "the summary shape has exactly one column");
        assertEquals("status", rs.getColumns().get(0).getName());
        assertEquals(NO_FILES, rs.getRows().get(0).getValue(0));
        assertEquals(0, count());
    }

    /** The same pattern spelled over an EMPTY stage is a no-op as well. */
    @Test
    public void aPatternOnAnEmptyStageIsStillANoOp() {
        final ResultSet rs = copy("PATTERN = '.*anything.*'");

        assertEquals(1, rs.getColumns().size());
        assertEquals(NO_FILES, rs.getRows().get(0).getValue(0));
    }

    /**
     * A PATTERN beside a FILES list is ignored outright — it neither narrows the list nor excuses a name.
     * Both halves live-verified: two named files both loaded through a pattern selecting only one, and a
     * named ABSENT file failed even under a pattern that matched a present one.
     */
    @Test
    public void aPatternDoesNotFilterANamedFileList() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);
        stage("good2.csv", TWO_MORE_GOOD_ROWS);

        final ResultSet rs = copy("FILES = ('good1.csv','good2.csv') PATTERN = '.*good1.*'");

        assertEquals(2, rs.getRows().size(), "the pattern must not narrow the FILES list");
        assertEquals(4, count());
    }

    /** …and a present named file survives a pattern that excludes it. */
    @Test
    public void aPatternDoesNotExcludeANamedFile() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("FILES = ('good1.csv') PATTERN = '.*nomatch.*'");

        assertEquals("LOADED", fileRow(rs, "good1.csv").getValue(1));
        assertEquals(2, count());
    }

    /** …while an ABSENT named file still fails, whatever the pattern would have matched. */
    @Test
    public void aPatternDoesNotExcuseAnAbsentNamedFile() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv') PATTERN = '.*good.*'");
            }
        });
    }

    // ── VALIDATION_MODE keeps its own answer ───────────────────────────────────────────────────────

    /** RETURN_ERRORS reports the miss as an error ROW rather than failing the statement. */
    @Test
    public void validationModeReturnErrorsReportsTheMissAsARow() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        final ResultSet rs = copy("FILES = ('nosuch.csv') VALIDATION_MODE = RETURN_ERRORS");

        assertEquals("ERROR", rs.getColumns().get(0).getName());
        assertEquals(1, rs.getRows().size());
        assertTrue(((String) rs.getRows().get(0).getValue(0)).contains("was not found"),
            String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("nosuch.csv", rs.getRows().get(0).getValue(1));

        // Control: the same mode over a file that IS there reports nothing.
        assertTrue(copy("FILES = ('good1.csv') VALIDATION_MODE = RETURN_ERRORS").getRows().isEmpty());
    }

    /** RETURN_&lt;n&gt;_ROWS throws instead — the two validation families do not agree here. */
    @Test
    public void validationModeReturnRowsThrowsOnTheMiss() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("nosuch.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('nosuch.csv') VALIDATION_MODE = RETURN_2_ROWS");
            }
        });

        // Control: the same mode over a file that IS there returns its rows.
        assertEquals(2, copy("FILES = ('good1.csv') VALIDATION_MODE = RETURN_2_ROWS").getRows().size());
    }

    // ── a FILES entry is a path, not a bare name ───────────────────────────────────────────────────

    /** A subdirectory path in FILES reaches the file — the account loads it, so it is not a miss. */
    @Test
    public void aSubdirectoryPathInFilesResolves() throws IOException {
        engine.execute("CREATE STAGE st URL = 'file://" + namedStageDir + "'");
        Files.createDirectories(namedStageDir.resolve("sub"));
        Files.write(namedStageDir.resolve("sub").resolve("nested.csv"),
            TWO_GOOD_ROWS.getBytes(StandardCharsets.UTF_8));

        final ResultSet rs = engine.executeQuery("COPY INTO t FROM @st FILES = ('sub/nested.csv')"
            + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");

        assertEquals("LOADED", rs.getRows().get(0).getValue(1));
        assertEquals(2, count());
    }

    /** The bare basename of a file that only exists in a subdirectory is a miss, as on the account. */
    @Test
    public void theBareNameOfANestedFileIsAMiss() throws IOException {
        engine.execute("CREATE STAGE st URL = 'file://" + namedStageDir + "'");
        Files.createDirectories(namedStageDir.resolve("sub"));
        Files.write(namedStageDir.resolve("sub").resolve("nested.csv"),
            TWO_GOOD_ROWS.getBytes(StandardCharsets.UTF_8));

        assertNotFound("nested.csv", new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("COPY INTO t FROM @st FILES = ('nested.csv')"
                    + " FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
            }
        });
    }

    /**
     * A name that climbs out of the stage never reaches the file it points at — it is a miss. The account
     * agrees for the absolute form: it reads {@code FILES = ('/good1.csv')} as {@code @stage//good1.csv}
     * and reports THAT as not found, even with {@code good1.csv} right there on the stage.
     */
    @Test
    public void aNameThatEscapesTheStageIsAMiss() throws IOException {
        stage("good1.csv", TWO_GOOD_ROWS);

        assertNotFound("good1.csv", new Executable() {
            @Override
            public void execute() {
                copy("FILES = ('../good1.csv')");
            }
        });
        assertEquals(0, count());
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
