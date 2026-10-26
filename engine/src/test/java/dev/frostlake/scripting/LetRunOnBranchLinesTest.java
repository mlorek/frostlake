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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines after a LET's value run into the next statement: in the block's own list, and in an IF's or a CASE
 * statement's branch and a REPEAT's body, where the block's lines are read and the last moves on past the first
 * name after it - past the statement's semicolon in an IF or a CASE, not in a REPEAT (all live-verified).
 */
public class LetRunOnBranchLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void runOnLines() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 32 unexpected '('.",
            "BEGIN LET a := 1 CREATE TABLE u (a INT); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 32 unexpected '('.",
            "BEGIN LET a := 1 CREATE TABLE u (a INT, b INT); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 35 unexpected 'TABLE'. | syntax error line 1 at position 43 "
                + "unexpected '('.",
            "BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT, b INT); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 31 unexpected 'VALUES'.",
            "BEGIN LET a := 1 INSERT INTO u VALUES (1); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 35 unexpected 'VIEW'. | syntax error line 1 at position 40 "
                + "unexpected 'v'.",
            "BEGIN LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 32 unexpected '('.",
            "BEGIN LET a := 1 CREATE TABLE u (a INT); RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 26 unexpected 'SET'.",
            "BEGIN LET a := 1 UPDATE u SET a = 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'DELETE'. | "
                + "syntax error line 1 at position 61 unexpected 'END'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 DELETE FROM u; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 66 unexpected 'END'.",
            "BEGIN IF (TRUE) THEN LET a := 1 INSERT INTO u VALUES (1); END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 50 unexpected 'VIEW'. | syntax error line 1 at position 78 "
                + "unexpected 'END'.",
            "BEGIN IF (TRUE) THEN LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 50 unexpected 'INT'.",
            "BEGIN IF (TRUE) THEN LET a := 1 CREATE TABLE u (a INT, b INT); END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 50 unexpected 'TABLE'. | syntax error line 1 at position 61 "
                + "unexpected 'INT'.",
            "BEGIN IF (TRUE) THEN LET a := 1 CREATE OR REPLACE TABLE u (a INT, b INT, c INT); END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 47 unexpected '='.",
            "BEGIN IF (TRUE) THEN LET a := 1 UPDATE u SET a = 1; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'INSERT'.",
            "BEGIN REPEAT LET a := 1 INSERT INTO u VALUES (1); UNTIL (TRUE) END REPEAT; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 42 unexpected 'VIEW'.",
            "BEGIN REPEAT LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; UNTIL (TRUE) END REPEAT; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 73 unexpected 'END'.",
            "BEGIN CASE WHEN TRUE THEN LET a := 1 INSERT INTO u VALUES (1); END CASE; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 55 unexpected 'INT'.",
            "BEGIN CASE WHEN TRUE THEN LET a := 1 CREATE TABLE u (a INT); END CASE; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 37 unexpected 'VALUES'.",
            "BEGIN BEGIN LET a := 1 INSERT INTO u VALUES (1); END; END");
    }
}
