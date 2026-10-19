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
 * INTERSECT, EXCEPT and MINUS compare and answer in the set operation's folded column types, as UNION does
 * (live-verified): a text arm beside a NUMBER arm converts to the unified NUMBER(18,5), a TIMESTAMP_NTZ meets an
 * equal TIMESTAMP_LTZ or TIMESTAMP_TZ, and an exact NUMBER beside a FLOAT compares as a FLOAT, NaN and infinity
 * included.
 */
public class SetOperationFoldedTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextArmConvertsToTheFoldedType() {
        engine.execute("CREATE OR REPLACE TABLE s (x VARCHAR)");
        engine.execute("INSERT INTO s VALUES ('01'), ('1.50'), (' 7')");
        assertCells(new String[][] {
            {"SELECT x FROM s EXCEPT SELECT 99 ORDER BY 1", "1.00000 ;; 1.50000 ;; 7.00000"},
            {"SELECT x FROM s UNION SELECT 99 ORDER BY 1", "1.00000 ;; 1.50000 ;; 7.00000 ;; 99.00000"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT x FROM s EXCEPT SELECT 99 UNION ALL SELECT 1)", "3"},
            {"SELECT d, SYSTEM$TYPEOF(d) FROM (SELECT '2024-01-01' AS d EXCEPT SELECT '2024-02-01'::DATE)",
                "2024-01-01|DATE[SB4]"},
            {"SELECT 1 AS n EXCEPT SELECT 2.5::NUMBER(10,2)", "1.00"},
            {"SELECT x FROM s INTERSECT SELECT 1 ORDER BY 1", "1.00000"},
            {"SELECT x FROM s MINUS SELECT 7 ORDER BY 1", "1.00000 ;; 1.50000"},
        });
    }

    @Test
    public void equalMomentsOfDifferentTimestampKindsMeet() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"SELECT '2024-01-01 00:00:00'::TIMESTAMP_NTZ = '2024-01-01 00:00:00'::TIMESTAMP_LTZ", "true"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_NTZ x UNION SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_NTZ x INTERSECT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_NTZ x EXCEPT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ)",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_TZ x EXCEPT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_NTZ)",
                "0"},
            {"SELECT SYSTEM$TYPEOF(x), REPLACE(x::VARCHAR, ':', '.') FROM (SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_NTZ x INTERSECT SELECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ)",
                "TIMESTAMP_LTZ(9)[SB16]|2024-01-01 00.00.00.000 Z"},
        });
    }

    @Test
    public void anExactNumberBesideAFloatComparesAsAFloat() {
        assertCells(new String[][] {
            {"SELECT 'NaN'::FLOAT x INTERSECT SELECT 1.5::NUMBER(10,2)", ""},
            {"SELECT 'inf'::FLOAT x EXCEPT SELECT 1.5::NUMBER(10,2)", "Infinity"},
            {"SELECT 'NaN'::FLOAT x UNION SELECT 1.5::NUMBER(10,2) ORDER BY 1", "1.5 ;; NaN"},
            {"SELECT 12345678901234567891 = 12345678901234567891::FLOAT", "true"},
            {"SELECT COUNT(*) FROM (SELECT 12345678901234567891 x INTERSECT SELECT 12345678901234567891::FLOAT)", "1"},
            {"SELECT x, SYSTEM$TYPEOF(x) FROM (SELECT 1 x INTERSECT SELECT 1.0::FLOAT)", "1.0|FLOAT[DOUBLE]"},
        });
    }
}
