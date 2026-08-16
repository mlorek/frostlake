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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A typeof over an aggregate the account folds away, beside an aggregate it answers from statistics,
 * answers one row per input row: the typeof's aggregate leaves the scan behind, and the statistic is a
 * constant over it. Each row carries the statistic over the whole (filtered) input, and no input at all
 * still answers one row. An aggregate the account really computes, a HAVING and a GROUP BY keep the
 * ordinary shape, and DISTINCT, ORDER BY and LIMIT act on the repeated rows. Every cell is live-verified,
 * over two distinct rows (a column holding one value is answered like a constant on the account).
 */
public class TypeofBesideStatisticsTest extends BaseDatabaseTest {

    private static final String SUM_TYPE = "NUMBER(32,10)[SB16]";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE aw (c20_10 NUMBER(20,10), k VARCHAR, d DATE)");
        engine.execute("INSERT INTO aw VALUES (1.5, 'a', '2024-01-01'), (3.5, 'b', '2024-01-02')");
    }

    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    line.append(", ");
                }
                line.append(row.getValue(c));
            }
            out.add(line.toString());
        }
        return out;
    }

    @Test
    public void aStatisticBesideAFoldedTypeofIsRepeatedPerInputRow() {
        assertEquals(List.of(SUM_TYPE + ", 2", SUM_TYPE + ", 2"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw"));
        assertEquals(List.of("NUMBER(34,12)[SB16], 2", "NUMBER(34,12)[SB16], 2"),
            rows("SELECT SYSTEM$TYPEOF(AVG(c20_10)), COUNT(*) FROM aw"));
        assertEquals(List.of("2, " + SUM_TYPE, "2, " + SUM_TYPE), rows("SELECT COUNT(*), SYSTEM$TYPEOF(SUM(c20_10)) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 2", SUM_TYPE + ", 2"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(c20_10) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 3.5000000000", SUM_TYPE + ", 3.5000000000"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), MAX(c20_10) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 2024-01-01", SUM_TYPE + ", 2024-01-01"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), MIN(d) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 3", SUM_TYPE + ", 3"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) + 1 FROM aw"));
    }

    @Test
    public void theStatisticIsOverTheFilteredInputAndNoInputIsOneRow() {
        assertEquals(List.of(SUM_TYPE + ", 2", SUM_TYPE + ", 2"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw WHERE c20_10 > 0"));
        assertEquals(List.of(SUM_TYPE + ", 1"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw WHERE c20_10 > 2"));
        assertEquals(List.of(SUM_TYPE + ", 0"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw WHERE c20_10 > 100"));
        assertEquals(List.of(SUM_TYPE + ", null"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), MAX(c20_10) FROM aw WHERE c20_10 > 100"));
    }

    @Test
    public void aComputedAggregateAHavingAndTheLaterClausesKeepTheirShape() {
        assertEquals(List.of(SUM_TYPE + ", b"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), MAX(k) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 2"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(DISTINCT k) FROM aw"));
        assertEquals(List.of("NUMBER(18,0)[SB1], 2"), rows("SELECT SYSTEM$TYPEOF(COUNT(*)), COUNT(*) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 2"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw HAVING COUNT(*) > 0"));
        assertEquals(List.of(SUM_TYPE + ", 2"), rows("SELECT DISTINCT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw"));
        assertEquals(List.of(SUM_TYPE + ", 2"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw LIMIT 1"));
        assertEquals(List.of(SUM_TYPE + ", 2", SUM_TYPE + ", 2"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw ORDER BY 2"));
    }
}
