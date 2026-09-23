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

package dev.frostlake.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The session's output formats govern every string rendering of a date, a time or a timestamp: a cast to text, a
 * concatenation, TO_VARCHAR and TO_CHAR without a format, a string function's argument, LIKE and a value embedded in a
 * VARIANT. TIMESTAMP_LTZ and TIMESTAMP_TZ fall back to TIMESTAMP_OUTPUT_FORMAT, TIMESTAMP_NTZ only once its own is
 * emptied, and DATE and TIME stand apart.
 */
public class SessionOutputFormatTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE TABLE tf (n9 TIMESTAMP_NTZ(9), t9 TIME(9), l3 TIMESTAMP_LTZ(3), z3 TIMESTAMP_TZ(3), d DATE)");
        engine.execute("INSERT INTO tf SELECT '2020-01-01 10:00:00.123456789', '10:00:00.123456789', "
            + "'2020-01-01 10:00:00.5', '2020-01-01 10:00:00.5 +02:00', '2020-01-01'");
    }

    @Override
    protected void teardownTest() {
        for (final String name : new String[] {"TIMESTAMP_OUTPUT_FORMAT", "TIMESTAMP_NTZ_OUTPUT_FORMAT",
            "TIMESTAMP_LTZ_OUTPUT_FORMAT", "TIMESTAMP_TZ_OUTPUT_FORMAT", "TIME_OUTPUT_FORMAT", "DATE_OUTPUT_FORMAT",
            "TIMEZONE"}) {
            engine.execute("ALTER SESSION UNSET " + name);
        }
    }

    private String row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            out.append(i > 0 ? "|" : "").append(rs.getRows().get(0).getValue(i));
        }
        return out.toString();
    }

    @Test
    public void eachFamilyRendersInItsOwnFormat() {
        assertEquals("2020-01-01 10:00:00.123|10:00:00|2020-01-01 10:00:00.500 Z|2020-01-01 10:00:00.500 +0200|2020-01-01",
            row("SELECT n9::VARCHAR, t9::VARCHAR, l3::VARCHAR, z3::VARCHAR, d::VARCHAR FROM tf"));
        engine.execute("ALTER SESSION SET TIMESTAMP_OUTPUT_FORMAT = 'YYYY-MM-DD\"T\"HH24:MI:SS'");
        assertEquals("2020-01-01 10:00:00.123|2020-01-01T10:00:00|2020-01-01T10:00:00|10:00:00|2020-01-01",
            row("SELECT n9::VARCHAR, l3::VARCHAR, z3::VARCHAR, t9::VARCHAR, d::VARCHAR FROM tf"));
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = ''");
        assertEquals("2020-01-01T10:00:00", row("SELECT n9::VARCHAR FROM tf"));
    }

    @Test
    public void everyStringPathFollowsIt() {
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY/MM/DD HH24:MI:SS.FF6'");
        final String n = "2020/01/01 10:00:00.123456";
        assertEquals(n + "|26|" + n + "|x" + n + "|" + n + "|" + n + "|true|" + n + "|" + n,
            row("SELECT UPPER(n9), LENGTH(n9), n9::VARCHAR, 'x' || n9, TO_VARCHAR(n9), CONCAT(n9, ''), "
                + "n9 LIKE '2020/%', CAST(n9 AS STRING), TO_CHAR(n9) FROM tf"));
        assertEquals("{\"t\":\"" + n + "\"}|" + n + "|[\"" + n + "\"]",
            row("SELECT OBJECT_CONSTRUCT('t', n9)::VARCHAR, TO_VARIANT(n9)::VARCHAR, ARRAY_CONSTRUCT(n9)::VARCHAR FROM tf"));
        assertEquals("2020", row("SELECT TO_VARCHAR(n9, 'YYYY') FROM tf"));
        assertEquals("2020/02/03 04:05:06.000000", row("SELECT '2020-02-03 04:05:06'::TIMESTAMP_NTZ::VARCHAR"));
        engine.execute("ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT");
        engine.execute("ALTER SESSION SET TIME_OUTPUT_FORMAT = 'HH24.MI.SS.FF3'");
        assertEquals("10.00.00.123|12|10.00.00.123|x10.00.00.123",
            row("SELECT UPPER(t9), LENGTH(t9), t9::VARCHAR, 'x' || t9 FROM tf"));
        engine.execute("ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'");
        assertEquals("01/01/2020|01/01/2020|x01/01/2020|true|01/01/2020",
            row("SELECT UPPER(d), d::VARCHAR, 'x' || d, d LIKE '01/%', TO_VARCHAR(d) FROM tf"));
        engine.execute("ALTER SESSION SET TIMESTAMP_TZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI TZHTZM'");
        assertEquals("2020-01-01 10:00 +0200|x2020-01-01 10:00 +0200|2020-01-01 10:00:00.500 Z",
            row("SELECT z3::VARCHAR, 'x' || z3, l3::VARCHAR FROM tf"));
        engine.execute("ALTER SESSION UNSET TIMESTAMP_TZ_OUTPUT_FORMAT");
        engine.execute("ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS TZH:TZM'");
        assertEquals("2020-01-01 10:00:00 Z|x2020-01-01 10:00:00 Z|2020-01-01 10:00:00.500 +0200",
            row("SELECT l3::VARCHAR, 'x' || l3, z3::VARCHAR FROM tf"));
    }

    @Test
    public void theNtzFormatTakesNoAuto() {
        assertEquals("SQL compilation error:\ninvalid value [AUTO] for parameter 'TIMESTAMP_NTZ_OUTPUT_FORMAT'",
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'AUTO'");
                }
            }).getMessage());
    }
}
