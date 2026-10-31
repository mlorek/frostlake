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
 * Creating a view, a SQL function or a SQL table function over a sequence's NEXTVAL draws no value, nor does a
 * query that reads no row of its FROM through such a view, a CTAS over one included; the first draw after them
 * is the sequence's first. A view's column that is the bare NEXTVAL is described NOT NULL, NEXTVAL + 0 nullable
 * (all live-verified, a fresh sequence per question).
 */
public class NextvalShapeReadTest extends MeasuredCellsTestSupport {

    @Test
    public void nothingDrawsBeforeARow() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Sequence SQ1 successfully created.", "CREATE OR REPLACE SEQUENCE sq1");
        assertAnswer("View SV1 successfully created.", "CREATE OR REPLACE VIEW sv1 AS SELECT sq1.NEXTVAL AS n");
        assertAnswer("1", "SELECT sq1.NEXTVAL");
        assertAnswer("N|NUMBER(19,0)|COLUMN|N|null|N|N|null|null|null|null|null|null", "DESCRIBE VIEW sv1");
        assertAnswer("Sequence SQ2 successfully created.", "CREATE OR REPLACE SEQUENCE sq2");
        assertAnswer("Function SF2 successfully created.",
            "CREATE OR REPLACE FUNCTION sf2() RETURNS INT AS 'sq2.NEXTVAL'");
        assertAnswer("1", "SELECT sq2.NEXTVAL");
        assertAnswer("Sequence SQ3 successfully created.", "CREATE OR REPLACE SEQUENCE sq3");
        assertAnswer("View SV3 successfully created.", "CREATE OR REPLACE VIEW sv3 AS SELECT sq3.NEXTVAL AS n");
        assertAnswer("", "SELECT * FROM sv3 WHERE 1 = 0");
        assertAnswer("1", "SELECT sq3.NEXTVAL");
        assertAnswer("Sequence SQ4 successfully created.", "CREATE OR REPLACE SEQUENCE sq4");
        assertAnswer("View SV4 successfully created.", "CREATE OR REPLACE VIEW sv4 AS SELECT sq4.NEXTVAL AS n");
        assertAnswer("Table ST4 successfully created.", "CREATE OR REPLACE TABLE st4 AS SELECT * FROM sv4 WHERE 1 = 0");
        assertAnswer("1", "SELECT sq4.NEXTVAL");
        assertAnswer("N|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE TABLE st4");
        assertAnswer("Sequence SQ5 successfully created.", "CREATE OR REPLACE SEQUENCE sq5");
        assertAnswer("View SV5 successfully created.",
            "CREATE OR REPLACE VIEW sv5 AS SELECT sq5.NEXTVAL AS n FROM (SELECT 1 AS x)");
        assertAnswer("1", "SELECT sq5.NEXTVAL");
        assertAnswer("N|NUMBER(19,0)|COLUMN|N|null|N|N|null|null|null|null|null|null", "DESCRIBE VIEW sv5");
        assertAnswer("Sequence SQ6 successfully created.", "CREATE OR REPLACE SEQUENCE sq6");
        assertAnswer("Function SF6 successfully created.",
            "CREATE OR REPLACE FUNCTION sf6() RETURNS TABLE (n INT) AS 'SELECT sq6.NEXTVAL'");
        assertAnswer("1", "SELECT sq6.NEXTVAL");
        assertAnswer("Sequence SQ7 successfully created.", "CREATE OR REPLACE SEQUENCE sq7");
        assertAnswer("View SV7 successfully created.", "CREATE OR REPLACE VIEW sv7 AS SELECT sq7.NEXTVAL + 0 AS n");
        assertAnswer("1", "SELECT sq7.NEXTVAL");
        assertAnswer("N|NUMBER(20,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE VIEW sv7");
        assertAnswer("Sequence SQ8 successfully created.", "CREATE OR REPLACE SEQUENCE sq8");
        assertAnswer("View SV8 successfully created.",
            "CREATE OR REPLACE VIEW sv8 AS SELECT sq8.NEXTVAL AS n, 1 AS one");
        assertAnswer("1", "SELECT one FROM sv8");
        // ORDER hands the numbers out one after the other, where the default may jump from one batch to the next.
        assertAnswer("Sequence SQ9 successfully created.", "CREATE OR REPLACE SEQUENCE sq9 ORDER");
        assertAnswer("View SV9 successfully created.", "CREATE OR REPLACE VIEW sv9 AS SELECT sq9.NEXTVAL AS n");
        assertAnswer("1", "SELECT n FROM sv9");
        assertAnswer("2", "SELECT n FROM sv9");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
