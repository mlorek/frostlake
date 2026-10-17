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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SEARCH takes a star for its data beside named options, {@code SEARCH(*, 'a b', SEARCH_MODE => 'EXACT')}, as it takes
 * a column; and data holding no column a full-text search reads — a number, a BOOLEAN or a date, as a value or a
 * column — is refused as {@code Expected non-empty set of columns supporting full-text search.} at the call, while a
 * row with a text among its members, a VARIANT and a NULL are searched (live-verified).
 */
public class SearchStarOptionTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarTakesNamedOptionsAndNumbersAreRefused() {
        engine.execute("CREATE OR REPLACE TABLE ts (c VARCHAR)");
        engine.execute("INSERT INTO ts VALUES ('A B'), ('a b')");
        engine.execute("CREATE OR REPLACE TABLE tn (n INT, c VARCHAR)");
        engine.execute("INSERT INTO tn VALUES (12, 'a'), (3, 'x 12')");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM ts WHERE SEARCH(*, 'a b', SEARCH_MODE => 'EXACT')", "2"},
            {"SELECT COUNT(*) FROM ts WHERE SEARCH(*, 'a b', SEARCH_MODE => 'OR')", "2"},
            {"SELECT COUNT(*) FROM ts WHERE SEARCH(ts.*, 'a b', SEARCH_MODE => 'EXACT')", "2"},
            {"SELECT COUNT(*) FROM ts WHERE SEARCH(*, 'a b', ANALYZER => 'NO_OP_ANALYZER')", "1"},
            {"SELECT SEARCH(12, '12', SEARCH_MODE => 'EXACT')",
                "REFUSED SQL compilation error: error line 1 at position 7: Expected non-empty set of columns "
                + "supporting full-text search."},
            {"SELECT SEARCH(12, '12')",
                "REFUSED SQL compilation error: error line 1 at position 7: Expected non-empty set of columns "
                + "supporting full-text search."},
            {"SELECT SEARCH(1.5, '1.5')",
                "REFUSED SQL compilation error: error line 1 at position 7: Expected non-empty set of columns "
                + "supporting full-text search."},
            {"SELECT SEARCH(TRUE, 'true')",
                "REFUSED SQL compilation error: error line 1 at position 7: Expected non-empty set of columns "
                + "supporting full-text search."},
            {"SELECT SEARCH('2024-01-01'::DATE, '2024')",
                "REFUSED SQL compilation error: error line 1 at position 7: Expected non-empty set of columns "
                + "supporting full-text search."},
            {"SELECT SEARCH(PARSE_JSON('{\"a\":\"b\"}'), 'b')", "true"},
            {"SELECT SEARCH(n, '12') FROM tn ORDER BY n",
                "REFUSED SQL compilation error: error line 1 at position 7: Expected non-empty set of columns "
                + "supporting full-text search."},
            {"SELECT SEARCH((n, c), '12') FROM tn ORDER BY n", "true ;; false"},
            {"SELECT n, SEARCH(*, '12') FROM tn ORDER BY n", "3|true ;; 12|false"},
            {"SELECT SEARCH(NULL, 'x')", "null"},
            {"SELECT SEARCH(12::VARCHAR, '12')", "true"},
        });
    }
}
