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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A temporary function or procedure is its own session's alone (live-verified): another session neither calls nor
 * lists it - INFORMATION_SCHEMA.FUNCTIONS alone lists it everywhere - creates its own under the same name and
 * signature, and drops only its own. A temporary routine may not take a permanent routine's signature; a permanent
 * one created after it is the one every session calls, its creator's included.
 */
public class TemporaryRoutinesPerSessionTest extends BaseJdbcTest {

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
    public void anotherSessionNeitherCallsNorListsATemporaryRoutine() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"A", "CREATE TEMPORARY FUNCTION tmpf() RETURNS NUMBER AS '1'", "OK"},
                {"A", "CREATE TEMPORARY PROCEDURE tmpp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$",
                    "OK"},
                {"A", "SELECT tmpf()", "1"},
                {"B", "SELECT tmpf()", "REFUSED Unknown function TMPF."},
                {"B", "CALL tmpp()", "REFUSED Unknown function TMPP."},
                {"C", "SELECT tmpf()", "REFUSED Unknown function TMPF."},
                {"B", "SHOW USER FUNCTIONS LIKE 'TMPF' IN SCHEMA test_db.public", "0 rows"},
                {"B", "SHOW PROCEDURES LIKE 'TMPP' IN SCHEMA test_db.public", "0 rows"},
                {"A", "SHOW USER FUNCTIONS LIKE 'TMPF' IN SCHEMA test_db.public", "1 rows"},
                {"B", "SELECT COUNT(*) FROM test_db.information_schema.functions WHERE function_name = 'TMPF'", "1"},
                {"B", "SELECT COUNT(*) FROM test_db.information_schema.procedures WHERE procedure_name = 'TMPP'",
                    "0"},
                {"A", "SELECT COUNT(*) FROM test_db.information_schema.functions WHERE function_name = 'TMPF'", "1"},
                {"B", "CREATE TEMPORARY FUNCTION tmpf() RETURNS NUMBER AS '2'", "OK"},
                {"B", "CREATE TEMPORARY PROCEDURE tmpp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 2; END; $$",
                    "OK"},
                {"B", "SELECT tmpf()", "2"},
                {"A", "SELECT tmpf()", "1"},
                {"B", "CALL tmpp()", "2"},
                {"A", "CALL tmpp()", "1"},
                {"B", "DROP FUNCTION tmpf()", "OK"},
                {"B", "DROP PROCEDURE tmpp()", "OK"},
                {"A", "SELECT tmpf()", "1"},
                {"A", "CALL tmpp()", "1"},
                {"B", "SELECT tmpf()", "REFUSED Unknown function TMPF."},
                {"B", "DROP FUNCTION tmpf()", "REFUSED Function 'TEST_DB.PUBLIC.TMPF' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                    + "TEST_DB.PUBLIC.TMPF."},
                {"A", "CREATE TEMPORARY FUNCTION tmpf() RETURNS NUMBER AS '3'",
                    "REFUSED Object 'TMPF' already exists."},
                {"A", "CREATE TEMPORARY PROCEDURE tmpp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 3; END; $$",
                    "REFUSED Object 'TMPP' already exists."},
                {"A", "CREATE OR REPLACE TEMPORARY FUNCTION tmpf() RETURNS NUMBER AS '7'", "OK"},
                {"A", "SELECT tmpf()", "7"},
            });
        }
    }

    @Test
    public void aPermanentRoutineAndATemporaryOneOfTheSameSignature() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            assertSteps(a, b, new String[][] {
                {"B", "CREATE FUNCTION permf() RETURNS NUMBER AS '3'", "OK"},
                {"A", "CREATE TEMPORARY FUNCTION permf() RETURNS NUMBER AS '4'",
                    "REFUSED Object 'PERMF():NUMBER(38,0)' already exists."},
                {"B", "CREATE PROCEDURE permp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 3; END; $$", "OK"},
                {"A", "CREATE TEMPORARY PROCEDURE permp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 4; END; $$",
                    "REFUSED Object 'PERMP():NUMBER(38,0)' already exists."},
                {"A", "CREATE TEMPORARY FUNCTION latef() RETURNS NUMBER AS '5'", "OK"},
                {"B", "CREATE FUNCTION latef() RETURNS NUMBER AS '6'", "OK"},
                {"A", "SELECT latef()", "6"},
                {"B", "SELECT latef()", "6"},
                {"C", "SELECT latef()", "6"},
                {"A", "SHOW USER FUNCTIONS LIKE 'LATEF' IN SCHEMA test_db.public", "1 rows"},
                {"A", "CREATE TEMPORARY PROCEDURE latep() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 5; END; $$",
                    "OK"},
                {"B", "CREATE PROCEDURE latep() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 6; END; $$", "OK"},
                {"A", "CALL latep()", "6"},
                {"B", "CALL latep()", "6"},
            });
        }
    }
}
