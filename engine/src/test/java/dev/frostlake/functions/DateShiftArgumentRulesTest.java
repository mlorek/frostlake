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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Three argument rules of the date shifts and differences, live-verified:
 *
 * <ul>
 *   <li>a VARIANT value of DATEDIFF converts to the other value's declared type, so a JSON number beside a
 *       DATE fails the variant's cast where Frostlake read it as a day count;</li>
 *   <li>a bare NULL folds the call to an untyped NULL wherever it stands, the unit and ADD_MONTHS
 *       included;</li>
 *   <li>a day-or-coarser or hour amount must fit a 32-bit integer once rounded, where Frostlake shifted
 *       by it, and a week, quarter or year count wraps in 32 bits as it is multiplied.</li>
 * </ul>
 */
public class DateShiftArgumentRulesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("""
            CREATE TABLE vt (d DATE, ts TIMESTAMP_NTZ, t TIME, ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ, v VARIANT,
                vb VARIANT, vs VARIANT, vx VARIANT)""");
        engine.execute("""
            INSERT INTO vt SELECT '2024-01-15', '2024-01-15 10:00:00', '10:00:00', '2024-01-15 10:00:00',
                '2024-01-15 10:00:00 +02:00', PARSE_JSON('2'), PARSE_JSON('true'), PARSE_JSON('"2024-01-10"'),
                PARSE_JSON('"abc"')""");
    }

    @Override
    protected void teardownTest() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aVariantConvertsToTheOtherValuesType() {
        assertEquals("Failed to cast variant value 2 to DATE", refusal("SELECT DATEDIFF(day, v, d) FROM vt"));
        assertEquals("Failed to cast variant value 2 to DATE", refusal("SELECT DATEDIFF(day, d, v) FROM vt"));
        assertEquals("Failed to cast variant value 2 to DATE", refusal("SELECT TIMEDIFF(day, v, d) FROM vt"));
        assertEquals("Failed to cast variant value 2 to DATE", refusal("SELECT TIMESTAMPDIFF(day, v, d) FROM vt"));
        assertEquals("Failed to cast variant value [1] to DATE",
            refusal("SELECT DATEDIFF(day, PARSE_JSON('[1]'), d) FROM vt"));
        assertEquals("Failed to cast variant value true to TIMESTAMP_LTZ", refusal("SELECT DATEDIFF(hour, vb, ltz) FROM vt"));
        assertEquals("Failed to cast variant value true to TIMESTAMP_TZ", refusal("SELECT DATEDIFF(hour, vb, tz) FROM vt"));
        assertEquals("Failed to cast variant value true to TIMESTAMP_NTZ",
            refusal("SELECT DATEDIFF(day, vb, '2024-01-15') FROM vt"));
        assertEquals("Failed to cast variant value \"abc\" to TIMESTAMP_NTZ", refusal("SELECT DATEDIFF(day, vx, v) FROM vt"));
        assertEquals("Failed to cast variant value 2 to DATE", refusal("SELECT DATEDIFF(day, v, NULL::DATE) FROM vt"));
        assertEquals(List.of("5", "19737", "null"),
            row("SELECT DATEDIFF(day, vs, d), DATEDIFF(day, v, ts), DATEDIFF(day, vb, NULL) FROM vt"));
    }

    @Test
    public void aBareNullFoldsTheCallWhereverItStands() {
        assertEquals(List.of("NULL[LOB]", "NULL[LOB]", "NULL[LOB]", "NULL[LOB]", "NULL[LOB]", "NULL[LOB]"),
            row("""
                SELECT SYSTEM$TYPEOF(DATEADD(NULL, 1, ts)), SYSTEM$TYPEOF(TIMEADD(NULL, 1, t)),
                    SYSTEM$TYPEOF(DATEDIFF(NULL, d, d)), SYSTEM$TYPEOF(DATE_TRUNC(NULL, d)),
                    SYSTEM$TYPEOF(ADD_MONTHS(d, NULL)), SYSTEM$TYPEOF(ADD_MONTHS(NULL, 1))
                FROM vt"""));
    }

    @Test
    public void aCoarseAmountMustFitThirtyTwoBits() {
        assertEquals(List.of("5881634-07-25", "5877588-07-06", "2092-02-02 13:14:08.000", "6107-02-07 12:08:00.000"),
            row("""
                SELECT DATEADD(day, 2147483647, d)::VARCHAR, (d - 2147483648)::VARCHAR,
                    DATEADD(second, 2147483648, ts)::VARCHAR, DATEADD(minute, 2147483648, ts)::VARCHAR
                FROM vt"""));
        final String past = "Numeric value '2147483648' is out of range";
        assertEquals(past, refusal("SELECT DATEADD(day, 2147483648, d) FROM vt"));
        assertEquals(past, refusal("SELECT DATEADD(day, 2147483647.5, d) FROM vt"));
        assertEquals(past, refusal("SELECT DATEADD(month, 2147483648, d) FROM vt"));
        assertEquals(past, refusal("SELECT DATEADD(quarter, 2147483648, d) FROM vt"));
        assertEquals(past, refusal("SELECT DATEADD(year, 2147483648, d) FROM vt"));
        assertEquals(past, refusal("SELECT DATEADD(hour, 2147483648, ts) FROM vt"));
        assertEquals(past, refusal("SELECT TIMEADD(hour, 2147483648, t) FROM vt"));
        assertEquals(past, refusal("SELECT ADD_MONTHS(d, 2147483648) FROM vt"));
        assertEquals(past, refusal("SELECT d + 2147483648 FROM vt"));
        assertEquals("Numeric value '-2147483649' is out of range", refusal("SELECT d - 2147483649 FROM vt"));
        assertEquals("Numeric value '-2147483649' is out of range", refusal("SELECT DATEADD(day, -2147483649, d) FROM vt"));
        assertEquals("Numeric value '3000000000' is out of range", refusal("SELECT DATEADD(day, 3e9, d) FROM vt"));
        assertEquals("Numeric value '9223372036854775808' is not recognized",
            refusal("SELECT DATEADD(second, 9223372036854775808, ts) FROM vt"));
    }

    @Test
    public void weekQuarterAndYearCountsWrapAsTheyAreMultiplied() {
        assertEquals(List.of("2023-01-15", "5881634-07-19", "178958994-06-15", "2124-01-15", "2025-04-15", "2024-02-05"),
            row("""
                SELECT DATEADD(year, 2147483647, d)::VARCHAR, DATEADD(week, 2147483647, d)::VARCHAR,
                    DATEADD(quarter, 2147483647, d)::VARCHAR, DATEADD(year, 100, d)::VARCHAR,
                    DATEADD(quarter, 5, d)::VARCHAR, DATEADD(week, 3, d)::VARCHAR
                FROM vt"""));
        assertEquals(List.of("2024-03-15", "2024-04-15", "2023-11-15", "2024-02-15"),
            row("""
                SELECT ADD_MONTHS(d, 1.5)::VARCHAR, ADD_MONTHS(d, 2.5)::VARCHAR, ADD_MONTHS(d, -1.5)::VARCHAR,
                    ADD_MONTHS(d, 1.4)::VARCHAR
                FROM vt"""));
    }
}
