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
 * A NULL tested by IN, NOT IN, = ANY and <> ALL against an empty subquery is NULL while the tested column holds NULL in
 * every stored row - the plan folds the column as the NULL its statistics say it is - and FALSE or TRUE once a
 * row holds a value (all live-verified).
 */
public class MembershipOverAllNullColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnTheStatisticsKnowToBeNullFoldsAsNull() {
        engine.execute("CREATE OR REPLACE TABLE ntn (x INT, y INT)");
        engine.execute("INSERT INTO ntn VALUES (NULL, 1), (NULL, 2)");
        assertAnswer("null|null ;; null|null",
            "SELECT x IN (SELECT 1 WHERE FALSE) i, x NOT IN (SELECT 1 WHERE FALSE) ni FROM ntn ORDER BY y");
        assertAnswer("null|null ;; null|null",
            "SELECT x = ANY (SELECT 1 WHERE FALSE) a, x <> ALL (SELECT 1 WHERE FALSE) l FROM ntn ORDER BY y");
        assertAnswer("0", "SELECT COUNT(*) FROM ntn WHERE x NOT IN (SELECT 1 WHERE FALSE)");
        assertAnswer("0", "SELECT COUNT(*) FROM ntn WHERE x IN (SELECT 1 WHERE FALSE)");
        assertAnswer("1|null ;; 2|null", "SELECT y, x IN (SELECT 1 FROM ntn WHERE y > 100) FROM ntn ORDER BY y");
        assertAnswer("null", "SELECT NULL IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT NULL::INT NOT IN (SELECT 1 WHERE FALSE)");
        assertAnswer("1", "INSERT INTO ntn VALUES (7, 3)");
        assertAnswer("3", "SELECT COUNT(*) FROM ntn WHERE x NOT IN (SELECT 1 WHERE FALSE)");
        assertAnswer("1|false|true ;; 2|false|true ;; 3|false|true",
            "SELECT y, x IN (SELECT 1 WHERE FALSE) i, x NOT IN (SELECT 1 WHERE FALSE) ni FROM ntn ORDER BY y");
        assertAnswer("1|false|true ;; 2|false|true ;; 3|false|true",
            "SELECT y, x = ANY (SELECT 1 WHERE FALSE) a, x <> ALL (SELECT 1 WHERE FALSE) l FROM ntn ORDER BY y");
        assertAnswer("1|false ;; 2|false",
            "SELECT y, x IN (SELECT 1 WHERE FALSE) FROM (SELECT x, y FROM ntn WHERE x IS NULL) ORDER BY y");
        assertAnswer("1|false ;; 2|false ;; 3|false", "SELECT y, x IN (SELECT 1 WHERE 1 = 0) FROM ntn ORDER BY y");
    }
}
