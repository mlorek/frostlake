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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ALTER TABLE ADD COLUMN echoes a refused DEFAULT that moves a date or a timestamp by a quoted interval as the plan
 * holds it: one DATE_ADD shift per part of the interval, its amount a decimal converted to NUMBER(9,0) and negated
 * for a subtraction; an interval written first is refused by its argument types at the operator (all
 * live-verified).
 */
public class AddColumnIntervalDefaultTest extends MeasuredCellsTestSupport {

    @Test
    public void intervalDefaults() {
        engine.execute("CREATE OR REPLACE TABLE ac2091 (a INT)");
        engine.execute("INSERT INTO ac2091 VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(CAST(1.0 "
                + "AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d1 DATE DEFAULT CURRENT_DATE + INTERVAL '1 DAY'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(CAST(2.0 "
                + "AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d2 DATE DEFAULT CURRENT_DATE + INTERVAL '2 DAYS'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDDAYSTODATE(CAST(-1.0 AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d3 DATE DEFAULT CURRENT_DATE - INTERVAL '1 DAY'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDWEEKSTODATE(CAST(1.0 AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d4 DATE DEFAULT CURRENT_DATE + INTERVAL '1 WEEK'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDMONTHSTODATE(CAST(1.0 AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d5 DATE DEFAULT CURRENT_DATE + INTERVAL '1 MONTH'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDYEARSTODATE(CAST(1.0 AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d6 DATE DEFAULT CURRENT_DATE + INTERVAL '1 YEAR'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDHOURSTOTIMESTAMP(CAST(1.0 AS NUMBER(9,0)), CAST(CURRENT_DATE() AS TIMESTAMP_NTZ(9)))]",
            "ALTER TABLE ac2091 ADD COLUMN d7 TIMESTAMP_NTZ DEFAULT CURRENT_DATE + INTERVAL '1 HOUR'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDHOURSTOTIMESTAMP(CAST(12.0 AS NUMBER(9,0)), CAST(DATE_ADDDAYSTODATE(CAST(1.0 AS NUMBER(9,"
                + "0)), CURRENT_DATE()) AS TIMESTAMP_NTZ(9)))]",
            "ALTER TABLE ac2091 ADD COLUMN d8 DATE DEFAULT CURRENT_DATE + INTERVAL '1 DAY, 12 HOURS'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_ADDDAYSTOTIMESTAMP(CAST(1.0 AS NUMBER(9,0)), CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_NTZ(9)))]",
            "ALTER TABLE ac2091 ADD COLUMN d9 TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP::TIMESTAMP_NTZ + INTERVAL '1 "
                + "DAY'");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(CAST(1.5 "
                + "AS NUMBER(9,0)), CURRENT_DATE())]",
            "ALTER TABLE ac2091 ADD COLUMN d10 DATE DEFAULT CURRENT_DATE + 1.5");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 64 | Invalid argument types for "
                + "function '+': (INTERVAL, DATE)",
            "ALTER TABLE ac2091 ADD COLUMN d11 DATE DEFAULT INTERVAL '1 DAY' + CURRENT_DATE");
    }
}
