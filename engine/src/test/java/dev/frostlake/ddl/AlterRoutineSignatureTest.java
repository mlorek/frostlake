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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER FUNCTION and ALTER PROCEDURE name one overload by its signature, which is required. RENAME TO renames
 * that overload alone, leaving its siblings, and takes its arguments as a CREATE writes them — named, with a
 * default, a required one never after an optional one — while every other action takes the types alone. Every
 * cell is live-verified.
 */
public class AlterRoutineSignatureTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(hinted(cell[1]), answer(cell[0]), cell[0]);
        }
    }

    private static String syntax(final int position, final String token) {
        return ERROR + "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    /** Each user function's name and argument count, in listing order. */
    private String functions() {
        final StringBuilder listed = new StringBuilder();
        for (final Row row : engine.executeQuery("SHOW USER FUNCTIONS").getRows()) {
            listed.append(listed.length() > 0 ? " " : "").append(row.getValue(1)).append(':').append(row.getValue(6));
        }
        return listed.toString();
    }

    @Test
    public void aRenameNamesOneOverloadAndLeavesItsSiblings() {
        engine.execute("CREATE FUNCTION f1() RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION f1(x INT) RETURNS INT AS $$ 2 $$");
        engine.execute("CREATE FUNCTION f2() RETURNS INT AS $$ 3 $$");
        engine.execute("CREATE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        engine.execute("CREATE PROCEDURE p1(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 2; END; $$");
        assertCells(new String[][] {
            {"ALTER FUNCTION f1() RENAME TO f1", ERROR + "Object 'F1' already exists."},
            {"ALTER FUNCTION f1() RENAME TO f2", ERROR + "Object 'F2' already exists."},
            {"ALTER PROCEDURE p1() RENAME TO p1", ERROR + "Object 'P1' already exists."},
            {"ALTER FUNCTION f1(VARCHAR) RENAME TO f4", ERROR + "Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not authorized."},
        });
        engine.execute("ALTER FUNCTION IF EXISTS f1(VARCHAR) RENAME TO f4");
        engine.execute("ALTER FUNCTION f1(INT) RENAME TO f3");
        engine.execute("ALTER PROCEDURE p1(INT) RENAME TO p3");
        assertEquals("F1:0 F2:0 F3:1", functions());
        assertCells(new String[][] {
            {"SELECT f1()", "1"},
            {"SELECT f3(5)", "2"},
            {"CALL p1()", "1"},
            {"CALL p3(1)", "2"},
            {"CALL p1(1)", "SQL compilation error: error line 0 at position -1|too many arguments for function [P1(1)] expected 0, got 1"},
        });
        engine.execute("ALTER FUNCTION f3(INT) RENAME TO test_db.test_schema.f5");
        assertEquals("2", answer("SELECT f5(1)"));
    }

    @Test
    public void aRenameTakesArgumentsAsACreateWritesThem() {
        engine.execute("CREATE FUNCTION g1(x INT, y VARCHAR) RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE PROCEDURE p1(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        engine.execute("ALTER FUNCTION g1(x INT, VARCHAR) RENAME TO g2");
        engine.execute("ALTER FUNCTION g2(INT, y VARCHAR) RENAME TO g3");
        engine.execute("ALTER FUNCTION g3(x INT, y VARCHAR) RENAME TO g4");
        engine.execute("ALTER FUNCTION g4(\"x\" INT, y VARCHAR) RENAME TO g5");
        engine.execute("ALTER PROCEDURE p1(x INT) RENAME TO p2");
        assertCells(new String[][] {
            {"SELECT g5(1, 'a')", "1"},
            {"CALL p2(1)", "1"},
            {"ALTER FUNCTION g5(x INT DEFAULT 1, y VARCHAR) RENAME TO g6",
                "SQL compilation error: error line 1 at position 35|required argument Y cannot follow optional argument X"},
            {"ALTER FUNCTION nosuch(x INT DEFAULT 1, y VARCHAR) RENAME TO z",
                "SQL compilation error: error line 1 at position 39|required argument Y cannot follow optional argument X"},
            {"ALTER PROCEDURE nosuch(x INT DEFAULT 1, y VARCHAR) RENAME TO z",
                "SQL compilation error: error line 1 at position 40|required argument Y cannot follow optional argument X"},
            {"ALTER FUNCTION nosuch(x INT, y VARCHAR) RENAME TO z", ERROR + "Function 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."},
            {"ALTER FUNCTION g5(x INT DEFAULT 1, y INT DEFAULT 2) RENAME TO g8",
                ERROR + "Function 'TEST_DB.TEST_SCHEMA.G5' does not exist or not authorized."},
            {"ALTER FUNCTION g5(INT DEFAULT 1) RENAME TO g9", syntax(22, "DEFAULT")},
            {"ALTER FUNCTION g5(x INT := 1) RENAME TO g9", syntax(20, "INT")},
        });
    }

    @Test
    public void anyOtherActionTakesTypesAloneAndEveryActionASignature() {
        engine.execute("CREATE FUNCTION g5(x INT, y VARCHAR) RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE PROCEDURE p2(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        assertCells(new String[][] {
            {"ALTER FUNCTION g5(x INT, y VARCHAR) SET COMMENT = 'n'", syntax(20, "INT")},
            {"ALTER FUNCTION g5(x varchar) SET COMMENT = 'x'", syntax(20, "varchar")},
            {"ALTER FUNCTION g5(INT, x INT) SET COMMENT = 'x'", syntax(25, "INT")},
            {"ALTER FUNCTION g5(x NUMBER(10,2)) SET COMMENT = 'x'", syntax(20, "NUMBER")},
            {"ALTER FUNCTION g5(x INT, y VARCHAR) UNSET COMMENT", syntax(20, "INT")},
            {"ALTER FUNCTION g5(x INT, y VARCHAR) SET SECURE", syntax(20, "INT")},
            {"ALTER PROCEDURE p2(x INT) SET COMMENT = 'p'", syntax(21, "INT")},
            {"ALTER FUNCTION g5(INT DEFAULT 1) SET COMMENT = 'x'", syntax(22, "DEFAULT")},
            {"ALTER FUNCTION g5 RENAME TO g6", syntax(18, "RENAME")},
            {"ALTER FUNCTION IF EXISTS g5 RENAME TO g6", syntax(28, "RENAME")},
            {"ALTER FUNCTION g5 SET COMMENT = 'x'", syntax(18, "SET")},
            {"ALTER PROCEDURE p2 RENAME TO p9", syntax(19, "RENAME")},
        });
        engine.execute("ALTER FUNCTION g5(INT, VARCHAR) SET COMMENT = 'typed'");
        assertEquals("typed", String.valueOf(engine.executeQuery("SHOW USER FUNCTIONS LIKE 'G5'").getRows().get(0).getValue(9)));
    }
}
