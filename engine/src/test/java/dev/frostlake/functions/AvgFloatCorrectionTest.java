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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AVG over FLOATs is its SUM over the count, the sum read as SUM reads it in the same shape: corrected
 * for an aggregate (DISTINCT included), a whole-partition window and a cumulative RANGE frame's peer
 * groups, running for a running frame. Over 0.3 then 0.7 squared the corrected average is
 * 0.28999999999999992450 and the running one 0.28999999999999998002. Every digit is live-verified.
 */
public class AvgFloatCorrectionTest extends BaseDatabaseTest {

    private static final String PAIR = "(SELECT 1 AS i, 1 AS k, 0.3::FLOAT AS x UNION ALL SELECT 2, 1, 0.7::FLOAT)";
    private static final String CORRECTED = "0.28999999999999992450";

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String window(final String over) {
        return rows("SELECT i, TO_VARCHAR((AVG(x * x) OVER (" + over + "))::NUMBER(38,20)) FROM " + PAIR
            + " ORDER BY i");
    }

    @Test
    public void theAggregateAveragesTheCorrectedSum() {
        assertEquals(CORRECTED + ", 0.50000000000000000000",
            rows("SELECT TO_VARCHAR(AVG(x * x)::NUMBER(38,20)), TO_VARCHAR(AVG(x)::NUMBER(38,20)) FROM " + PAIR));
        assertEquals("1, " + CORRECTED + " | 2, 0.01000000000000000194",
            rows("SELECT k, TO_VARCHAR(AVG(x * x)::NUMBER(38,20)) FROM (SELECT 1 AS k, 0.3::FLOAT AS x "
                + "UNION ALL SELECT 2, 0.1::FLOAT UNION ALL SELECT 1, 0.7::FLOAT) GROUP BY k ORDER BY k"));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(AVG(DISTINCT x * x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.3::FLOAT AS x UNION ALL SELECT 0.7::FLOAT UNION ALL SELECT 0.3::FLOAT)"));
    }

    @Test
    public void tenthsAreUnchanged() {
        assertEquals("0.10000000000000001943", rows("SELECT TO_VARCHAR(AVG(x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.1::FLOAT AS x FROM TABLE(GENERATOR(ROWCOUNT => 3)))"));
        assertEquals("0.10000000000000000555", rows("SELECT TO_VARCHAR(AVG(x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.1::FLOAT AS x FROM TABLE(GENERATOR(ROWCOUNT => 7)))"));
    }

    @Test
    public void aWindowReadsItsSumByItsShape() {
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED, window(""));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED, window("PARTITION BY k"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED,
            window("ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED, window("ORDER BY k"));
        assertEquals("1, 0.08999999999999999667 | 2, 0.28999999999999998002", window("ORDER BY i"));
    }
}
