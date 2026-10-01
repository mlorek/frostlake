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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.ResultSetData;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A DATE, a TIME or a timestamp held in a VARIANT reaches a client as a JSON string of its variant text:
 * a DATE with its year unsigned past 9999 and signed before year one, a TIME to the second, a timestamp to
 * the millisecond with its offset where it has one. The in-process driver and the HTTP wire agree.
 * Live-verified.
 */
public class VariantTemporalClientTextTest extends BaseJdbcTest {

    /** Every cell of the first row through getString, a comma between cells. */
    private String firstRow(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            final StringBuilder out = new StringBuilder();
            if (rs.next()) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    if (i > 1) {
                        out.append(", ");
                    }
                    out.append(rs.getString(i));
                }
            }
            return out.toString();
        }
    }

    @Test
    public void aTemporalInAVariantIsItsQuotedText() throws SQLException {
        assertEquals("\"2024-01-15\"", firstRow("SELECT TO_VARIANT('2024-01-15'::DATE)"));
        assertEquals("\"10:00:00\"", firstRow("SELECT TO_VARIANT('10:00:00'::TIME)"));
        assertEquals("\"2024-01-15 10:00:00.000\"", firstRow("SELECT TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ)"));
        assertEquals("\"2024-01-15 10:00:00.000 +0200\"",
            firstRow("SELECT TO_VARIANT('2024-01-15 10:00:00 +0200'::TIMESTAMP_TZ)"));
        assertEquals("\"2024-01-15\"", firstRow("SELECT OBJECT_CONSTRUCT('d', '2024-01-15'::DATE):d"));
    }

    @Test
    public void theYearIsTheVariantsYear() throws SQLException {
        assertEquals("\"20201-01-15\"", firstRow("SELECT TO_VARIANT('20201-01-15'::DATE)"));
        assertEquals("\"10000-01-01\"", firstRow("SELECT TO_VARIANT('10000-01-01'::DATE)"));
        assertEquals("\"9999-12-31\"", firstRow("SELECT TO_VARIANT('9999-12-31'::DATE)"));
        assertEquals("\"0001-01-01\"", firstRow("SELECT TO_VARIANT('0001-01-01'::DATE)"));
        assertEquals("\"-1-01-01\"", firstRow("SELECT TO_VARIANT(DATEADD(year, -2025, '2024-01-01'::DATE))"));
        assertEquals("\"20201-01-15 10:00:00.000\"", firstRow("SELECT TO_VARIANT('20201-01-15 10:00:00'::TIMESTAMP_NTZ)"));
    }

    /** The HTTP wire carries the same text. */
    @Test
    public void theHttpWireCarriesTheSameText() {
        assumeFalse(LiveSnowflake.enabled(), "the engine's own wire has no live counterpart");
        final ResultSetData data = ResultSetData.from(sharedEngine.executeQuery(
            "SELECT TO_VARIANT('2024-01-15'::DATE), TO_VARIANT('10:00:00'::TIME), TO_VARIANT('20201-01-15'::DATE)"));
        assertEquals("\"2024-01-15\"", data.getRows().get(0).get(0));
        assertEquals("\"10:00:00\"", data.getRows().get(0).get(1));
        assertEquals("\"20201-01-15\"", data.getRows().get(0).get(2));
    }
}
