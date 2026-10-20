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
 * TIME_SLICE's slice length is a whole number of one at least (live-verified): a text, a FLOAT, a VARIANT and a
 * fixed-point number with a scale are refused by their type as the statement compiles, and a length written out that
 * is not positive by its value, read as a 32-bit integer and spelled with thousands separators, over no rows as well;
 * a length a row gives is refused as the row is read, "Invalid parameter value: 0. Reason: non-positive slice length".
 */
public class TimeSliceLengthTest extends MeasuredCellsTestSupport {

    /** Each slice and its answer. */
    @Test
    public void aLengthIsJudgedByItsTypeAndValue() {
        engine.execute("CREATE OR REPLACE TABLE dn_t (k INT, f FLOAT, n NUMBER(10,2), d DATE)");
        engine.execute("INSERT INTO dn_t SELECT 1, 'NaN'::FLOAT, 0, '2024-01-01'::DATE UNION ALL SELECT 2, "
            + "'inf'::FLOAT, -2, '2024-01-03'::DATE");
        assertCells(new String[][] {
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 'NaN'::FLOAT, 'DAY')",
                "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 0, 'DAY')",
                "REFUSED SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, -2, 'DAY')",
                "REFUSED SQL compilation error: | invalid value [-2] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 0.5, 'DAY')",
                "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-03'::DATE, 1.5, 'DAY')",
                "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-03'::DATE, 2.5::FLOAT, 'DAY')",
                "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-03'::DATE, '3', 'DAY')",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-03'::DATE, 2, 'DAY'), TIME_SLICE('2024-01-03'::DATE, NULL, 'DAY')",
                "2024-01-02|null"},
            {"SELECT k, TIME_SLICE(d, n, 'DAY') FROM dn_t ORDER BY k",
                "REFUSED SQL compilation error: | invalid type [NUMBER(10,2)] for parameter 'slice-length'"},
            {"SELECT k, TIME_SLICE(d, n, 'DAY') FROM dn_t WHERE k = 1",
                "REFUSED SQL compilation error: | invalid type [NUMBER(10,2)] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE(d, n, 'DAY') FROM dn_t WHERE 1 = 0",
                "REFUSED SQL compilation error: | invalid type [NUMBER(10,2)] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, 0, 'HOUR')",
                "REFUSED SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, 'inf'::FLOAT, 'HOUR')",
                "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'slice-length'"},
        });
    }

    /** Each slice and its answer. */
    @Test
    public void aWrittenLengthIsJudgedAsTheStatementCompiles() {
        engine.execute("CREATE OR REPLACE TABLE dq_t (k INT, d DATE)");
        engine.execute("INSERT INTO dq_t SELECT 2, '2024-01-03'::DATE");
        assertCells(new String[][] {
            {"SELECT TIME_SLICE(d, k, 'DAY') FROM dq_t",
                "2024-01-02"},
            {"SELECT TIME_SLICE(d, k + 1, 'DAY') FROM dq_t",
                "2024-01-03"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 2::NUMBER(10,0), 'DAY')",
                "2023-12-31"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 2::FLOAT, 'DAY')",
                "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 2.0, 'DAY')",
                "2023-12-31"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 1 + 1, 'DAY')",
                "2023-12-31"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, -0, 'DAY')",
                "REFUSED SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 3000000000, 'DAY')",
                "REFUSED SQL compilation error: | invalid value [-1,294,967,296] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, 0, 'DAY') FROM dq_t WHERE 1 = 0",
                "REFUSED SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, TO_VARIANT(2), 'DAY')",
                "REFUSED SQL compilation error: | invalid type [VARIANT] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, 0, 'HOUR', 'END')",
                "REFUSED SQL compilation error: | invalid value [0] for parameter 'slice-length'"},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, -1, 'MONTH')",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'slice-length'"},
        });
    }

    /** Each slice and its answer. */
    @Test
    public void aRowsLengthIsJudgedAsTheRowIsRead() {
        engine.execute("CREATE OR REPLACE TABLE dl_t (k INT, f FLOAT, fn FLOAT NOT NULL, z INT, d DATE)");
        engine.execute("INSERT INTO dl_t SELECT 1, 'NaN'::FLOAT, 3e9::FLOAT, 0, '2024-01-03'::DATE UNION ALL SELECT "
            + "2, 1.5, 1.5, -2, '2024-01-03'::DATE");
        assertCells(new String[][] {
            {"SELECT k, TIME_SLICE(d, z, 'DAY') FROM dl_t ORDER BY k",
                "REFUSED Invalid parameter value: 0. Reason: non-positive slice length"},
            {"SELECT k, TIME_SLICE(d, z, 'DAY') FROM dl_t WHERE k = 2",
                "REFUSED Invalid parameter value: -2. Reason: non-positive slice length"},
            {"SELECT k, TIME_SLICE(d, k, 'DAY') FROM dl_t ORDER BY k",
                "1|2024-01-03 ;; 2|2024-01-02"},
            {"SELECT TIME_SLICE(d, z, 'DAY') FROM dl_t WHERE 1 = 0",
                ""},
            {"SELECT TIME_SLICE('2024-01-01'::DATE, NULL::INT, 'DAY'), TIME_SLICE('2024-01-01'::DATE, NULL::FLOAT, "
                + "'DAY')",
                "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'slice-length'"},
        });
    }
}
