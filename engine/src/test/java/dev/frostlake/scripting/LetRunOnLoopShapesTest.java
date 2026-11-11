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
 * The lines after a LET's value run into the next statement in more loop and block shapes (live-verified): a WHILE's
 * body closed with END WHILE, a FOR's body written with LOOP, an INSERT in a FOR's body, an UPDATE and a DELETE in a
 * nested block, a RETURN's value run on in a loop, a nested block and a FOR's body, and a second run-on after a loop;
 * a FOR's body read as a loop's (a VIEW, an UPDATE, a DELETE), a nested block's VIEW, a MERGE's USING, a value read
 * on whole in a loop, and a value ending in a word in a loop's body and a FOR's.
 */
public class LetRunOnLoopShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void moreLoopAndBlockShapes() {
        assertCells(new String[][] {
            {"BEGIN WHILE (FALSE) DO LET a := 1 CREATE OR REPLACE TABLE u (a INT); END WHILE; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 52 unexpected 'TABLE'. | syntax error line 1 at position 60 unexpected "
                + "'('. | syntax error line 1 at position 63 unexpected 'INT'."},
            {"BEGIN WHILE (FALSE) DO LET a := 1 UPDATE u SET a = 1; END WHILE; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 49 unexpected '='. | syntax error line 1 at position 65 unexpected 'END'."},
            {"BEGIN FOR i IN 1 TO 2 LOOP LET a := 1 CREATE OR REPLACE TABLE u (a INT); END LOOP; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 56 unexpected 'TABLE'. | syntax error line 1 at position 64 unexpected "
                + "'('. | syntax error line 1 at position 67 unexpected 'INT'."},
            {"BEGIN FOR i IN 1 TO 2 LOOP LET a := 1 INSERT INTO u VALUES (1); END LOOP; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 52 unexpected 'VALUES'. | syntax error line 1 at position 74 unexpected "
                + "'END'."},
            {"BEGIN BEGIN LET a := 1 UPDATE u SET a = 1; END; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 32 unexpected 'SET'. | syntax error line 1 at position 38 unexpected '='."},
            {"BEGIN BEGIN LET a := 1 DELETE FROM u WHERE a = 1; END; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'DELETE'. | syntax "
                + "error line 1 at position 37 unexpected 'WHERE'. | syntax error line 1 at position 45 unexpected "
                + "'='."},
            {"BEGIN WHILE (FALSE) LOOP RETURN 1 CREATE OR REPLACE TABLE u (a INT); END LOOP; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 52 unexpected 'TABLE'. | syntax error line 1 at position 60 unexpected "
                + "'('. | syntax error line 1 at position 63 unexpected 'INT'."},
            {"BEGIN BEGIN RETURN 1 CREATE OR REPLACE TABLE u (a INT); END; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 39 unexpected 'TABLE'. | syntax error line 1 at position 47 unexpected "
                + "'('. | syntax error line 1 at position 50 unexpected 'INT'."},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN 1 INSERT INTO u VALUES (1); END FOR; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 48 unexpected 'VALUES'."},
            {"BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE TABLE u (a INT); END LOOP; LET b := 1 CREATE TABLE w "
                + "(b INT); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 54 unexpected 'INT'. | syntax error line 1 at position 57 unexpected ')'."},
        });
    }

    @Test
    public void forBodiesReadAsLoopsAndTheRefittedShapes() {
        assertCells(new String[][] {
            {"BEGIN FOR i IN 1 TO 2 DO LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END FOR; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 54 unexpected 'VIEW'. | syntax error line 1 at position 61 "
                + "unexpected 'AS'."},
            {"BEGIN FOR i IN 1 TO 2 DO LET a := 1 UPDATE u SET a = 1; END FOR; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 51 unexpected '='."},
            {"BEGIN FOR i IN 1 TO 2 DO LET a := 1 DELETE FROM u WHERE a = 1; END FOR; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'DELETE'. | "
                + "syntax error line 1 at position 58 unexpected '='."},
            {"BEGIN BEGIN LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 41 unexpected 'VIEW'. | syntax error line 1 at position 46 "
                + "unexpected 'v'. | syntax error line 1 at position 48 unexpected 'AS'."},
            {"BEGIN WHILE (FALSE) LOOP LET a := 1 MERGE INTO u USING w ON u.a = w.a WHEN MATCHED THEN DELETE; END "
                + "LOOP; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'MERGE'. | syntax "
                + "error line 1 at position 49 unexpected 'USING'. | syntax error line 1 at position 55 unexpected "
                + "'w'."},
            {"BEGIN WHILE (FALSE) LOOP LET a := 1 SELECT 1 INTO :a; END LOOP; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'END'."},
            {"BEGIN WHILE (FALSE) LOOP LET a := b CREATE TABLE u (a INT); END LOOP; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 51 unexpected '('. | syntax error line 1 at position 54 "
                + "unexpected 'INT'."},
            {"BEGIN FOR i IN 1 TO 2 DO LET a := b CREATE TABLE u (a INT); END FOR; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 51 unexpected '('. | syntax error line 1 at position 54 "
                + "unexpected 'INT'."},
        });
    }
}
