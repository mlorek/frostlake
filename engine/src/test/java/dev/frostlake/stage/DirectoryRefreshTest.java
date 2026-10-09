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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A directory table lists the files its last refresh registered, not the files its stage holds. The engine listed
 * the stage's files as they were, so a staged file showed at once and a refresh answered a bare status.
 *
 * <p>On the account {@code ALTER STAGE … REFRESH} compares the stage with what the table has registered and answers
 * one row per file whose registration changed — {@code stages/<id>/<path>} with REGISTERED_NEW, REGISTERED_UPDATE or
 * UNREGISTERED — and no row for a file that stayed as it was. The id is the stage's own. A SUBPATH narrows the
 * refresh to the paths that start with it, as plain text; of several options the first alone is read. Every cell is
 * live-verified.
 */
public class DirectoryRefreshTest extends BaseDatabaseTest {

    private static final String ID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE rd DIRECTORY = (ENABLE = TRUE)");
    }

    /** A refresh's rows as {@code path status description}, by path; the id is checked and left out. */
    private List<String> refreshed(final String stage, final String tail) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("ALTER STAGE " + stage + " REFRESH" + tail).getRows()) {
            final String file = String.valueOf(row.getValue(0));
            assertTrue(file.matches("stages/" + ID + "/.*"), file);
            out.add(file.replaceFirst("^stages/" + ID + "/", "") + " " + row.getValue(1) + " " + row.getValue(2));
        }
        Collections.sort(out);
        return out;
    }

    /** The paths the directory table lists, in order. */
    private List<String> listed(final String stage) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT relative_path FROM DIRECTORY(@" + stage + ") ORDER BY 1")
                .getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        return out;
    }

    /** The statement's refusal on one line, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Stages a file over one of the same name. */
    private void restage(final String stage, final String name, final String content) {
        try {
            final Path file = Files.createTempDirectory("fl_restage").resolve(name);
            Files.writeString(file, content);
            engine.execute("PUT file://" + file.toAbsolutePath() + " @" + stage + " AUTO_COMPRESS=FALSE OVERWRITE=TRUE");
        } catch (final IOException e) {
            throw new IllegalStateException("could not stage " + name, e);
        }
    }

    private static List<String> rows(final String... rows) {
        final List<String> out = new ArrayList<>();
        Collections.addAll(out, rows);
        return out;
    }

    @Test
    public void aFileIsListedOnceARefreshRegistersIt() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        stageLocalFile("rd/sub", "b1.csv", "1,b\n");
        assertEquals(rows(), listed("rd"), "a staged file is not listed before a refresh");
        assertEquals(rows("a1.csv REGISTERED_NEW File registered successfully.",
            "sub/b1.csv REGISTERED_NEW File registered successfully."), refreshed("rd", ""));
        assertEquals(rows("a1.csv", "sub/b1.csv"), listed("rd"));
        assertEquals(rows(), refreshed("rd", ""), "nothing changed");
    }

    @Test
    public void aRemovedFileStaysListedUntilTheNextRefresh() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        stageLocalFile("rd", "b1.csv", "1,b\n");
        refreshed("rd", "");
        stageLocalFile("rd", "c1.csv", "3,c\n");
        engine.execute("REMOVE @rd/a1.csv");
        assertEquals(rows("a1.csv", "b1.csv"), listed("rd"));
        assertEquals(rows("a1.csv UNREGISTERED File unregistered successfully.",
            "c1.csv REGISTERED_NEW File registered successfully."), refreshed("rd", ""));
        assertEquals(rows("b1.csv", "c1.csv"), listed("rd"));
    }

    @Test
    public void aReplacedFileIsRegisteredAgain() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        refreshed("rd", "");
        final Row before = engine.executeQuery("SELECT md5, last_modified FROM DIRECTORY(@rd)").getRows().get(0);
        restage("rd", "a1.csv", "1,a,changed,and,much,longer,than,it,was,before,by,a,lot\n");
        final Row stale = engine.executeQuery("SELECT md5, last_modified FROM DIRECTORY(@rd)").getRows().get(0);
        assertEquals(String.valueOf(before.getValue(0)), String.valueOf(stale.getValue(0)),
            "the listing keeps what was registered");
        assertEquals(rows("a1.csv REGISTERED_UPDATE File registered (updated) successfully."), refreshed("rd", ""));
        final Row after = engine.executeQuery("SELECT md5, last_modified FROM DIRECTORY(@rd)").getRows().get(0);
        assertNotEquals(String.valueOf(before.getValue(0)), String.valueOf(after.getValue(0)));
    }

    @Test
    public void theAnswerHasThreeTextColumnsAndAStageId() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        final ResultSet first = engine.executeQuery("ALTER STAGE rd REFRESH");
        assertEquals("file", first.getColumns().get(0).getName());
        assertEquals("status", first.getColumns().get(1).getName());
        assertEquals("description", first.getColumns().get(2).getName());
        final String id = String.valueOf(first.getRows().get(0).getValue(0)).split("/")[1];
        stageLocalFile("rd", "b1.csv", "1,b\n");
        final String again = String.valueOf(engine.executeQuery("ALTER STAGE rd REFRESH").getRows().get(0).getValue(0));
        assertEquals("stages/" + id + "/b1.csv", again, "the id is the stage's, the same at every refresh");
        engine.execute("CREATE STAGE rd_other DIRECTORY = (ENABLE = TRUE)");
        stageLocalFile("rd_other", "a1.csv", "1,a\n");
        final String other = String.valueOf(engine.executeQuery("ALTER STAGE rd_other REFRESH").getRows().get(0)
            .getValue(0));
        assertNotEquals("stages/" + id + "/a1.csv", other, "another stage has another id");
    }

    @Test
    public void theDigestColumnsAreFilled() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        refreshed("rd", "");
        final Row row = engine.executeQuery("SELECT md5, etag, md5 = etag, size > 0 FROM DIRECTORY(@rd)").getRows().get(0);
        assertTrue(String.valueOf(row.getValue(0)).matches("[0-9a-f]{32}"), String.valueOf(row.getValue(0)));
        assertEquals("true", String.valueOf(row.getValue(2)));
        assertEquals("true", String.valueOf(row.getValue(3)));
    }

    @Test
    public void aSubpathIsAPlainPrefix() {
        stageLocalFile("rd/sub", "b1.csv", "1,b\n");
        stageLocalFile("rd/sub2", "c1.csv", "1,c\n");
        stageLocalFile("rd/other", "d1.csv", "1,d\n");
        stageLocalFile("rd", "subfile.csv", "1,s\n");
        assertEquals(rows(), refreshed("rd", " SUBPATH = '/sub/'"), "a leading slash starts no path");
        assertEquals(rows(), refreshed("rd", " SUBPATH = 'nosuch/'"));
        assertEquals(rows("sub/b1.csv REGISTERED_NEW File registered successfully.",
            "sub2/c1.csv REGISTERED_NEW File registered successfully.",
            "subfile.csv REGISTERED_NEW File registered successfully."), refreshed("rd", " SUBPATH = 'su'"));
        assertEquals(rows("sub/b1.csv", "sub2/c1.csv", "subfile.csv"), listed("rd"),
            "a file outside the subpath is not registered");
        assertEquals(rows("other/d1.csv REGISTERED_NEW File registered successfully."), refreshed("rd", ""));
    }

    @Test
    public void aFileOutsideTheSubpathKeepsItsRegistration() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        stageLocalFile("rd/sub", "b1.csv", "1,b\n");
        refreshed("rd", "");
        engine.execute("REMOVE @rd/a1.csv");
        assertEquals(rows(), refreshed("rd", " SUBPATH = 'sub/'"));
        assertEquals(rows("a1.csv", "sub/b1.csv"), listed("rd"));
    }

    @Test
    public void theFirstOptionAloneIsRead() {
        stageLocalFile("rd/one", "o1.csv", "1,o\n");
        stageLocalFile("rd/two", "t1.csv", "1,t\n");
        assertEquals(rows("one/o1.csv REGISTERED_NEW File registered successfully."),
            refreshed("rd", " SUBPATH = 'one/' SUBPATH = 'two/'"));
        assertEquals(rows(), refreshed("rd", " SUBPATH = 'x' FOO = 'y'"));
        assertEquals(rows(), refreshed("rd", " SUBPATH = 'x', SUBPATH = 'y'"));
        assertEquals(rows(), refreshed("rd", " subpath = 'x'"));
        assertEquals(rows(), refreshed("rd", " SUBPATH = abc"));
        assertEquals(rows("two/t1.csv REGISTERED_NEW File registered successfully."), refreshed("rd", ""));
    }

    @Test
    public void anOptionIsJudgedByNameAndValue() {
        assertEquals("Invalid Alter Stage Refresh Option FOO", outcome("ALTER STAGE rd REFRESH FOO = 'x'"));
        assertEquals("SQL compilation error:|invalid value [5] for parameter 'SUBPATH'",
            outcome("ALTER STAGE rd REFRESH SUBPATH = 5"));
        assertEquals("SQL compilation error:|invalid value [null] for parameter 'SUBPATH'",
            outcome("ALTER STAGE rd REFRESH SUBPATH = NULL"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 31 unexpected ''a/''.",
            outcome("ALTER STAGE rd REFRESH SUBPATH 'a/'"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 26 unexpected '<EOF>'.",
            outcome("ALTER STAGE rd REFRESH NOW"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'SET'.",
            outcome("ALTER STAGE rd REFRESH SET COMMENT = 'z'"));
    }

    @Test
    public void aRefreshNeedsAStageWithADirectoryTable() {
        engine.execute("CREATE STAGE nd");
        assertEquals("DIRECTORY not enabled for the stage ND", outcome("ALTER STAGE nd REFRESH"));
        final String missing = hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not"
            + " authorized.");
        assertEquals(missing, outcome("ALTER STAGE nosuch REFRESH"));
        assertEquals(missing, outcome("ALTER STAGE IF EXISTS nosuch REFRESH"), "IF EXISTS forgives no missing stage here");
        assertEquals("ok", outcome("ALTER STAGE IF EXISTS rd REFRESH"));
        assertEquals("ok", outcome("ALTER STAGE test_db.test_schema.rd REFRESH"));
    }

    @Test
    public void turningTheTableOffForgetsWhatItRegistered() {
        stageLocalFile("rd", "a1.csv", "1,a\n");
        stageLocalFile("rd", "b1.csv", "1,b\n");
        refreshed("rd", "");
        engine.execute("ALTER STAGE rd SET DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows("a1.csv", "b1.csv"), listed("rd"), "turning on a table that is on changes nothing");
        engine.execute("ALTER STAGE rd SET DIRECTORY = (AUTO_REFRESH = FALSE)");
        assertEquals(rows("a1.csv", "b1.csv"), listed("rd"), "a list without ENABLE leaves the table on");
        engine.execute("ALTER STAGE rd SET DIRECTORY = (ENABLE = FALSE)");
        assertEquals("DIRECTORY not enabled for the stage rd", outcome("SELECT COUNT(*) FROM DIRECTORY(@rd)"));
        engine.execute("ALTER STAGE rd SET DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows(), listed("rd"), "a table turned on again starts empty");
        assertEquals(rows("a1.csv REGISTERED_NEW File registered successfully.",
            "b1.csv REGISTERED_NEW File registered successfully."), refreshed("rd", ""));
    }

    @Test
    public void aTableTurnedOnByAnAlterRegistersNothing() {
        engine.execute("CREATE STAGE late");
        stageLocalFile("late", "x1.csv", "1,x\n");
        engine.execute("ALTER STAGE late SET DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows(), listed("late"));
        assertEquals(rows("x1.csv REGISTERED_NEW File registered successfully."), refreshed("late", ""));
    }
}
