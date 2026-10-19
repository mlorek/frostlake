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
 * A set operation's arms convert to the column's type before their rows are combined (live-verified): a DATE arm
 * beside a TIMESTAMP becomes the timestamp of its midnight, so a DATE INTERSECT the same moment as a TIMESTAMP_LTZ,
 * TIMESTAMP_NTZ or TIMESTAMP_TZ keeps its row and EXCEPT drops it, and a VARIANT arm under an ARRAY column becomes an
 * array as TO_ARRAY makes one — a JSON null becomes NULL — while under an OBJECT column a value that holds no object
 * is "Failed to cast variant value 2 to OBJECT".
 */
public class SetOperationArmWideningTest extends MeasuredCellsTestSupport {

    @Test
    public void aDateArmWidensAndAVariantArmTakesTheSemiStructuredType() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x INTERSECT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x EXCEPT SELECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ)",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ AS x INTERSECT SELECT "
                + "'2024-01-01'::DATE)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x INTERSECT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_NTZ)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x MINUS SELECT '2024-01-01 00:00:00'::TIMESTAMP_NTZ)",
                "0"},
            {"SELECT TYPEOF(x) FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('2')) ORDER BY 1",
                "ARRAY ;; ARRAY"},
            {"SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('2')) ORDER BY 1",
                "[1] ;; [2]"},
            {"SELECT TYPEOF(x) FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('[3]')) ORDER BY 1",
                "ARRAY ;; ARRAY"},
            {"SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('{\"a\":1}')) ORDER BY 1",
                "[1] ;; [{\"a\":1}]"},
            {"SELECT x FROM (SELECT OBJECT_CONSTRUCT('a', 1) AS x UNION ALL SELECT PARSE_JSON('2')) ORDER BY 1",
                "REFUSED Failed to cast variant value 2 to OBJECT"},
            {"SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('null')) ORDER BY 1",
                "[1] ;; null"},
            {"SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION SELECT PARSE_JSON('\"s\"')) ORDER BY 1",
                "[1] ;; [\"s\"]"},
        });
    }

    @Test
    public void anArmConvertsBeforeTheRowsAreCompared() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"SELECT x IS NULL, TYPEOF(x) FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT "
                + "PARSE_JSON('null')) ORDER BY 1",
                "false|ARRAY ;; true|null"},
        });
        assertShape("X:ARRAY", "SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('2'))");
        assertShape("X:OBJECT",
            "SELECT x FROM (SELECT OBJECT_CONSTRUCT('a', 1) AS x UNION ALL SELECT PARSE_JSON('{\"b\":2}'))");
        assertCells(new String[][] {
            {"SELECT x FROM (SELECT OBJECT_CONSTRUCT('a', 1) AS x UNION ALL SELECT PARSE_JSON('{\"b\":2}')) ORDER BY 1",
                "{\"b\":2} ;; {\"a\":1}"},
            {"SELECT x FROM (SELECT OBJECT_CONSTRUCT('a', 1) AS x UNION ALL SELECT PARSE_JSON('[1]')) ORDER BY 1",
                "REFUSED Failed to cast variant value [1] to OBJECT"},
            {"SELECT TYPEOF(x) FROM (SELECT PARSE_JSON('2') AS x UNION ALL SELECT ARRAY_CONSTRUCT(1)) ORDER BY 1",
                "ARRAY ;; INTEGER"},
            {"SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION ALL SELECT PARSE_JSON('2') WHERE FALSE) ORDER BY 1",
                "[1]"},
            {"SELECT x FROM (SELECT ARRAY_CONSTRUCT(1) AS x UNION SELECT PARSE_JSON('1')) ORDER BY 1", "[1]"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x INTERSECT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_TZ)",
                "1"},
            {"SELECT x, SYSTEM$TYPEOF(x) FROM (SELECT '2024-01-01'::DATE AS x INTERSECT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_NTZ)",
                "2024-01-01T00:00|TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x INTERSECT SELECT '2024-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ INTERSECT SELECT '2024-01-01'::DATE)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT '2024-01-01'::DATE AS x MINUS SELECT '2024-01-01 00:00:00'::TIMESTAMP_TZ)",
                "0"},
            {"SELECT TO_VARCHAR(x), SYSTEM$TYPEOF(x) FROM (SELECT '2024-01-01'::DATE AS x INTERSECT SELECT "
                + "'2024-01-01 00:00:00'::TIMESTAMP_LTZ)",
                "2024-01-01 00:00:00.000 Z|TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT TO_VARCHAR(x) FROM (SELECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ AS x EXCEPT SELECT "
                + "'2024-01-02'::DATE)",
                "2024-01-01 00:00:00.000 Z"},
        });
    }
}
