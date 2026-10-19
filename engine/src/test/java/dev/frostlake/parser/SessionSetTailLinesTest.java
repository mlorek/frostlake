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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session SET whose name its '=' does not follow: what the account reports after the name when a bracket or
 * a comma stands further on, a schema's variable, a doubled '=', binds, and a ':=' SET in a block
 * (live-verified).
 */
public class SessionSetTailLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBracketOrACommaAfterTheName() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected '<EOF>'.",
                "SET x (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected ';'. | syntax error line 1 at position 22 unexpected "
                    + "'y'.",
                "SET x (1); SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected ';'. | syntax error line 1 at position 32 unexpected "
                    + "'y'.",
                "SET x (1); SELECT 2; SELECT 3 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected 'y'.",
                "SET x (1) y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 8 unexpected '<EOF>'.",
                "SET x ()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '2'. | syntax error line 1 at position 12 unexpected "
                    + "'<EOF>'.",
                "SET x (1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected '<EOF>'.",
                "SET x,");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 8 unexpected '<EOF>'.",
                "SET x, y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected '='.",
                "SET x, y = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected '='.",
                "SET x, y = (1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 7 unexpected '<EOF>'.",
                "SET x )");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected ']'.",
                "SET x ]");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 12 unexpected '<EOF>'.",
                "SET x IN (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected ')'. | syntax "
                    + "error line 1 at position 6 unexpected '<EOF>'.",
                "SET ()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x (1) = 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x (");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x [1]");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '('.",
                "SET x (1) (2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 26 unexpected 'y'.",
                "SET x (1) = 2; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '2'. | syntax error line 1 at position 13 unexpected "
                    + "'3'. | syntax error line 1 at position 15 unexpected '<EOF>'.",
                "SET x (1, 2, 3)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 11 unexpected '<EOF>'.",
                "SET x (1 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected '<EOF>'.",
                "SET x (y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected ')'.",
                "SET x ((1))");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 16 unexpected '<EOF>'.",
                "SET x (SELECT 1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 12 unexpected '<EOF>'.",
                "SET x (1) = ");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 11 unexpected ';'. | syntax error line 1 at position 24 unexpected "
                    + "'y'.",
                "SET x (1) =; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected 'y'. | syntax error line 1 at position 12 unexpected "
                    + "'z'.",
                "SET x (1) y z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected ','.",
                "SET x (1), y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 11 unexpected '<EOF>'.",
                "SET x, y, z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 12 unexpected '='.",
                "SET x, y, z = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected '<EOF>'.",
                "SET x, y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 7 unexpected '('. | syntax error line 1 at position 10 unexpected "
                    + "'<EOF>'.",
                "SET x, (y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 7 unexpected '1'.",
                "SET x, 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected ','. | syntax error line 1 at position 9 unexpected "
                    + "'<EOF>'.",
                "SET x,, y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 8 unexpected ';'.",
                "SET x, y; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected '='. | syntax error line 1 at position 25 unexpected "
                    + "'y'.",
                "SET x, y = 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 5 unexpected ';'.",
                "SET x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x y z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x 'a'");
        } finally {
            answer(
                "UNSET x");
            answer(
                "UNSET y");
            answer(
                "UNSET z");
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void schemaVariablesAndTheSigns() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y' does not exist or not authorized.{2}",
                "SET x.y = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '<EOF>'.",
                "SET x.y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
                "SET x.y 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'.",
                "SET x.y = 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y.Z' does not exist or not authorized.{2}",
                "SET x.y.z = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'z'.",
                "SET x.y = 1 z");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable '\"x\".\"y\"' does not exist or not "
                    + "authorized.{2}",
                "SET \"x\".\"y\" = 1");
            assertAnswer(
                "Statement executed successfully.",
                "SET x == 1");
            assertAnswer(
                "1",
                "SELECT $x");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'X'",
                "BEGIN SET x := 1; RETURN 1; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '<EOF>'.",
                "SET x =");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
                "SET x ==");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '='.",
                "SET x === 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '='.",
                "SET x = = 1");
            assertAnswer(
                "Statement executed successfully.",
                "SET x == 1 + 1");
            assertAnswer(
                "Statement executed successfully.",
                "SET (x) == (1)");
            assertAnswer(
                "Statement executed successfully.",
                "SET (x, y) == (1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x := 1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Bind variable :1 not set.",
                "SET x =: 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x => 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '=='.",
                "SET x == == 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y' does not exist or not authorized.{2}",
                "SET x.y == 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected ':='.",
                "SET x.y := 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '.'.",
                "SET .y = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '='.",
                "SET x. = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '.'.",
                "SET x..y = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y.Z.W' does not exist or not authorized.{2}",
                "SET x.y.z.w = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y' does not exist or not authorized.{2}",
                "SET (x.y) = (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y' does not exist or not authorized.{2}",
                "SET (x.y, z) = (1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'TEST_SCHEMA.V' does not exist or not "
                    + "authorized.{2}",
                "SET test_schema.v = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'TEST_DB.TEST_SCHEMA.V' does not exist or not "
                    + "authorized.{2}",
                "SET test_db.test_schema.v = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'NOSUCHDB.S.V' does not exist or not "
                    + "authorized.{2}",
                "SET nosuchdb.s.v = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y' does not exist or not authorized.{2}",
                "UNSET x.y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
                "SET x.y = ");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ','.",
                "SET x.y, z = 1");
        } finally {
            answer(
                "UNSET x");
            answer(
                "UNSET y");
            answer(
                "UNSET z");
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void aSetInABlock() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'X'",
                "BEGIN SET x := 1; END");
            assertAnswer(
                "1",
                "BEGIN LET x INT := 0; SET x := 1; RETURN x; END");
            assertAnswer(
                "1",
                "BEGIN SET x = 1; RETURN 1; END");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 12 | invalid identifier 'X'",
                "BEGIN SET x := 1 + 1; RETURN 1; END");
            assertAnswer(
                "5",
                "DECLARE x INT; BEGIN SET x := 5; RETURN x; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ':='.",
                "BEGIN SET (x, y) := (1, 2); RETURN 1; END");
            assertAnswer(
                "0",
                "BEGIN LET x INT := 0; SET x = 1; RETURN x; END");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'Y'",
                "BEGIN LET x INT := 0; SET y := x; RETURN x; END");
        } finally {
            answer(
                "UNSET x");
            answer(
                "UNSET y");
            answer(
                "UNSET z");
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void laterStatementsListsAndBinds() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected 'y'. | syntax error line 1 at position 24 unexpected "
                    + "'y'.",
                "SET x (1) y; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected 'z'.",
                "SET x, y z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected 'z'.",
                "SET x, y z = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected 'y'. | syntax error line 1 at position 12 unexpected "
                    + "'z'.",
                "SET x (1) y z w");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected ')'. | syntax error line 1 at position 7 unexpected "
                    + "'<EOF>'.",
                "SET x,)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected ')'.",
                "SET x,) = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected ')'.",
                "SET x (1))");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 14 unexpected 'z'.",
                "SET x (1) = 2 z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '('.",
                "SET (x, y) (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
                "SET (x) 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'.",
                "SET x = 1 (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected ']'.",
                "SET x ] y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 6 unexpected ']'.",
                "SET x ]]");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x [");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x }");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x {");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x ) = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 8 unexpected 'y'.",
                "SET x ) y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x IN (1) = 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x 1 = 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
                "SET x y = 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '<EOF>'.",
                "SET x 1, y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '<EOF>'.",
                "SET x y, z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 30 unexpected 'y'.",
                "SET x 1; SELECT 2; SELECT 3 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 8 unexpected ';'. | syntax error line 1 at position 31 unexpected "
                    + "'y'.",
                "SET x, y; SELECT 2; SELECT 3 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '('. | syntax error line 1 at position 15 unexpected "
                    + "'3'.",
                "SET x (1) (2) (3)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected '1'.",
                "SET x (1) 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 10 unexpected ''a''. | syntax error line 1 at position 14 unexpected "
                    + "'b'.",
                "SET x (1) 'a' b");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 13 unexpected ','.",
                "SET x (1) = 2, y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                    + "error line 1 at position 19 unexpected '<EOF>'.",
                "SELECT 1; SET x (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                    + "error line 1 at position 16 unexpected 'x'. | syntax error line 1 at position 21 unexpected "
                    + "'<EOF>'.",
                "SELECT 1 +; SET x (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                    + "error line 1 at position 9 unexpected ';'. | syntax error line 1 at position 15 unexpected "
                    + "'y'. | syntax error line 1 at position 20 unexpected '<EOF>'.",
                "SET x (1); SET y (2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
                "SET (x, y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'.",
                "SET (x, y) =");
            assertAnswer(
                "REFUSED SQL compilation error: Number of variables on left-hand side of assignment (2) is not "
                    + "equal to the number of values on right-hand side (1).",
                "SET (x, y) = 1");
            assertAnswer(
                "REFUSED SQL compilation error: Number of variables on left-hand side of assignment (2) is not "
                    + "equal to the number of values on right-hand side (1).",
                "SET (x, y) = (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
                "SET (x, 1) = (1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ')'.",
                "SET (x,) = (1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'y'.",
                "UNSET x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ','.",
                "UNSET x, y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'z'.",
                "UNSET (x, y) z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected '<EOF>'.",
                "UNSET");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema Variable 'X.Y' does not exist or not authorized.{2}",
                "UNSET (x.y, z)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 8 | Bind variable :1 not set.",
                "SET x = :1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 8 | Bind variable :nosuch not set.",
                "SET x = :nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 8 | Bind variable ? not set.",
                "SET x = ?");
        } finally {
            answer(
                "UNSET x");
            answer(
                "UNSET y");
            answer(
                "UNSET z");
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void whatStillSets() {
        try {
            assertAnswer(
                "1",
                "BEGIN LET v INT := 5; SET x = :v; RETURN 1; END");
            assertAnswer(
                "5",
                "SELECT $x");
            assertAnswer(
                "Statement executed successfully.",
                "SET (x, y) = (1, 2)");
            assertAnswer(
                "1|2",
                "SELECT $x, $y");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET (x, y)");
            assertAnswer(
                "Statement executed successfully.",
                "SET x == 5");
            assertAnswer(
                "5",
                "SELECT $x");
            assertAnswer(
                "Statement executed successfully.",
                "SET (x, y) == (7, 8)");
            assertAnswer(
                "7|8",
                "SELECT $x, $y");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET (x, y)");
            assertAnswer(
                "Statement executed successfully.",
                "SET (x) = 3");
            assertAnswer(
                "3",
                "SELECT $x");
            assertAnswer(
                "REFUSED SQL compilation error: Number of variables on left-hand side of assignment (1) is not "
                    + "equal to the number of values on right-hand side (2).",
                "SET (x) = (1, 2)");
        } finally {
            answer(
                "UNSET x");
            answer(
                "UNSET y");
        }
    }
}
