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
 * A correlated scalar subquery whose select item reads the outer row through a derived table or a common table
 * expression of its own is typed from the item, as live types it (live-verified): (SELECT c + o.k FROM (SELECT 1 AS
 * c)) and its CTE form declare NUMBER(38,0), a decimal operand NUMBER(38,1), where they declared the text
 * placeholder VARCHAR(16777216); a FROM-less item, an aliased derived table and a text item keep their types.
 */
public class CorrelatedDerivedItemTypeTest extends MeasuredCellsTestSupport {

    /** Each query and its columns, and the values one of them answers. */
    @Test
    public void theItemIsTypedWithTheOuterNamesInScope() {
        engine.execute("CREATE OR REPLACE TABLE o (k INT)");
        engine.execute("INSERT INTO o VALUES (1), (2)");
        assertShape("K:NUMBER(38,0), X:NUMBER(38,0)",
            "SELECT k, (SELECT o.k + 1) AS x FROM o ORDER BY k");
        assertShape("K:NUMBER(38,0), X:NUMBER(38,0)",
            "SELECT k, (SELECT c + o.k FROM (SELECT 1 AS c)) AS x FROM o ORDER BY k");
        assertShape("K:NUMBER(38,0), X:NUMBER(38,0)",
            "SELECT k, (WITH d AS (SELECT 1 AS c) SELECT c + o.k FROM d) AS x FROM o ORDER BY k");
        assertShape("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                    + "position 11",
            "SELECT k, (SELECT MAX(v) + o.k FROM (SELECT 5 AS v)) AS x FROM o ORDER BY k");
        assertShape("K:NUMBER(38,0), X:VARCHAR(134217728)",
            "SELECT k, (SELECT c || o.k FROM (SELECT 'a' AS c)) AS x FROM o ORDER BY k");
        assertShape("K:NUMBER(38,0), X:NUMBER(38,1)",
            "SELECT k, (SELECT c * 1.5 + o.k FROM (SELECT 2 AS c)) AS x FROM o ORDER BY k");
        assertShape("K:NUMBER(38,0), X:NUMBER(38,0)",
            "SELECT k, (SELECT c FROM (SELECT o.k AS c)) AS x FROM o ORDER BY k");
        assertShape("K:NUMBER(38,0), X:NUMBER(38,0)",
            "SELECT k, (SELECT d.c + o.k FROM (SELECT 1 AS c) d) AS x FROM o ORDER BY k");
        assertCells(new String[][] {
            {"SELECT k, (SELECT c + o.k FROM (SELECT 1 AS c)) AS x FROM o ORDER BY k",
                "1|2 ;; 2|3"},
        });
    }
}
