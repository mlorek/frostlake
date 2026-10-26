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
 * A CREATE TABLE column's value DEFAULT reads only the columns defined before it: a later or unknown name, and a
 * qualified one, is an invalid identifier where it stands, and the column's own name is refused at the column; an
 * added column's DEFAULT reading the table's columns is no default it takes, its columns named by the table, and
 * one reading a name the table lacks is an invalid identifier (all live-verified).
 */
public class ColumnDefaultReadsTest extends MeasuredCellsTestSupport {

    @Test
    public void defaultReads() {
        engine.execute("CREATE OR REPLACE TABLE de685f (c2 INT, s VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685a (c INT DEFAULT c2 + 1, c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685b (c VARCHAR DEFAULT UPPER(c2), c2 VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685c (c VARCHAR DEFAULT c2 || 'x', c2 VARCHAR)");
        assertAnswer("Table DE685D successfully created.",
            "CREATE OR REPLACE TABLE de685d (c2 INT, c INT DEFAULT c2 + 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 |  Column 'C' cannot reference "
                + "itself in its default value expression.",
            "CREATE OR REPLACE TABLE de685e (c INT DEFAULT c + 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE TABLE de685g (c INT DEFAULT nosuch + 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685h (c INT DEFAULT ABS(c2), c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685i (c INT DEFAULT c2, c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 54 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685j (c BOOLEAN DEFAULT NOT c2, c2 BOOLEAN)");
        assertAnswer("Table DE685K successfully created.", "CREATE OR REPLACE TABLE de685k (c INT DEFAULT 1 + 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 47 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685l (c INT DEFAULT (c2) + 1, c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685m (c INT DEFAULT 1 + c2 * 2, c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685n (c INT DEFAULT COALESCE(c2, 0), c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de685o (c INT DEFAULT CASE WHEN c2 > 0 THEN 1 ELSE 0 END, c2 INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'DE685P.C2'",
            "CREATE OR REPLACE TABLE de685p (c INT DEFAULT de685p.c2 + 1, c2 INT)");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [DE685F.C2 + 1]",
            "ALTER TABLE de685f ADD COLUMN c INT DEFAULT c2 + 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [UPPER(DE685F.S)]",
            "ALTER TABLE de685f ADD COLUMN d VARCHAR DEFAULT UPPER(s)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'NOSUCH'",
            "ALTER TABLE de685f ADD COLUMN e INT DEFAULT nosuch + 1");
    }
}
