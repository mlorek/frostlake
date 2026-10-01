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
 * A VARIANT operand of a date shift or a date difference, live-verified. DATEADD, TIMEADD and TIMESTAMPADD read a
 * VARIANT as a TIMESTAMP_NTZ, and DATEDIFF and its names read one as the other operand's type, or as a
 * TIMESTAMP_NTZ beside another VARIANT. So a DATE, a TIME or a timestamp the VARIANT holds converts only within its
 * own family — a held DATE fails a shift, and a held timestamp fails beside a DATE — where Frostlake read the held
 * value as itself.
 */
public class VariantDateShiftTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
    }

    @Override
    protected void teardownTest() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
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

    @Test
    public void aHeldDateOrTimeFailsTheShiftsCast() {
        final String date = "Failed to cast variant value \"2024-01-15\" to TIMESTAMP_NTZ";
        assertEquals(date, refusal("SELECT DATEADD(day, 1, TO_VARIANT('2024-01-15'::DATE))"));
        assertEquals(date, refusal("SELECT TIMESTAMPADD(day, 1, TO_VARIANT('2024-01-15'::DATE))"));
        assertEquals(date, refusal("SELECT DATEADD(day, 1, OBJECT_CONSTRUCT('d', '2024-01-15'::DATE):d)"));
        assertEquals(date, refusal("SELECT TIMEADD(hour, 1, TO_VARIANT('2024-01-15'::DATE))"));
        assertEquals(date, refusal("SELECT DATEADD(day, 1, v) FROM (SELECT TO_VARIANT('2024-01-15'::DATE) v)"));
        assertEquals("Failed to cast variant value \"2024-01-31\" to TIMESTAMP_NTZ",
            refusal("SELECT DATEADD(month, 1, TO_VARIANT('2024-01-31'::DATE))"));
        assertEquals("Failed to cast variant value \"10:00:00\" to TIMESTAMP_NTZ",
            refusal("SELECT TIMEADD(hour, 1, TO_VARIANT('10:00:00'::TIME))"));
        assertEquals("Failed to cast variant value \"10:00:00\" to TIMESTAMP_NTZ",
            refusal("SELECT DATEADD(hour, 1, TO_VARIANT('10:00:00'::TIME))"));
        assertEquals("Failed to cast variant value \"10:00:00\" to TIMESTAMP_NTZ",
            refusal("SELECT DATEADD(day, 1, PARSE_JSON('\"10:00:00\"'))"));
        assertEquals("Failed to cast variant value true to TIMESTAMP_NTZ", refusal("SELECT DATEADD(day, 1, PARSE_JSON('true'))"));
        assertEquals("Failed to cast variant value [1] to TIMESTAMP_NTZ", refusal("SELECT DATEADD(day, 1, PARSE_JSON('[1]'))"));
        assertEquals("Failed to cast variant value \"AB\" to TIMESTAMP_NTZ",
            refusal("SELECT DATEADD(day, 1, TO_VARIANT(TO_BINARY('AB', 'HEX')))"));
    }

    @Test
    public void aShiftReadsTextNumbersAndTimestampsAsTimestampNtz() {
        assertEquals(List.of("2024-01-16 10:00:00.000", "2024-01-16 10:00:00.000", "2024-01-16 10:00:00.000",
                "2024-01-16 00:00:00.000", "2024-01-15 11:00:00.000", "2024-01-15 11:30:00.000"), row("""
            SELECT DATEADD(day, 1, TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ))::VARCHAR,
                DATEADD(day, 1, TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_LTZ))::VARCHAR,
                DATEADD(day, 1, TO_VARIANT('2024-01-15 10:00:00 +02:00'::TIMESTAMP_TZ))::VARCHAR,
                DATEADD(day, 1, PARSE_JSON('"2024-01-15"'))::VARCHAR,
                TIMEADD(hour, 1, PARSE_JSON('"2024-01-15 10:00:00"'))::VARCHAR,
                TIMESTAMPADD(minute, 90, TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ))::VARCHAR"""));
        assertEquals(List.of("2024-01-16 10:00:00.000", "null", "null", "2024-01-16"), row("""
            SELECT DATEADD(day, 1, PARSE_JSON('1705312800'))::VARCHAR, DATEADD(day, 1, PARSE_JSON('null')),
                DATEADD(day, 1, TO_VARIANT(NULL::DATE)), DATEADD(day, 1, TO_VARIANT('2024-01-15'::DATE)::DATE)::VARCHAR"""));
        assertEquals(List.of("TIMESTAMP_NTZ(9)[SB16]"),
            row("SELECT SYSTEM$TYPEOF(DATEADD(day, 1, TO_VARIANT('2024-01-15 10:00:00 +02:00'::TIMESTAMP_TZ)))"));
    }

    @Test
    public void aDifferenceReadsAVariantAsTheOtherOperandsType() {
        final String date = "Failed to cast variant value \"2024-01-15\" to TIMESTAMP_NTZ";
        assertEquals(date, refusal("SELECT DATEDIFF(day, TO_VARIANT('2024-01-15'::DATE), TO_VARIANT('2024-01-16'::DATE))"));
        assertEquals(date, refusal("SELECT TIMESTAMPDIFF(day, TO_VARIANT('2024-01-15'::DATE), TO_VARIANT('2024-01-16'::DATE))"));
        assertEquals(date, refusal("SELECT TIMEDIFF(day, TO_VARIANT('2024-01-15'::DATE), TO_VARIANT('2024-01-16'::DATE))"));
        assertEquals(date, refusal("SELECT DATEDIFF(day, TO_VARIANT('2024-01-15'::DATE), '2024-01-16 10:00:00'::TIMESTAMP_NTZ)"));
        assertEquals("Failed to cast variant value \"2024-01-16\" to TIMESTAMP_NTZ",
            refusal("SELECT DATEDIFF(day, TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ), TO_VARIANT('2024-01-16'::DATE))"));
        assertEquals("Failed to cast variant value \"2024-01-15 10:00:00.000\" to DATE",
            refusal("SELECT DATEDIFF(day, TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ), '2024-01-16'::DATE)"));
        assertEquals(List.of("1", "-1", "0", "1", "null", "1"), row("""
            SELECT DATEDIFF(day, TO_VARIANT('2024-01-15'::DATE), '2024-01-16'::DATE),
                DATEDIFF(day, '2024-01-16'::DATE, TO_VARIANT('2024-01-15'::DATE)),
                DATEDIFF(hour, TO_VARIANT('2024-01-15 10:00:00 +02:00'::TIMESTAMP_TZ),
                    TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)),
                DATEDIFF(day, PARSE_JSON('1705312800'), PARSE_JSON('1705399200')),
                DATEDIFF(day, PARSE_JSON('null'), '2024-01-16'::DATE),
                DATEDIFF(day, PARSE_JSON('"2024-01-15"'), '2024-01-16'::DATE)"""));
    }
}
