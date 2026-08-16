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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class TemporalStaticTypeTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
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

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aTemporalCallCarriesLivesStaticType() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P477_DB");
            engine.execute("CREATE OR REPLACE TABLE k1 AS SELECT GETDATE() AS c");
            assertEquals("C, TIMESTAMP_LTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k1"));
            engine.execute("CREATE OR REPLACE TABLE k2 AS SELECT SYSTIMESTAMP() AS c");
            assertEquals("C, TIMESTAMP_LTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k2"));
            engine.execute("CREATE OR REPLACE TABLE k3 AS SELECT CONVERT_TIMEZONE('UTC', '2024-01-01'::TIMESTAMP_NTZ(3)) AS c");
            assertEquals("C, TIMESTAMP_TZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k3"));
            engine.execute("CREATE OR REPLACE TABLE k4 AS SELECT DATE_TRUNC('day', '2024-01-01'::TIMESTAMP_NTZ(3)) AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k4"));
            engine.execute("CREATE OR REPLACE TABLE k5 AS SELECT '2024-01-01'::TIMESTAMP_NTZ(3) + INTERVAL '1 day' AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k5"));
            engine.execute("CREATE OR REPLACE TABLE k6 AS SELECT DATE_TRUNC('day', '2024-01-01 00:00:00 +02:00'::TIMESTAMP_TZ(3)) AS c");
            assertEquals("C, TIMESTAMP_TZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k6"));
            engine.execute("CREATE OR REPLACE TABLE k7 AS SELECT DATE_TRUNC('day', '2024-01-01'::TIMESTAMP_LTZ(3)) AS c");
            assertEquals("C, TIMESTAMP_LTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k7"));
            engine.execute("CREATE OR REPLACE TABLE k8 AS SELECT DATE_TRUNC('hour', '12:34:56'::TIME(3)) AS c");
            assertEquals("C, TIME(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k8"));
            engine.execute("CREATE OR REPLACE TABLE k10 AS SELECT SYSDATE() AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k10"));
            engine.execute("CREATE OR REPLACE TABLE k11 AS SELECT CURRENT_TIMESTAMP AS c");
            assertEquals("C, TIMESTAMP_LTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k11"));
            engine.execute("CREATE OR REPLACE TABLE k12 AS SELECT CONVERT_TIMEZONE('UTC','America/New_York','2024-01-01'::TIMESTAMP_NTZ(3)) AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k12"));
            engine.execute("CREATE OR REPLACE TABLE k14 AS SELECT DATE_TRUNC('day', '2024-01-01'::DATE) AS c");
            assertEquals("C, DATE, COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE k14"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P477_DB");
        }
    }

    @Test
    public void aFamilyKeepingCallAnswersAtNineDigits() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P477B_DB");
            engine.execute("CREATE OR REPLACE TABLE m1 AS SELECT DATEADD('day', 1, '2024-01-01'::TIMESTAMP_NTZ(3)) AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m1"));
            engine.execute("CREATE OR REPLACE TABLE m2 AS SELECT ADD_MONTHS('2024-01-01'::TIMESTAMP_NTZ(3), 1) AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m2"));
            engine.execute("CREATE OR REPLACE TABLE m3 AS SELECT TRUNC('2024-01-01'::TIMESTAMP_NTZ(3), 'month') AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m3"));
            engine.execute("CREATE OR REPLACE TABLE m4 AS SELECT LAST_DAY('2024-01-01'::TIMESTAMP_NTZ(3)) AS c");
            assertEquals("C, DATE, COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m4"));
            engine.execute("CREATE OR REPLACE TABLE m5 AS SELECT TIMEADD('hour', 1, '12:34:56'::TIME(3)) AS c");
            assertEquals("C, TIME(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m5"));
            engine.execute("CREATE OR REPLACE TABLE m6 AS SELECT DATE_TRUNC('day', '2024-01-01'::TIMESTAMP_NTZ(0)) AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m6"));
            engine.execute("CREATE OR REPLACE TABLE m7 AS SELECT DATEADD('day', 1, '2024-01-01'::TIMESTAMP_TZ(3)) AS c");
            assertEquals("C, TIMESTAMP_TZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m7"));
            engine.execute("CREATE OR REPLACE TABLE m8 AS SELECT '2024-01-01'::TIMESTAMP_NTZ(3) - INTERVAL '1 day' AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m8"));
            engine.execute("CREATE OR REPLACE TABLE m9 AS SELECT DATEADD('hour', 1, '2024-01-01'::DATE) AS c");
            assertEquals("C, TIMESTAMP_NTZ(9), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m9"));
            engine.execute("CREATE OR REPLACE TABLE m10 AS SELECT '2024-01-01'::DATE + INTERVAL '1 day' AS c");
            assertEquals("C, DATE, COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE m10"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P477B_DB");
        }
    }
}
