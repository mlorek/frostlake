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
 * The DATE conversions of a digit string read it as seconds from the epoch, as a constant and as the text of a
 * stored VARIANT, ARRAY or OBJECT element alike (live-verified): {@code TRY_CAST(a[0]::VARCHAR AS DATE)} over an
 * element 1 is 1970-01-01 in a TRY_CAST, a cast, TO_DATE and TRY_TO_DATE, and {@code '20200115'::DATE} is
 * 1970-08-22; a TRY_CAST of a VARIANT itself to a DATE is refused for its argument types.
 */
public class DigitTextDateConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void aDigitStringIsReadAsSecondsFromTheEpoch() {
        engine.execute("CREATE OR REPLACE TABLE va904 (a VARIANT, s VARCHAR)");
        engine.execute("INSERT INTO va904 SELECT PARSE_JSON('[1, \"1\", 20200115, \"20200115\", \"2020-01-15\"]'), "
            + "'1'");
        assertCells(new String[][] {
            {"SELECT TRY_CAST('1' AS DATE)", "1970-01-01"},
            {"SELECT '1'::DATE", "1970-01-01"},
            {"SELECT TRY_CAST('20200115' AS DATE)", "1970-08-22"},
            {"SELECT '20200115'::DATE", "1970-08-22"},
            {"SELECT TO_DATE('1')", "1970-01-01"},
            {"SELECT TRY_TO_DATE('1')", "1970-01-01"},
            {"SELECT TRY_CAST(PARSE_JSON('1')::VARCHAR AS DATE)", "1970-01-01"},
            {"SELECT TRY_CAST(TO_VARCHAR(1) AS DATE)", "1970-01-01"},
            {"SELECT TRY_CAST(a[0]::VARCHAR AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(a[1]::VARCHAR AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(a[2]::VARCHAR AS DATE) FROM va904", "1970-08-22"},
            {"SELECT TRY_CAST(a[3]::VARCHAR AS DATE) FROM va904", "1970-08-22"},
            {"SELECT TRY_CAST(a[4]::VARCHAR AS DATE) FROM va904", "2020-01-15"},
            {"SELECT a[0]::VARCHAR::DATE FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(a[0]::VARCHAR AS TIME) FROM va904", "00:00:01"},
            {"SELECT TRY_CAST(s AS DATE) FROM va904", "1970-01-01"},
            {"SELECT s::DATE FROM va904", "1970-01-01"},
            {"SELECT TRY_TO_DATE(s) FROM va904", "1970-01-01"},
            {"SELECT TRY_TO_DATE(a[0]::VARCHAR) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(a[0] AS DATE) FROM va904",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VARIANT "
                + "and DATE"},
            {"SELECT TRY_CAST(a[1] AS DATE) FROM va904",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VARIANT "
                + "and DATE"},
            {"SELECT TRY_CAST(TO_VARCHAR(a[0]) AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(CAST(a[0] AS VARCHAR) AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(a[0]::STRING AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST('1' AS TIME)", "00:00:01"},
            {"SELECT TRY_CAST(PARSE_JSON('\"1\"')::VARCHAR AS DATE)", "1970-01-01"},
            {"SELECT TRY_CAST(a[0]::VARCHAR(10) AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(a[1]::VARCHAR(10) AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(GET(a, 0)::VARCHAR AS DATE) FROM va904", "1970-01-01"},
            {"SELECT TRY_CAST(x.value::VARCHAR AS DATE) FROM va904, LATERAL FLATTEN(a) x WHERE x.index = 0",
                "1970-01-01"},
        });
    }

    @Test
    public void anArrayElementsTextReadsAsAConstantsDoes() {
        engine.execute("CREATE OR REPLACE TABLE va905 (i INT, a ARRAY, o OBJECT, v VARIANT)");
        engine.execute("INSERT INTO va905 SELECT 0, ARRAY_CONSTRUCT(PARSE_JSON('1')), OBJECT_CONSTRUCT('n', "
            + "PARSE_JSON('1')), PARSE_JSON('[1]')");
        engine.execute("INSERT INTO va905 SELECT 1, ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('n', 1), "
            + "PARSE_JSON('[\"1\"]')");
        engine.execute("INSERT INTO va905 SELECT 2, ARRAY_CONSTRUCT('1'), OBJECT_CONSTRUCT('n', '1'), "
            + "PARSE_JSON('[20200115]')");
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(TRY_CAST(a[0]::VARCHAR AS DATE), 'YYYYMMDD') FROM va905 WHERE i = 0", "19700101"},
            {"SELECT i, TRY_CAST(a[0]::VARCHAR AS DATE) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT i, a[0]::VARCHAR, TYPEOF(a[0]) FROM va905 ORDER BY i",
                "0|1|INTEGER ;; 1|1|INTEGER ;; 2|1|VARCHAR"},
            {"SELECT i, TRY_CAST(o:n::VARCHAR AS DATE) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT i, TRY_CAST(v[0]::VARCHAR AS DATE) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-08-22"},
            {"SELECT i, TRY_CAST(GET(a, 0)::VARCHAR AS DATE) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT i, TRY_TO_DATE(a[0]::VARCHAR) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT i, a[0]::VARCHAR::DATE FROM va905 ORDER BY i", "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT i, TRY_CAST(a[0]::VARCHAR AS TIME) FROM va905 ORDER BY i",
                "0|00:00:01 ;; 1|00:00:01 ;; 2|00:00:01"},
            {"SELECT i, TRY_CAST(CAST(a[0] AS VARCHAR) AS DATE) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT i, TRY_CAST(TO_VARCHAR(a[0]) AS DATE) FROM va905 ORDER BY i",
                "0|1970-01-01 ;; 1|1970-01-01 ;; 2|1970-01-01"},
            {"SELECT TRY_CAST(ARRAY_CONSTRUCT(PARSE_JSON('1'))[0]::VARCHAR AS DATE)", "1970-01-01"},
            {"SELECT TRY_CAST(ARRAY_CONSTRUCT(1)[0]::VARCHAR AS DATE)", "1970-01-01"},
            {"SELECT TRY_CAST([1][0]::VARCHAR AS DATE)", "1970-01-01"},
            {"SELECT TRY_CAST(OBJECT_CONSTRUCT('n', 1):n::VARCHAR AS DATE)", "1970-01-01"},
            {"SELECT i, TRY_CAST(a[0]::VARCHAR AS DATE) IS NULL FROM va905 ORDER BY i",
                "0|false ;; 1|false ;; 2|false"},
        });
    }
}
