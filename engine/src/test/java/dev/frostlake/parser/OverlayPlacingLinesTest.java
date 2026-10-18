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
 * An OVERLAY written in the ANSI PLACING form is refused at the PLACING (live-verified): as a select item's own
 * value the PLACING is named twice, in a WHERE once, and under brackets the lines after it are what the brackets
 * decide — a cast's names the PLACING's value and the FROM's, a call's the FROM's value, two calls the inner
 * call's ')', a call under a cast the cast's ')', and plain parentheses the PLACING's value.
 */
public class OverlayPlacingLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void thePlacingFormIsRefusedByTheBracketsAroundIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertCells(new String[][] {
            {"SELECT UPPER(CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT)) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 36 unexpected ''x''. | syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT OVERLAY(b PLACING 'x' FROM 2) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT OVERLAY(b PLACING 'x' FROM 2 FOR 1) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 30 unexpected ''x''. | syntax error line 1 at position 39 unexpected '2'."},
            {"SELECT OVERLAY(b PLACING 'x') FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 40 unexpected '2'."},
            {"SELECT OVERLAY(b PLACING 'x' FROM 2), 1 FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT 1 FROM t WHERE OVERLAY(b PLACING 'x' FROM 2) = 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'PLACING'."},
            {"SELECT OVERLAY(b PLACING 'x' FROM a) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT OVERLAY(b PLACING UPPER('x') FROM 2) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT UPPER(UPPER(OVERLAY(b PLACING 'x' FROM 2))) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 48 unexpected ')'."},
            {"SELECT CAST(UPPER(OVERLAY(b PLACING 'x' FROM 2)) AS INT) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 55 unexpected ')'."},
            {"SELECT OVERLAY(b PLACING 'x' FROM 2 FOR 1 + 1) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 17 unexpected 'PLACING'."},
            {"SELECT (OVERLAY(b PLACING 'x' FROM 2)) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'PLACING'. | syntax "
                + "error line 1 at position 26 unexpected ''x''."},
        });
    }
}
