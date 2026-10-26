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
 * The lines after a LET's value run into the next statement in a WHILE's or a LOOP's body, where they go on from the
 * value's own fault and a statement read on at a name passes that name over, and in a FOR's body and a nested
 * block, where the block's own lines are read and one more follows them (all live-verified).
 */
public class LetRunOnLoopLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void loopBodies() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'TABLE'. | syntax error line 1 at position 62 "
                + "unexpected '('. | syntax error line 1 at position 65 unexpected 'INT'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE OR REPLACE TABLE u (a INT, b INT); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'INT'. | syntax error line 1 at position 57 "
                + "unexpected ')'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE TABLE u (a INT); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'VIEW'. | syntax error line 1 at position 61 "
                + "unexpected 'AS'. | syntax error line 1 at position 84 unexpected 'END'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 40 unexpected 'TABLE'. | syntax error line 1 at position 48 "
                + "unexpected '('. | syntax error line 1 at position 51 unexpected 'INT'.",
            "BEGIN LOOP LET a := 1 CREATE OR REPLACE TABLE u (a INT); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'TABLE'. | syntax error line 1 at position 62 "
                + "unexpected '('. | syntax error line 1 at position 65 unexpected 'VARCHAR'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE OR REPLACE TABLE u (a VARCHAR(10)); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 50 unexpected 'VALUES'. | syntax error line 1 at position 72 "
                + "unexpected 'END'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 INSERT INTO u VALUES (1); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 50 unexpected '('. | syntax error line 1 at position 52 "
                + "unexpected ')'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 INSERT INTO u (a) VALUES (1, 2); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'INT'. | syntax error line 1 at position 57 "
                + "unexpected ','.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE TABLE u (a INT, b INT); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 51 unexpected '='. | syntax error line 1 at position 66 "
                + "unexpected 'END'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 UPDATE u SET a = 1; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'TABLE'. | syntax error line 1 at position 62 "
                + "unexpected '('. | syntax error line 1 at position 65 unexpected 'INT'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE OR REPLACE TABLE u (a INT, b INT, c INT); END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 40 unexpected 'VIEW'. | syntax error line 1 at position 47 "
                + "unexpected 'AS'. | syntax error line 1 at position 70 unexpected 'END'.",
            "BEGIN LOOP LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 37 unexpected '='. | syntax error line 1 at position 52 "
                + "unexpected 'END'.",
            "BEGIN LOOP LET a := 1 UPDATE u SET a = 1; END LOOP; END");
    }

    @Test
    public void forBodiesAndNestedBlocks() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 41 unexpected 'TABLE'. | syntax error line 1 at position 49 "
                + "unexpected '('. | syntax error line 1 at position 52 unexpected 'INT'.",
            "BEGIN BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT); END; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'TABLE'. | syntax error line 1 at position 62 "
                + "unexpected '('. | syntax error line 1 at position 65 unexpected 'INT'.",
            "BEGIN FOR i IN 1 TO 2 DO LET a := 1 CREATE OR REPLACE TABLE u (a INT, b INT); END FOR; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 41 unexpected 'TABLE'. | syntax error line 1 at position 49 "
                + "unexpected '('. | syntax error line 1 at position 52 unexpected 'INT'.",
            "BEGIN BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT, b INT); END; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 38 unexpected '('. | syntax error line 1 at position 41 "
                + "unexpected 'INT'.",
            "BEGIN BEGIN LET a := 1 CREATE TABLE u (a INT); END; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'INSERT'. | "
                + "syntax error line 1 at position 50 unexpected 'VALUES'.",
            "BEGIN FOR i IN 1 TO 2 DO LET a := 1 INSERT INTO u VALUES (1); END FOR; END");
    }
}
