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
 * A NULL read from a row is FALSE IN, and TRUE NOT IN, an empty subquery (live-verified), whatever the column's
 * other rows hold: a column holding only NULLs answers as one that also holds a number.
 */
public class NullProbeEmptySubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullRowProbeOfAnEmptySet() {
        engine.execute("CREATE OR REPLACE TABLE tn (x INT, y INT)");
        engine.execute("INSERT INTO tn VALUES (NULL, 1), (NULL, 2)");
        assertCells(new String[][] {
            {"SELECT x, x IN (SELECT 1 WHERE FALSE), x NOT IN (SELECT 1 WHERE FALSE) FROM tn ORDER BY y",
                "null|null|null ;; null|null|null"},
            {"SELECT COUNT(*) FROM tn WHERE x NOT IN (SELECT 1 WHERE FALSE)", "0"},
            {"SELECT y FROM tn WHERE NOT (x IN (SELECT y FROM tn WHERE y > 5)) ORDER BY 1", ""},
            {"INSERT INTO tn VALUES (7, 3)", "1"},
            {"SELECT COUNT(*) FROM tn WHERE x NOT IN (SELECT 1 WHERE FALSE)", "3"},
        });
    }
}
