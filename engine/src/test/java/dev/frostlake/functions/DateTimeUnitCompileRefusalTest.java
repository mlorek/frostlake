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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A date/time unit a function does not take is refused while the statement compiles, so a table with no rows
 * refuses it as one with rows does: {@code DATEADD(zz, 1, ts)} is {@code ['ZZ'] is not a valid date/time
 * component for function DATEADD.}, a written text echoed as written, and a unit a TIME does not have names
 * the type. Every cell is live-verified.
 */
public class DateTimeUnitCompileRefusalTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE e (ts TIMESTAMP_NTZ, d DATE, t TIME)");
        engine.execute("CREATE OR REPLACE TABLE r (ts TIMESTAMP_NTZ, d DATE, t TIME)");
        engine.execute("INSERT INTO r VALUES ('2024-01-01 10:00:00', '2024-01-01', '10:00:00')");
    }

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalid(final String unit, final String function) {
        return "SQL compilation error: [" + unit + "] is not a valid date/time component for function " + function + ".";
    }

    @Test
    public void anUnknownUnitIsRefusedOverNoRows() {
        final String[][] cells = {
            {"SELECT DATEADD(zz, 1, ts) FROM e", invalid("'ZZ'", "DATEADD")},
            {"SELECT DATEADD('zz', 1, ts) FROM e", invalid("'zz'", "DATEADD")},
            {"SELECT DATE_TRUNC('dayofweek', d) FROM e", invalid("'dayofweek'", "DATE_TRUNC")},
            {"SELECT DATE_TRUNC(zz, d) FROM e", invalid("'ZZ'", "DATE_TRUNC")},
            {"SELECT DATEDIFF(zz, d, d) FROM e", invalid("'ZZ'", "DATEDIFF")},
            {"SELECT TIMEADD(zz, 1, t) FROM e", invalid("'ZZ'", "TIMEADD")},
            {"SELECT TIMESTAMPADD(zz, 1, ts) FROM e", invalid("'ZZ'", "TIMESTAMPADD")},
            {"SELECT TIMEDIFF(zz, ts, ts) FROM e", invalid("'ZZ'", "TIMEDIFF")},
            {"SELECT TIMESTAMPDIFF(zz, ts, ts) FROM e", invalid("'ZZ'", "TIMESTAMPDIFF")},
            {"SELECT 1 FROM e WHERE DATEADD(zz, 1, ts) > ts", invalid("'ZZ'", "DATEADD")},
            {"SELECT DATEADD('', 1, ts) FROM e", invalid("''", "DATEADD")},
            {"SELECT DATE_TRUNC('', d) FROM e", invalid("''", "DATE_TRUNC")},
            {"SELECT TRUNC(d, 'zz') FROM e", invalid("'zz'", "TRUNC")},
            {"SELECT DATEADD(dayofweek, 1, d) FROM e", invalid("'DAYOFWEEK'", "DATEADD")},
            {"SELECT DATEDIFF('dayofweek', d, d) FROM e", invalid("'dayofweek'", "DATEDIFF")},
            {"SELECT DATE_TRUNC(zz, t) FROM e", invalid("'ZZ'", "DATE_TRUNC")},
            {"SELECT DATEADD(zz, 1, ts) FROM r", invalid("'ZZ'", "DATEADD")},
            {"SELECT DATE_TRUNC('dayofweek', d) FROM r", invalid("'dayofweek'", "DATE_TRUNC")},
            {"SELECT DATEADD(day, 1, t) FROM e",
                "SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9)."},
            {"SELECT DATE_TRUNC('day', t) FROM e",
                "SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC and type TIME."},
            {"SELECT DATEADD(hour, 1, d) FROM e", "no row"},
            {"SELECT DATE_TRUNC('hour', d) FROM e", "no row"},
            {"SELECT DATEDIFF(hour, d, d) FROM e", "no row"},
            {"SELECT DATEADD(nanosecond, 1, d) FROM e", "no row"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
