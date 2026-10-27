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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A sort or grouping key written as a number under two signs with nothing between them is refused as an invalid
 * function named by the outer sign, before the statement's names are looked up, where a parenthesised inner sign is
 * an ordinary position (all live-verified).
 */
public class DoubledSignKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void twoSignsInARowAreAnInvalidFunction() {
        assertAnswer("Table TC733 successfully created.", "CREATE OR REPLACE TABLE tc733 (a INT, b VARCHAR)");
        assertAnswer("3", "INSERT INTO tc733 VALUES (1, 'z'), (2, 'y'), (3, 'x')");
        assertAnswer("REFUSED SQL compilation error: | invalid function '-'",
            "SELECT a FROM tc733 ORDER BY -+1, a DESC");
        assertAnswer("REFUSED SQL compilation error: | invalid function '+'",
            "SELECT a FROM tc733 ORDER BY ++1, a DESC");
        assertAnswer("REFUSED SQL compilation error: | invalid function '+'",
            "SELECT a FROM tc733 ORDER BY +-1, a DESC");
        assertAnswer("REFUSED SQL compilation error: | invalid function '-'",
            "SELECT a FROM tc733 ORDER BY - -1, a DESC");
        assertAnswer("1 ;; 2 ;; 3", "SELECT a FROM tc733 ORDER BY -(-1), a DESC");
        assertAnswer("REFUSED SQL compilation error: | invalid function '-'",
            "SELECT a FROM no_such_table ORDER BY - -1");
        assertAnswer("REFUSED SQL compilation error: | invalid function '-'",
            "SELECT COUNT(*) FROM no_such_table GROUP BY - -1");
    }
}
