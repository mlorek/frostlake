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
 * A wrap into a VARIANT of a number or boolean literal a derived relation projects folds as the literal's own wrap
 * does, so it converts to a 1-character text column unchecked (live-verified): through a select over a table, a CTE, a
 * view, a CTE or a view over a table, a derived relation over another and one with a WHERE or a LIMIT alike. A literal
 * under a cast that adds decimals, and a set operation's column, are held to the column's length.
 */
public class VariantWrapThroughRelationsTest extends MeasuredCellsTestSupport {

    @Test
    public void aProjectedLiteralFoldsThroughEveryRelation() {
        engine.execute("CREATE OR REPLACE TABLE t924 (a INT)");
        engine.execute("INSERT INTO t924 VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW v924 AS SELECT 12 z");
        engine.execute("CREATE OR REPLACE VIEW w924 AS SELECT 12 z FROM t924");
        assertCells(new String[][] {
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT 12 z FROM t924)) ORDER BY 1",
                "12 ;; x"},
            {"WITH c AS (SELECT 12 z) SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM c) ORDER BY 1",
                "12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM v924) ORDER BY 1", "12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT z FROM (SELECT 12 z))) "
                + "ORDER BY 1",
                "12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT TRUE z)) ORDER BY 1",
                "true ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT 1.50::NUMBER(5,2) z)) ORDER BY 1",
                "REFUSED String '1.5' is too long and would be truncated"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM w924) ORDER BY 1", "12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(TRUE)) ORDER BY 1", "true ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(1.50::NUMBER(5,2))) ORDER BY 1",
                "REFUSED String '1.5' is too long and would be truncated"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT 12 z) WHERE z > 0) ORDER BY 1",
                "12 ;; x"},
            {"WITH c AS (SELECT 12 z FROM t924) SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM "
                + "c) ORDER BY 1",
                "12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT 12 z)) ORDER BY 1", "12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT 12 z UNION ALL SELECT "
                + "13)) ORDER BY 1",
                "REFUSED String '12' is too long and would be truncated"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT -12 z)) ORDER BY 1", "-12 ;; x"},
            {"SELECT v FROM (SELECT 'x' v UNION ALL SELECT TO_VARIANT(z) FROM (SELECT 12 z LIMIT 1)) ORDER BY 1",
                "12 ;; x"},
        });
    }
}
