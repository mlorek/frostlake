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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A Snowpark Variant's asTimestamp reads its text as the java.sql classes read one, a wall clock of the session's
 * zone: in the Julian calendar before the Gregorian reform and with the zone's standard offset before its first
 * transition, so {@code 0001-01-01 00:00:00} in America/Los_Angeles is -62135740800000; a text with a zone offset, a
 * {@code T}, a date alone or a five-digit year is no timestamp (all live-verified).
 */
public class SnowparkVariantTimestampTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextIsReadAsTheSessionsWallClock() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE FUNCTION jt(s VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\n"
            + "import com.snowflake.snowpark_java.types.Variant;\n"
            + "class H {\n"
            + "  public static String f(String s) {\n"
            + "    try {\n"
            + "      return String.valueOf(new Variant(s).asTimestamp().getTime());\n"
            + "    } catch (Exception e) {\n"
            + "      return \"E:\" + e.getClass().getName() + \": \" + e.getMessage();\n"
            + "    }\n"
            + "  }\n"
            + "}\n$$");
        try {
            assertCells(new String[][] {
                {"SELECT jt('1969-12-31 23:59:59')", "28799000"},
                {"SELECT jt('1900-01-01 00:00:00')", "-2208960000000"},
                {"SELECT jt('2018-11-04 00:00:00')", "1541314800000"},
                {"SELECT jt('2024-01-02 03:04:05.123456789')", "1704193445123"},
                {"SELECT jt('0001-01-01 00:00:00')", "-62135740800000"},
                {"SELECT jt('2024-01-02 03:04:05 +05:00')",
                    "E:java.lang.NumberFormatException: Error at index 2 in: \"05 +05:00\""},
                {"SELECT jt('2024-01-02T03:04:05Z')",
                    "E:java.lang.IllegalArgumentException: Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]"},
                {"SELECT jt('2024-01-02')",
                    "E:java.lang.IllegalArgumentException: Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]"},
                {"SELECT jt('12024-01-01 00:00:00')",
                    "E:java.lang.IllegalArgumentException: Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]"},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
