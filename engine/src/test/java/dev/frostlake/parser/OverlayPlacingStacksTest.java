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
 * An OVERLAY written in the PLACING form under brackets of a select item names the PLACING, then what the
 * brackets around the call decide: a cast's the PLACING's value and the FROM's, a call's the FROM's value or a
 * bracket's ')', a plain parenthesis the value or the FROM's, a FOR tail its own value. In a block's statement the
 * block names more after those: its END, or in a RESULTSET's query the cast's AS and its ')' (all live-verified).
 */
public class OverlayPlacingStacksTest extends MeasuredCellsTestSupport {

    @Test
    public void bracketStacksAndTails() {
        engine.execute("CREATE OR REPLACE TABLE tb750 (b VARCHAR)");
        engine.execute("INSERT INTO tb750 VALUES ('abc')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 35 unexpected ''x''. | syntax error line 1 at position 44 "
                + "unexpected '2'.",
            "SELECT CAST(CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) AS INT) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 55 unexpected ')'.",
            "SELECT UPPER(UPPER(UPPER(OVERLAY(b PLACING 'x' FROM 2)))) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 36 unexpected '2'.",
            "SELECT ((OVERLAY(b PLACING 'x' FROM 2))) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 41 unexpected '2'.",
            "SELECT UPPER((OVERLAY(b PLACING 'x' FROM 2))) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 41 unexpected '2'.",
            "SELECT (UPPER(OVERLAY(b PLACING 'x' FROM 2))) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 31 unexpected ''x''. | syntax error line 1 at position 40 "
                + "unexpected '2'.",
            "SELECT (CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 40 unexpected '2'.",
            "SELECT CAST((OVERLAY(b PLACING 'x' FROM 2)) AS INT) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 53 unexpected ')'.",
            "SELECT UPPER(CAST(UPPER(OVERLAY(b PLACING 'x' FROM 2)) AS INT)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 34 unexpected ''x''. | syntax error line 1 at position 43 "
                + "unexpected '2'.",
            "SELECT TRY_CAST(OVERLAY(b PLACING 'x' FROM 2) AS VARCHAR) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 40 unexpected ''x''. | syntax error line 1 at position 49 "
                + "unexpected '2'.",
            "SELECT UPPER(TRY_CAST(OVERLAY(b PLACING 'x' FROM 2) AS VARCHAR)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 45 unexpected ''x''.",
            "SELECT CASE WHEN TRUE THEN OVERLAY(b PLACING 'x' FROM 2) END FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 40 unexpected '2'. | syntax error line 1 at position 46 unexpected "
                + "'1'.",
            "SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2 FOR 1)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 30 unexpected ''x''. | syntax error line 1 at position 39 "
                + "unexpected '2'. | syntax error line 1 at position 45 unexpected '1'.",
            "SELECT CAST(OVERLAY(b PLACING 'x' FROM 2 FOR 1) AS INT) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 47 unexpected '2'.",
            "SELECT UPPER(OVERLAY(b PLACING 'x' || 'y' FROM 2)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 47 unexpected '2'.",
            "SELECT UPPER(OVERLAY(b || 'c' PLACING 'x' FROM 2)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 40 unexpected '1'.",
            "SELECT UPPER(OVERLAY(b PLACING 'x' FROM 1 + 1)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 38 unexpected '2'.",
            "SELECT UPPER(OVERLAY(b PLACING x FROM 2)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 43 unexpected '2'.",
            "SELECT 1, UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 33 unexpected ''x''. | syntax error line 1 at position 42 "
                + "unexpected '2'.",
            "SELECT 1, CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'PLACING'.",
            "SELECT b FROM tb750 WHERE UPPER(OVERLAY(b PLACING 'x' FROM 2)) = 'a'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'PLACING'.",
            "SELECT b FROM tb750 WHERE CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 40 unexpected '2'.",
            "SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) AS u FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 30 unexpected ''x''. | syntax error line 1 at position 39 "
                + "unexpected '2'.",
            "SELECT CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) + 1 FROM tb750");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 46 unexpected '2'. | syntax error line 1 at position 62 unexpected "
                + "'END'.",
            "BEGIN SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb750; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 56 unexpected ''x''. | syntax error line 1 at position 65 "
                + "unexpected '2'. | syntax error line 1 at position 68 unexpected 'AS'. | syntax error line 1 at "
                + "position 74 unexpected ')'.",
            "BEGIN LET r RESULTSET := (SELECT CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) FROM tb750); RETURN TABLE(r); "
                + "END");
    }
}
