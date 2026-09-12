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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A TIME or TIMESTAMP declared with a precision keeps it on every surface that prints the type
 * (live-verified): {@code SYSTEM$TYPEOF} over the column, a cast and a fold, the TRY_CAST sentence,
 * the argument-type list, and the catalog's DATETIME_PRECISION. The storage tag follows the declared
 * precision's value range — SB8 for a three-digit timestamp and SB4 for a three-digit time, where the
 * default nine digits read SB16 and SB8.
 */
public class DeclaredTemporalPrecisionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tp (ts3 TIMESTAMP_NTZ(3), tl3 TIMESTAMP_LTZ(3), t3 TIME(3),"
            + " ts TIMESTAMP_NTZ, t TIME, ts0 TIMESTAMP_NTZ(0), t0 TIME(0))");
        engine.execute("INSERT INTO tp SELECT '2020-01-01 10:00:00.123456789', '2020-01-01 10:00:00.123456789',"
            + " '10:00:00.123456789', '2020-01-01 10:00:00.123456789', '10:00:00.123456789',"
            + " '2020-01-01 10:00:00.123456789', '10:00:00.123456789'");
    }

    /** The type text DESC TABLE prints for one column. */
    private String describedType(final String column) {
        for (final Row row : engine.executeQuery("DESC TABLE tp").getRows()) {
            if (column.equals(String.valueOf(row.getValue(0)))) {
                return String.valueOf(row.getValue(1));
            }
        }
        return "<no such column>";
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void theDeclaredPrecisionIsTheTypeText() {
        assertEquals("TIMESTAMP_NTZ(3)[SB8]", cell("SELECT SYSTEM$TYPEOF(ts3) FROM tp"));
        assertEquals("TIMESTAMP_LTZ(3)[SB8]", cell("SELECT SYSTEM$TYPEOF(tl3) FROM tp"));
        assertEquals("TIME(3)[SB4]", cell("SELECT SYSTEM$TYPEOF(t3) FROM tp"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", cell("SELECT SYSTEM$TYPEOF(ts) FROM tp"));
        assertEquals("TIME(9)[SB8]", cell("SELECT SYSTEM$TYPEOF(t) FROM tp"));
        assertEquals("TIMESTAMP_NTZ(3)[SB8]", cell("SELECT SYSTEM$TYPEOF(ts::TIMESTAMP_NTZ(3)) FROM tp"));
        assertEquals("TIMESTAMP_NTZ(3)[SB8]", cell("SELECT SYSTEM$TYPEOF(IFF(TRUE, ts3, ts3)) FROM tp"));
        assertEquals("3", cell("SELECT datetime_precision FROM information_schema.columns"
            + " WHERE table_name = 'TP' AND column_name = 'TS3'"));
        assertEquals("3", cell("SELECT datetime_precision FROM information_schema.columns"
            + " WHERE table_name = 'TP' AND column_name = 'T3'"));
        assertEquals("TIMESTAMP_NTZ(0)[SB8]", cell("SELECT SYSTEM$TYPEOF(ts0) FROM tp"));
        assertEquals("TIME(0)[SB4]", cell("SELECT SYSTEM$TYPEOF(t0) FROM tp"));
        assertEquals("TIME(3)[SB4]", cell("SELECT SYSTEM$TYPEOF(ts::TIME(3)) FROM tp"));
        // The tag is the byte width the declared range needs: seven timestamp digits still fit
        // eight bytes, four time digits still fit four.
        assertEquals("TIMESTAMP_NTZ(6)[SB8]", cell("SELECT SYSTEM$TYPEOF(ts::TIMESTAMP_NTZ(6)) FROM tp"));
        assertEquals("TIMESTAMP_NTZ(7)[SB8]", cell("SELECT SYSTEM$TYPEOF(ts::TIMESTAMP_NTZ(7)) FROM tp"));
        assertEquals("TIMESTAMP_NTZ(8)[SB16]", cell("SELECT SYSTEM$TYPEOF(ts::TIMESTAMP_NTZ(8)) FROM tp"));
        assertEquals("TIMESTAMP_LTZ(6)[SB8]", cell("SELECT SYSTEM$TYPEOF(ts::TIMESTAMP_LTZ(6)) FROM tp"));
        assertEquals("TIMESTAMP_TZ(8)[SB16]", cell("SELECT SYSTEM$TYPEOF(ts::TIMESTAMP_TZ(8)) FROM tp"));
        assertEquals("TIME(4)[SB4]", cell("SELECT SYSTEM$TYPEOF(t::TIME(4)) FROM tp"));
        assertEquals("TIME(5)[SB8]", cell("SELECT SYSTEM$TYPEOF(t::TIME(5)) FROM tp"));
        // A fold widens to the widest precision among its branches; a date function answers nine.
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", cell("SELECT SYSTEM$TYPEOF(COALESCE(ts3, ts)) FROM tp"));
        assertEquals("TIME(9)[SB8]", cell("SELECT SYSTEM$TYPEOF(COALESCE(t3, t)) FROM tp"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", cell("SELECT SYSTEM$TYPEOF(DATEADD(day, 1, ts3)) FROM tp"));
        // The catalog and the value keep it too.
        assertEquals("TIMESTAMP_NTZ(3)", describedType("TS3"));
        assertEquals("TIME(3)", describedType("T3"));
        assertEquals("TIMESTAMP_NTZ(0)", describedType("TS0"));
        assertEquals("0", cell("SELECT datetime_precision FROM information_schema.columns"
            + " WHERE table_name = 'TP' AND column_name = 'TS0'"));
        assertEquals("2020-01-01 10:00:00.000", cell("SELECT ts0::VARCHAR FROM tp"));
        assertEquals("2020-01-01 10:00:00.123", cell("SELECT ts3::VARCHAR FROM tp"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types"
            + " TIMESTAMP_NTZ(3) and TIME(9)", refusal("SELECT TRY_TO_TIME(ts3) FROM tp"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ABS': (TIMESTAMP_NTZ(3))", refusal("SELECT ABS(ts3) FROM tp"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ABS': (TIME(3))", refusal("SELECT ABS(t3) FROM tp"));
    }
}
