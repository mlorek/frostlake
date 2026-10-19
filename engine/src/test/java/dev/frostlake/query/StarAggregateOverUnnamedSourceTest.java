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
 * A star-argument aggregate over an unaliased VALUES list or derived table reads the source's columns as the same
 * call written out does, grouped or not: {@code HASH_AGG(*)} equals {@code HASH_AGG(column1, column2)}, and
 * {@code COUNT(* EXCLUDE …)}, {@code ARRAY_AGG(*)} and {@code OBJECT_AGG(*)} answer their values (live-verified).
 */
public class StarAggregateOverUnnamedSourceTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarAggregateReadsAnUnnamedSourcesColumns() {
        assertCells(new String[][] {
            {"SELECT HASH_AGG(*) = HASH_AGG(column1, column2) FROM VALUES (1, 'a'), (2, 'b')", "true"},
            {"SELECT HASH_AGG(*) = HASH_AGG(a, b) FROM (SELECT 1 a, 'a' b UNION ALL SELECT 2, 'b')", "true"},
            {"SELECT column2, HASH_AGG(*) = HASH_AGG(column1, column2) FROM VALUES (1, 'a'), (2, 'b') "
                + "GROUP BY column2 ORDER BY 1", "a|true ;; b|true"},
            {"SELECT COUNT(* EXCLUDE (column2)) FROM VALUES (1, 'a'), (NULL, 'b')", "1"},
            {"SELECT COUNT(* ILIKE 'COL%') FROM VALUES (1, 'a'), (NULL, 'b')", "1"},
            {"SELECT ARRAY_AGG(*) FROM VALUES (1)", "[1]"},
            {"SELECT ARRAY_AGG(*) FROM (SELECT 1 AS a)", "[1]"},
            {"SELECT OBJECT_AGG(*) FROM VALUES ('k', 1)", "{\"k\":1}"},
        });
    }
}
