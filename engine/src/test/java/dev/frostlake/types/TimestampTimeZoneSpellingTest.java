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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The worded timestamp types (live-verified): TIMESTAMP WITH TIME ZONE is TIMESTAMP_TZ, TIMESTAMP WITHOUT TIME ZONE
 * TIMESTAMP_NTZ and TIMESTAMP WITH LOCAL TIME ZONE TIMESTAMP_LTZ, in a column, a cast, a signature and an Iceberg
 * table alike; a precision is written after the words, never before them.
 */
public class TimestampTimeZoneSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void theWordedTimestampTypesAreTheirNamedOnes() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tzt (c TIMESTAMP WITH TIME ZONE, d TIMESTAMP WITHOUT TIME ZONE, e "
                + "TIMESTAMP WITH LOCAL TIME ZONE)",
                "Table TZT successfully created."},
        });
        engine.execute("DESCRIBE TABLE tzt");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\", \"default\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "C|TIMESTAMP_TZ(9)|Y|null|null ;; D|TIMESTAMP_NTZ(9)|Y|null|null ;; E|TIMESTAMP_LTZ(9)|Y|null|null"},
            {"SELECT SYSTEM$TYPEOF('2020-01-01'::TIMESTAMP WITH TIME ZONE)", "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(CAST('2020-01-01' AS TIMESTAMP WITHOUT TIME ZONE))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF('2020-01-01'::TIMESTAMP WITH LOCAL TIME ZONE)", "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(CAST('2020-01-01' AS TIMESTAMP WITHOUT TIME ZONE(3)))", "TIMESTAMP_NTZ(3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(CAST('2020-01-01' AS TIMESTAMP WITH TIME ZONE(3)))", "TIMESTAMP_TZ(3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(CAST(NULL AS TIMESTAMP WITHOUT TIME ZONE))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(TRY_CAST('2020-01-01' AS TIMESTAMP WITH TIME ZONE))", "TIMESTAMP_TZ(9)[SB16]"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_tz (c TIMESTAMP WITHOUT TIME ZONE)",
                "Table ICE_TZ successfully created."},
        });
        engine.execute("DESCRIBE TABLE ice_tz");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\", \"default\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "C|TIMESTAMP_NTZ(6)|Y|null|null"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_tz2 (c TIMESTAMP WITH TIME ZONE)",
                "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables."},
            {"CREATE OR REPLACE ICEBERG TABLE ice_tz3 (c TIMESTAMP WITH LOCAL TIME ZONE)",
                "Table ICE_TZ3 successfully created."},
        });
        engine.execute("DESCRIBE TABLE ice_tz3");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\", \"default\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "C|TIMESTAMP_LTZ(6)|Y|null|null"},
            {"DESCRIBE TABLE t1 (TIMESTAMP WITH TIME ZONE)",
                "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            {"DESCRIBE TABLE t1 (TIMESTAMP WITHOUT TIME ZONE)",
                "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            {"DESCRIBE TABLE t1 (TIMESTAMP WITH TIME ZONE, INT)",
                "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            {"DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITH TIME ZONE)",
                "Drop statement executed successfully (NOSUCH_X already dropped)."},
            {"DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITHOUT TIME ZONE)",
                "Drop statement executed successfully (NOSUCH_X already dropped)."},
            {"DESCRIBE TABLE t1 (TIMESTAMP WITHOUT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'WITHOUT'."},
            {"SELECT CAST('2020-01-01' AS TIMESTAMP WITHOUT TIME)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ')'."},
            {"CREATE OR REPLACE TABLE tzk (c TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP())",
                "Table TZK successfully created."},
        });
        engine.execute("DESCRIBE TABLE tzk");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fzt(x TIMESTAMP WITH TIME ZONE) RETURNS TIMESTAMP WITHOUT TIME ZONE AS "
                + "'x::TIMESTAMP_NTZ'",
                "Function FZT successfully created."},
            {"SELECT fzt('2020-01-01 10:00:00 +02:00'::TIMESTAMP_TZ)", "2020-01-01T10:00"},
            {"SELECT 1 FROM t1 WHERE x::TIMESTAMP WITH TIME ZONE IS NULL", ""},
            {"SELECT '2020-01-01'::TIMESTAMP WITH TIME ZONE = '2020-01-01'::TIMESTAMP_TZ", "true"},
        });
    }
}
