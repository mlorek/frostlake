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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temporal typed-value fidelity batch: LISTAGG renders timestamps in Snowflake's output text,
 * procedural temporal variables keep their temporal identity through bind substitution (they
 * re-enter SQL as {@code '…'::DATE} cast literals, not bare strings), and TRUNC over a temporal
 * behaves as DATE_TRUNC with swapped arguments.
 */
public class TemporalFidelityBatchTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void listaggRendersTimestampsInSnowflakeText() {
        engine.execute("CREATE TABLE tfb_ts (ts TIMESTAMP)");
        engine.execute("INSERT INTO tfb_ts VALUES ('2024-11-26 04:43:38.604'), ('2024-11-27 05:00:00')");
        final String agg = String.valueOf(scalar("SELECT LISTAGG(ts, '|') WITHIN GROUP (ORDER BY ts) FROM tfb_ts"));
        assertEquals("2024-11-26 04:43:38.604|2024-11-27 05:00:00.000", agg,
            "space separator and FF3, never java.time's T-form");
    }

    @Test
    public void proceduralTemporalVariablesKeepTheirType() {
        // The bind substitution inlines :x as a '…'::DATE cast literal, and TYPEOF's strict check
        // (which rejects any non-VARIANT argument, as Snowflake does) proves it: the rejection
        // names the TEMPORAL type — a bare quoted string would have been reported as (VARCHAR(…)).
        // The message carries the full parameterized type list, e.g. "(TIMESTAMP_NTZ(9))", so we
        // anchor on the type-name prefix only.
        assertRejectedWith("""
            BEGIN
                LET x := CURRENT_DATE();
                RETURN TYPEOF(:x);
            END""", "(DATE");
        assertRejectedWith("""
            BEGIN
                LET t := '2026-01-03 10:00:00'::TIMESTAMP;
                RETURN TYPEOF(:t);
            END""", "(TIMESTAMP_NTZ");
        // The positive path: DATEADD over the inlined variable stays in the temporal domain.
        final Object added = engine.executeQuery("""
            BEGIN
                LET x := '2026-01-03'::DATE;
                RETURN DATEADD('day', 1, :x);
            END""").getRows().get(0).getValue(0);
        assertEquals("2026-01-04", String.valueOf(added));
    }

    private void assertRejectedWith(final String sql, final String expectedTypeText) {
        try {
            engine.executeQuery(sql);
            org.junit.jupiter.api.Assertions.fail("expected strict rejection for: " + sql);
        } catch (final RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid argument types for function")
                    && e.getMessage().contains(expectedTypeText),
                "unexpected: " + e.getMessage());
        }
    }

    @Test
    public void truncOverTemporalsActsAsDateTrunc() {
        assertEquals("2026-03-01", String.valueOf(scalar("SELECT TRUNC('2026-03-15'::DATE, 'MONTH')")));
        assertTrue(String.valueOf(scalar("SELECT TRUNC('2026-03-15 10:20:30'::TIMESTAMP, 'HOUR')"))
            .startsWith("2026-03-15T10:00"), "hour truncation keeps the timestamp domain");
        assertEquals("3.14", String.valueOf(scalar("SELECT TRUNC(3.14159, 2)")),
            "the numeric overload is unchanged");
    }
}
