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
 * A stored array's element converted to a DATE reads the stored value wherever it stands outside the grouping keys a
 * grouped item holds (live-verified): under HAVING, CASE, IN, BETWEEN, IS NULL, NVL and IFNULL the conversion of the
 * element 1 is refused, a QUALIFY with no window function is refused for that, and a searched CASE whose first
 * condition is TRUE hands its column on as an IFF with a constant condition does, so its element converts. A grouped
 * subquery reading the element of the outer row inside its aggregate is a subquery live cannot evaluate.
 */
public class StoredElementConversionPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void conversionsOfAStoredElementInMorePlaces() {
        engine.execute("CREATE OR REPLACE TABLE va916 (i INT, a ARRAY)");
        engine.execute("INSERT INTO va916 SELECT 0, ARRAY_CONSTRUCT(1)");
        assertCells(new String[][] {
            {"SELECT i FROM va916 GROUP BY i, a HAVING TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') = '19700102'",
                "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT i FROM va916 QUALIFY TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') = '19700102'",
                "REFUSED SQL compilation error: error line 1 at position 20 | found QUALIFY clause but no window "
                + "function."},
            {"SELECT CASE WHEN TRUE THEN TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') END FROM va916 GROUP BY a",
                "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT a[0]::DATE IN ('1970-01-02'::DATE) FROM va916 GROUP BY a",
                "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT a[0]::DATE BETWEEN '1970-01-01'::DATE AND '1970-01-03'::DATE FROM va916 GROUP BY a",
                "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT a[0]::DATE IS NULL FROM va916 GROUP BY a", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT TO_VARCHAR(NVL(a, NULL)[0]::DATE, 'YYYYMMDD') FROM va916",
                "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT TO_VARCHAR(IFNULL(a, NULL)[0]::DATE, 'YYYYMMDD') FROM va916",
                "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT TO_VARCHAR((CASE WHEN TRUE THEN a END)[0]::DATE, 'YYYYMMDD') FROM va916", "19700102"},
            {"SELECT (SELECT MAX(TO_VARCHAR(va916.a[0]::DATE, 'YYYYMMDD')) FROM (SELECT 1 AS k) GROUP BY k) FROM va916",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
        });
    }
}
