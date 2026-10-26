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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A temporary table, view or stage is its own session's alone (live-verified): another session neither reads, lists nor
 * drops it, while its own session goes on reading it. Two sessions may each hold a temporary table or view of one name,
 * each reading its own, and a temporary table above a permanent one hides it from its own session alone; a dropped
 * schema takes every session's temporary tables with it. Two sessions may each hold a temporary stage of one name, its
 * files their own, and one above a permanent stage hides it from its session alone; a session swaps two of its own
 * temporary tables. INFORMATION_SCHEMA.TABLES lists a temporary table to its own
 * session only, where INFORMATION_SCHEMA.VIEWS and FILE_FORMATS list every session's. A temporary file format is its
 * session's alone too, and ends with it.
 */
public class TemporaryObjectsPerSessionTest extends BaseJdbcTest {

    /** Another session on the same account or engine as the test's own connection, in the test's schema. */
    private Connection another() throws SQLException {
        final Connection opened = LiveSnowflake.enabled() ? LiveSnowflake.open()
            : new DirectConnection(sharedEngine);
        try (Statement use = opened.createStatement()) {
            use.execute("USE SCHEMA test_db.public");
        }
        return opened;
    }

    /**
     * What a statement answers: a query's or a call's first cell, the number of rows a listing holds, OK for any other
     * statement that runs, or its refusal with the session's role masked.
     */
    private static String answer(final Statement on, final String sql) {
        try {
            final boolean listing = sql.startsWith("SHOW");
            if (!sql.startsWith("SELECT") && !sql.startsWith("CALL") && !listing) {
                on.execute(sql);
                return "OK";
            }
            try (ResultSet rs = on.executeQuery(sql)) {
                if (listing) {
                    int rows = 0;
                    while (rs.next()) {
                        rows++;
                    }
                    return rows + " rows";
                }
                return rs.next() ? rs.getString(1) : "no rows";
            }
        } catch (final SQLException refused) {
            final String message = refused.getMessage().replace('\n', ' ').trim()
                .replaceAll("primary role \\S+ must", "primary role <ROLE> must");
            final String compilation = "SQL compilation error: ";
            return "REFUSED " + (message.startsWith(compilation) ? message.substring(compilation.length()) : message)
                .trim();
        }
    }

    /** Runs each step in its session and compares every answer at once, so one run shows each that differs. */
    private void assertSteps(final Statement a, final Statement b, final String[][] steps) {
        final List<String> expected = new ArrayList<>();
        final List<String> answered = new ArrayList<>();
        for (final String[] step : steps) {
            final Statement on = "A".equals(step[0]) ? a : "B".equals(step[0]) ? b : statement;
            expected.add(step[0] + ": " + step[1] + " => " + step[2]);
            answered.add(step[0] + ": " + step[1] + " => " + answer(on, step[1]));
        }
        assertEquals(String.join("\n", expected), String.join("\n", answered));
    }

    @Test
    public void anotherSessionNeitherReadsNorListsATemporaryObject() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY TABLE tmpt (x INT)", "OK"},
                {"A", "INSERT INTO tmpt VALUES (1)", "OK"},
                {"A", "CREATE TEMPORARY VIEW tmpv AS SELECT 5 AS c", "OK"},
                {"A", "CREATE TEMPORARY STAGE tmps", "OK"},
                {"A", "SELECT COUNT(*) FROM tmpt", "1"},
                {"A", "SELECT c FROM tmpv", "5"},
                {"B", "SELECT COUNT(*) FROM tmpt", "REFUSED Object 'TMPT' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on TABLE TMPT."},
                {"C", "SELECT COUNT(*) FROM tmpt", "REFUSED Object 'TMPT' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on TABLE TMPT."},
                {"B", "SELECT c FROM tmpv", "REFUSED Object 'TMPV' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on TABLE TMPV."},
                {"B", "SHOW TABLES LIKE 'TMPT' IN SCHEMA test_db.public", "0 rows"},
                {"B", "SHOW VIEWS LIKE 'TMPV' IN SCHEMA test_db.public", "0 rows"},
                {"B", "SHOW STAGES LIKE 'TMPS' IN SCHEMA test_db.public", "0 rows"},
                {"A", "SHOW TABLES LIKE 'TMPT' IN SCHEMA test_db.public", "1 rows"},
                {"A", "SHOW VIEWS LIKE 'TMPV' IN SCHEMA test_db.public", "1 rows"},
                {"A", "SHOW STAGES LIKE 'TMPS' IN SCHEMA test_db.public", "1 rows"},
                {"B", "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'TMPT'", "0"},
                {"A", "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'TMPT'", "1"},
                {"B", "SELECT COUNT(*) FROM test_db.information_schema.views WHERE table_name = 'TMPV'", "1"},
                {"B", "DROP TABLE tmpt", "REFUSED Table 'TEST_DB.PUBLIC.TMPT' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on "
                    + "TABLE TEST_DB.PUBLIC.TMPT."},
                {"B", "DROP VIEW tmpv", "REFUSED View 'TEST_DB.PUBLIC.TMPV' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on "
                    + "TABLE TEST_DB.PUBLIC.TMPV."},
                {"B", "DROP STAGE tmps", "REFUSED Stage 'TEST_DB.PUBLIC.TMPS' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on "
                    + "STAGE TEST_DB.PUBLIC.TMPS."},
                {"A", "SELECT COUNT(*) FROM tmpt", "1"},
                {"A", "SELECT c FROM tmpv", "5"},
                {"A", "SHOW STAGES LIKE 'TMPS' IN SCHEMA test_db.public", "1 rows"},
            });
        }
    }
    @Test
    public void twoSessionsEachHoldATemporaryTableOfOneName() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY TABLE tmpt (x INT)", "OK"},
                {"A", "INSERT INTO tmpt VALUES (1)", "OK"},
                {"B", "CREATE TEMPORARY TABLE tmpt (y VARCHAR)", "OK"},
                {"B", "INSERT INTO tmpt VALUES ('b1'), ('b2')", "OK"},
                {"A", "SELECT COUNT(*) FROM tmpt", "1"},
                {"B", "SELECT COUNT(*) FROM tmpt", "2"},
                {"B", "SELECT MAX(y) FROM tmpt", "b2"},
                {"A", "CREATE OR REPLACE TEMPORARY TABLE tmpt (x INT)", "OK"},
                {"A", "INSERT INTO tmpt VALUES (7)", "OK"},
                {"B", "CREATE OR REPLACE TEMPORARY TABLE tmpt (y VARCHAR)", "OK"},
                {"A", "SELECT MAX(x) FROM tmpt", "7"},
                {"B", "SELECT COUNT(*) FROM tmpt", "0"},
                {"A", "DROP TABLE tmpt", "OK"},
                {"B", "INSERT INTO tmpt VALUES ('b3')", "OK"},
                {"B", "SELECT MAX(y) FROM tmpt", "b3"},
                {"A", "SELECT COUNT(*) FROM tmpt", "REFUSED Object 'TMPT' does not exist or not authorized."
                    + " Your primary role <ROLE> must have at least one privilege granted on TABLE TMPT."},
                {"B", "SHOW TABLES LIKE 'TMPT' IN SCHEMA test_db.public", "1 rows"},
                {"A", "SHOW TABLES LIKE 'TMPT' IN SCHEMA test_db.public", "0 rows"},
            });
        }
    }

    @Test
    public void twoSessionsEachHoldATemporaryViewOfOneName() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY VIEW tmpv AS SELECT 1 AS c", "OK"},
                {"B", "CREATE TEMPORARY VIEW tmpv AS SELECT 2 AS c", "OK"},
                {"A", "SELECT c FROM tmpv", "1"},
                {"B", "SELECT c FROM tmpv", "2"},
                {"A", "SELECT COUNT(*) FROM test_db.information_schema.views WHERE table_name = 'TMPV'", "2"},
                {"B", "CREATE OR REPLACE TEMPORARY VIEW tmpv AS SELECT 3 AS c", "OK"},
                {"A", "SELECT c FROM tmpv", "1"},
                {"B", "SELECT c FROM tmpv", "3"},
                {"A", "DROP VIEW tmpv", "OK"},
                {"B", "SELECT c FROM tmpv", "3"},
                {"B", "SHOW VIEWS LIKE 'TMPV' IN SCHEMA test_db.public", "1 rows"},
                {"A", "SHOW VIEWS LIKE 'TMPV' IN SCHEMA test_db.public", "0 rows"},
            });
        }
    }

    @Test
    public void aDroppedSchemaTakesEverySessionsTemporaryTables() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE SCHEMA test_db.tmps2", "OK"},
                {"A", "CREATE TEMPORARY TABLE test_db.tmps2.t (x INT)", "OK"},
                {"B", "CREATE TEMPORARY TABLE test_db.tmps2.t (y INT)", "OK"},
                {"B", "INSERT INTO test_db.tmps2.t VALUES (1)", "OK"},
                {"A", "DROP SCHEMA test_db.tmps2", "OK"},
                {"A", "CREATE SCHEMA test_db.tmps2", "OK"},
                {"A", "CREATE TEMPORARY TABLE test_db.tmps2.t (x INT)", "OK"},
                {"B", "CREATE TEMPORARY TABLE test_db.tmps2.t (y INT)", "OK"},
                {"B", "SELECT COUNT(*) FROM test_db.tmps2.t", "0"},
                {"A", "DROP SCHEMA test_db.tmps2", "OK"},
            });
        }
    }

    @Test
    public void aTemporaryTableHidesAPermanentOneFromItsOwnSessionAlone() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"B", "CREATE TABLE permt (x INT)", "OK"},
                {"B", "INSERT INTO permt VALUES (1), (2)", "OK"},
                {"A", "CREATE TEMPORARY TABLE permt (x INT)", "OK"},
                {"A", "SELECT COUNT(*) FROM permt", "0"},
                {"B", "SELECT COUNT(*) FROM permt", "2"},
                {"A", "INSERT INTO permt VALUES (9)", "OK"},
                {"B", "INSERT INTO permt VALUES (3)", "OK"},
                {"A", "SELECT MAX(x) FROM permt", "9"},
                {"B", "SELECT COUNT(*) FROM permt", "3"},
                {"A", "SHOW TABLES LIKE 'PERMT' IN SCHEMA test_db.public", "1 rows"},
                {"B", "SHOW TABLES LIKE 'PERMT' IN SCHEMA test_db.public", "1 rows"},
                {"A", "DROP TABLE permt", "OK"},
                {"A", "SELECT COUNT(*) FROM permt", "3"},
                {"B", "DROP TABLE permt", "OK"},
            });
        }
    }

    @Test
    public void aTemporaryFileFormatIsItsSessionsAlone() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY FILE FORMAT tff TYPE = CSV", "OK"},
                {"A", "SHOW FILE FORMATS LIKE 'TFF' IN SCHEMA test_db.public", "1 rows"},
                {"B", "SHOW FILE FORMATS LIKE 'TFF' IN SCHEMA test_db.public", "0 rows"},
                {"B", "DESCRIBE FILE FORMAT tff", "REFUSED File format 'TEST_DB.PUBLIC.TFF' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                    + "TEST_DB.PUBLIC.TFF."},
                {"B", "DROP FILE FORMAT tff", "REFUSED File format 'TEST_DB.PUBLIC.TFF' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                    + "TEST_DB.PUBLIC.TFF."},
                {"B", "SELECT COUNT(*) FROM test_db.information_schema.file_formats WHERE file_format_name = 'TFF'",
                    "1"},
                {"A", "SELECT COUNT(*) FROM test_db.information_schema.file_formats WHERE file_format_name = 'TFF'",
                    "1"},
                {"A", "DESCRIBE FILE FORMAT tff", "OK"},
            });
        }
    }

    @Test
    public void aTemporaryFileFormatEndsWithItsSession() throws SQLException {
        try (Connection first = another(); Statement a = first.createStatement()) {
            a.execute("CREATE TEMPORARY FILE FORMAT tff2 TYPE = CSV");
        }
        try (Connection later = another(); Statement c = later.createStatement()) {
            assertSteps(c, c, new String[][] {
                {"A", "SHOW FILE FORMATS LIKE 'TFF2' IN SCHEMA test_db.public", "0 rows"},
            });
        }
    }

    /** Two files to stage, holding 1,a and 2,b, in a directory of their own. */
    private static Path[] stagedFiles() throws IOException {
        final Path dir = Files.createTempDirectory("tmp_stage_files_");
        return new Path[] {Files.writeString(dir.resolve("fa.csv"), "1,a\n"),
            Files.writeString(dir.resolve("fb.csv"), "2,b\n")};
    }

    private static void deleteStaged(final Path[] files) throws IOException {
        for (final Path file : files) {
            Files.deleteIfExists(file);
        }
        Files.deleteIfExists(files[0].getParent());
    }

    @Test
    public void twoSessionsEachHoldATemporaryStageOfOneName() throws SQLException, IOException {
        final Path[] files = stagedFiles();
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            final String missing = "REFUSED Stage 'TEST_DB.PUBLIC.TMS' does not exist or not authorized. Your primary"
                + " role <ROLE> must have at least one privilege granted on STAGE TEST_DB.PUBLIC.TMS.";
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY STAGE tms", "OK"},
                {"B", "CREATE TEMPORARY STAGE tms", "OK"},
                {"A", "PUT 'file://" + files[0] + "' @tms AUTO_COMPRESS = FALSE", "OK"},
                {"B", "PUT 'file://" + files[1] + "' @tms AUTO_COMPRESS = FALSE", "OK"},
                {"A", "SELECT $1 FROM @tms", "1"},
                {"B", "SELECT $1 FROM @tms", "2"},
                {"C", "SELECT $1 FROM @tms", missing},
                {"A", "SHOW STAGES LIKE 'TMS' IN SCHEMA test_db.public", "1 rows"},
                {"C", "SHOW STAGES LIKE 'TMS' IN SCHEMA test_db.public", "0 rows"},
                {"A", "DROP STAGE tms", "OK"},
                {"B", "SELECT $1 FROM @tms", "2"},
                {"A", "SELECT $1 FROM @tms", missing},
            });
        } finally {
            deleteStaged(files);
        }
    }

    @Test
    public void aTemporaryStageHidesAPermanentOneFromItsOwnSessionAlone() throws SQLException, IOException {
        final Path[] files = stagedFiles();
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            final String counted = "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";
            assertSteps(a, b, new String[][] {
                {"C", "CREATE OR REPLACE STAGE pst2", "OK"},
                {"C", "PUT 'file://" + files[1] + "' @pst2 AUTO_COMPRESS = FALSE", "OK"},
                {"A", "CREATE OR REPLACE TEMPORARY STAGE pst2", "OK"},
                {"A", "LIST @pst2", "OK"},
                {"A", counted, "0"},
                {"B", "LIST @pst2", "OK"},
                {"B", counted, "1"},
                {"A", "SHOW STAGES LIKE 'PST2' IN SCHEMA test_db.public", "1 rows"},
                {"A", "DROP STAGE pst2", "OK"},
                {"A", "LIST @pst2", "OK"},
                {"A", counted, "1"},
                {"A", "CREATE TEMPORARY STAGE IF NOT EXISTS pst2", "OK"},
                {"A", "LIST @pst2", "OK"},
                {"A", counted, "0"},
                {"A", "CREATE TEMPORARY STAGE pst2", "REFUSED Object 'PST2' already exists."},
            });
        } finally {
            deleteStaged(files);
        }
    }

    @Test
    public void aSessionSwapsItsOwnTemporaryTables() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY TABLE sa (i INT)", "OK"},
                {"A", "INSERT INTO sa VALUES (1)", "OK"},
                {"A", "CREATE TEMPORARY TABLE sb (i INT)", "OK"},
                {"A", "INSERT INTO sb VALUES (2), (3)", "OK"},
                {"A", "ALTER TABLE sa SWAP WITH sb", "OK"},
                {"A", "SELECT COUNT(*) FROM sa", "2"},
                {"A", "SELECT COUNT(*) FROM sb", "1"},
                {"B", "SELECT COUNT(*) FROM sa", "REFUSED Object 'SA' does not exist or not authorized. Your primary"
                    + " role <ROLE> must have at least one privilege granted on TABLE SA."},
                {"C", "CREATE OR REPLACE TABLE sc (i INT)", "OK"},
                {"C", "INSERT INTO sc VALUES (7), (8), (9)", "OK"},
                {"A", "CREATE TEMPORARY TABLE sc (i INT)", "OK"},
                {"A", "ALTER TABLE sa SWAP WITH sc", "OK"},
                {"A", "SELECT COUNT(*) FROM sc", "2"},
                {"A", "SELECT COUNT(*) FROM sa", "0"},
                {"C", "SELECT COUNT(*) FROM sc", "3"},
            });
        }
    }
}
