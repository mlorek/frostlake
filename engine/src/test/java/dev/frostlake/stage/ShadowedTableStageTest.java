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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A temporary table that takes a permanent table's name has a table stage and a COPY load history of its own. The
 * engine kept both by the shared name, so the temporary table listed the permanent table's files and skipped the
 * files that one had loaded.
 *
 * <p>On the account the temporary table's stage starts empty, a file put through the shared name lands in it, and
 * once the temporary table is dropped the permanent table's files and its history are there again; the next
 * temporary table of the name starts empty likewise. Every cell is live-verified.
 */
public class ShadowedTableStageTest extends BaseDatabaseTest {

    /** The names of the files a stage lists, without their directories, in order. */
    private List<String> listed(final String stage) {
        final ResultSet rs = engine.executeQuery("LIST " + stage);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final String name = cell(rs, row, "name");
            out.add(name.substring(name.lastIndexOf('/') + 1));
        }
        Collections.sort(out);
        return out;
    }

    /** A COPY's answer: the file names it loaded, or its status. */
    private String copied(final String table) {
        final ResultSet rs = engine.executeQuery("COPY INTO " + table);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            final String first = String.valueOf(row.getValue(0));
            out.append(out.length() > 0 ? " " : "").append(rs.getColumns().size() > 1
                ? first.substring(first.lastIndexOf('/') + 1) + ":" + row.getValue(1) : first);
        }
        return out.toString();
    }

    private String rowsOf(final String table) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT a, b FROM " + table + " ORDER BY a").getRows()) {
            out.append(out.length() > 0 ? " " : "").append(row.getValue(0)).append(':').append(row.getValue(1));
        }
        return out.toString();
    }

    private static List<String> names(final String... names) {
        final List<String> out = new ArrayList<>();
        for (final String name : names) {
            out.add(name);
        }
        return out;
    }

    @Test
    public void theTemporaryTableHasItsOwnStageAndHistory() {
        engine.execute("CREATE TABLE kt (a INT, b VARCHAR)");
        stageLocalFile("%kt", "k1.csv", "1,perm\n");
        assertEquals(names("k1.csv"), listed("@%kt"));
        assertEquals("k1.csv:LOADED", copied("kt"));

        engine.execute("CREATE TEMPORARY TABLE kt (a INT, b VARCHAR)");
        assertEquals(names(), listed("@%kt"), "the temporary table's stage starts empty");
        assertEquals("Copy executed with 0 files processed.", copied("kt"));
        stageLocalFile("%kt", "k2.csv", "2,temp\n");
        assertEquals(names("k2.csv"), listed("@%kt"));
        assertEquals("k2.csv:LOADED", copied("kt"));
        assertEquals("2:temp", rowsOf("kt"));

        engine.execute("DROP TABLE kt");
        assertEquals(names("k1.csv"), listed("@%kt"), "the permanent table's files are there again");
        assertEquals("1:perm", rowsOf("kt"));
        assertEquals("Copy executed with 0 files processed.", copied("kt"), "and so is what it had loaded");

        engine.execute("CREATE TEMPORARY TABLE kt (a INT, b VARCHAR)");
        assertEquals(names(), listed("@%kt"), "a second temporary table starts empty too");
    }

    @Test
    public void aFileThePermanentTableLoadedIsNewToTheTemporaryOne() {
        engine.execute("CREATE TABLE kh (a INT, b VARCHAR)");
        engine.execute("CREATE STAGE kh_files");
        stageLocalFile("kh_files", "h1.csv", "1,one\n");
        assertEquals("h1.csv:LOADED", copied("kh FROM @kh_files"));
        assertEquals("Copy executed with 0 files processed.", copied("kh FROM @kh_files"));
        engine.execute("CREATE TEMPORARY TABLE kh (a INT, b VARCHAR)");
        assertEquals("h1.csv:LOADED", copied("kh FROM @kh_files"), "the temporary table has loaded nothing yet");
        assertEquals("Copy executed with 0 files processed.", copied("kh FROM @kh_files"));
        engine.execute("DROP TABLE kh");
        assertEquals("Copy executed with 0 files processed.", copied("kh FROM @kh_files"),
            "the permanent table keeps its own history");
        assertEquals("1:one", rowsOf("kh"));
    }
}
