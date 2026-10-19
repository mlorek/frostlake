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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * After a DESCRIBE's object the words are read in pairs to the end of the statement (live-verified): the first
 * of a pair is taken as a property's name and the token after it refused — a word, the semicolon or the end —
 * after a table, a view, a stage, a function, a policy and a kind no object is modelled of alike.
 */
public class DescribeWordPairsTest extends MeasuredCellsTestSupport {

    @Test
    public void theWordsAfterANameAreReadInPairs() {
        engine.execute("CREATE OR REPLACE TABLE dt1 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW dv1 AS SELECT 1 AS a");
        engine.execute("CREATE OR REPLACE SESSION POLICY sp_m");
        assertCells(new String[][] {
            {"DESCRIBE DATASET nosuch_ds x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 32 unexpected '<EOF>'."},
            {"DESCRIBE DATASET nosuch_ds x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 32 unexpected ';'."},
            {"DESCRIBE TABLE dt1 x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax error "
                + "line 1 at position 24 unexpected ';'."},
            {"DESCRIBE VIEW dv1 x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'. | syntax error "
                + "line 1 at position 23 unexpected ';'."},
            {"DESCRIBE TABLE dt1 x y;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'."},
            {"DESCRIBE TABLE dt1 x;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'."},
            {"DESCRIBE SESSION POLICY sp_m x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax error "
                + "line 1 at position 34 unexpected ';'."},
            {"DESCRIBE DATASET nosuch_ds x;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ';'."},
            {"DESCRIBE DATASET nosuch_ds x y;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'."},
            {"DESCRIBE MODEL nosuch_m x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 29 unexpected '<EOF>'."},
            {"DESCRIBE PACKAGES POLICY nosuch_p x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax error "
                + "line 1 at position 39 unexpected '<EOF>'."},
        });
    }

    @Test
    public void aSemicolonEndsThePairsAsTheEndDoes() {
        engine.execute("CREATE OR REPLACE TABLE dt1 (a INT)");
        engine.execute("CREATE OR REPLACE SESSION POLICY sp_d");
        assertCells(new String[][] {
            {"DESCRIBE DATASET nosuch_ds x y z w;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 33 unexpected 'w'."},
            {"DESCRIBE DATASET nosuch_ds x y z w",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 33 unexpected 'w'."},
            {"DESCRIBE TABLE dt1 x y z w;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax error "
                + "line 1 at position 25 unexpected 'w'."},
            {"DESCRIBE MODEL nosuch_m x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 29 unexpected ';'."},
            {"DESCRIBE SESSION POLICY sp_d x y z w",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax error "
                + "line 1 at position 35 unexpected 'w'."},
            {"DESC DATASET nosuch_ds x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax error "
                + "line 1 at position 28 unexpected ';'."},
            {"DESCRIBE DATASET test_db.test_schema.nosuch_ds x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax error "
                + "line 1 at position 52 unexpected '<EOF>'."},
            {"DESCRIBE DATASET nosuch_ds 'x' y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''x''."},
            {"DESCRIBE TABLE dt1 TYPE = COLUMNS x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax error "
                + "line 1 at position 39 unexpected '<EOF>'."},
            {"DESCRIBE TABLE dt1 x y z w v",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax error "
                + "line 1 at position 25 unexpected 'w'. | syntax error line 1 at position 28 unexpected '<EOF>'."},
            {"DESCRIBE TABLE dt1 x y z w v;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax error "
                + "line 1 at position 25 unexpected 'w'. | syntax error line 1 at position 28 unexpected ';'."},
            {"DESCRIBE VIEW nosuch_v x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax error "
                + "line 1 at position 28 unexpected ';'."},
            {"DESCRIBE STAGE nosuch_st x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'. | syntax error "
                + "line 1 at position 30 unexpected ';'."},
            {"DESCRIBE FUNCTION f2() x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax error "
                + "line 1 at position 28 unexpected ';'."},
            {"DESCRIBE TABLE dt1 x = y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'."},
            {"DESCRIBE AUTHENTICATION POLICY nosuch_ap x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'y'. | syntax error "
                + "line 1 at position 46 unexpected ';'."},
            {"DESCRIBE SEMANTIC VIEW nosuch_sv x y z;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax error "
                + "line 1 at position 38 unexpected ';'."},
        });
    }
}
