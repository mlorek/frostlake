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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A window ordered by a view column whose value faults reads that key — and raises — over one row as over many: LAG,
 * LEAD, NTILE, NTH_VALUE and one-row partitions included. Only a FIRST_VALUE or a LAST_VALUE over a whole input of
 * one row, framed from UNBOUNDED PRECEDING, leaves the key unread and answers.
 */
public class WindowFaultingOrderKeyTest extends BaseDatabaseTest {

    private static final String FAULT = "String 'abcdefgh' is too long and would be truncated";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nc (s VARCHAR, n INT)");
        engine.execute("INSERT INTO nc VALUES ('abcdefgh', 1)");
        engine.execute("CREATE VIEW nc_v AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc");
        engine.execute("CREATE TABLE nc2 (s VARCHAR, n INT)");
        engine.execute("INSERT INTO nc2 VALUES ('abcdefgh', 1), ('xyz', 2)");
        engine.execute("CREATE VIEW nc2_v AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc2");
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() > 0 ? ";" : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void theKeyIsReadOverOneRow() {
        for (final String window : new String[] {"LAG(n) OVER (ORDER BY c)", "LEAD(n) OVER (ORDER BY c)",
            "NTILE(2) OVER (ORDER BY c)", "NTH_VALUE(n, 1) OVER (ORDER BY c)",
            "FIRST_VALUE(n) OVER (ORDER BY c ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)",
            "NTH_VALUE(n, 1) OVER (ORDER BY c ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)"}) {
            assertEquals(FAULT, refusal("SELECT n, " + window + " FROM nc_v"), window);
        }
    }

    @Test
    public void aOneRowPartitionReadsItsKeyToo() {
        assertEquals(FAULT, refusal("SELECT n, FIRST_VALUE(n) OVER (PARTITION BY n ORDER BY c) FROM nc2_v ORDER BY 1"));
        assertEquals(FAULT, refusal("SELECT n, LAG(n) OVER (PARTITION BY n ORDER BY c) FROM nc2_v ORDER BY 1"));
        assertEquals(FAULT, refusal("SELECT n, FIRST_VALUE(n) OVER (ORDER BY c ROWS BETWEEN UNBOUNDED PRECEDING AND "
            + "CURRENT ROW) FROM nc2_v ORDER BY 1"));
    }

    @Test
    public void anUnboundedFirstOrLastValueOverOneRowLeavesItUnread() {
        assertEquals("1|1", rows("SELECT n, FIRST_VALUE(n) OVER (ORDER BY c) FROM nc_v"));
        assertEquals("1|1", rows("SELECT n, LAST_VALUE(n) OVER (ORDER BY c) FROM nc_v"));
        assertEquals("1|1", rows("SELECT n, FIRST_VALUE(n) OVER (ORDER BY c ROWS BETWEEN UNBOUNDED PRECEDING AND "
            + "CURRENT ROW) FROM nc_v"));
        assertEquals("1|1", rows("SELECT n, LAST_VALUE(n) OVER (ORDER BY c ROWS BETWEEN UNBOUNDED PRECEDING AND "
            + "UNBOUNDED FOLLOWING) FROM nc_v"));
        assertEquals("1|1", rows("SELECT n, FIRST_VALUE(n) OVER (ORDER BY c RANGE BETWEEN UNBOUNDED PRECEDING AND "
            + "CURRENT ROW) FROM nc_v"));
    }
}
