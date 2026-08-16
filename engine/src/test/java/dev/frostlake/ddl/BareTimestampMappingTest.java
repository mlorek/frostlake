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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the BARE word {@code TIMESTAMP} means. The session's TIMESTAMP_TYPE_MAPPING names it, and every
 * spelling that can carry the bare word follows: {@code CAST(x AS TIMESTAMP)}, {@code x::TIMESTAMP},
 * {@code TO_TIMESTAMP(x)}, a declared column, and a routine's parameter and return.
 *
 * <p>★ DATETIME DOES NOT FOLLOW IT. It reads as an alias for the mapped TIMESTAMP and is not one — it
 * is an alias for TIMESTAMP_NTZ specifically, and stays NTZ under every mapping on both engines. The
 * three explicit spellings are equally untouched. Both are kept here as controls, because getting the
 * bare word right by making every timestamp word follow the mapping would pass a thinner test.
 *
 * <p>★ AND IT IS RESOLVED ONCE, WHEN THE WORD IS WRITTEN. A column created under TIMESTAMP_LTZ still
 * describes as TIMESTAMP_LTZ after the session switches back, so the mapping is never consulted again
 * on read.
 *
 * <p>The declared type is read through a CTAS and DESCRIBE rather than from a query's result columns,
 * because the live harness reports every result column as VARCHAR and could not tell these apart.
 */
public class BareTimestampMappingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        // Reset the mapping EXPLICITLY, never relying on it being unset. These tests each change it,
        // and a live run shares ONE session across the whole class — so without this the method that
        // asserts the default behaviour inherits whatever the previous method left set. The embedded
        // run hides it, because there each test gets a fresh engine.
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_NTZ'");
        engine.execute("CREATE OR REPLACE TABLE mapsrc (s VARCHAR)");
        engine.execute("INSERT INTO mapsrc VALUES ('2020-01-01 10:00:00')");
    }

    /** The declared type of a one-column CTAS over {@code expr}. */
    private String declared(final String expr) {
        engine.execute("CREATE OR REPLACE TABLE map_t AS SELECT " + expr + " AS c FROM mapsrc");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE map_t");
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no rows>";
    }

    /** The declared type of a column written as {@code typeText}. */
    private String column(final String typeText) {
        engine.execute("CREATE OR REPLACE TABLE map_c (c " + typeText + ")");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE map_c");
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no rows>";
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Under the default mapping every spelling is TIMESTAMP_NTZ. */
    @Test
    public void theDefaultMappingIsNtzEverywhere() {
        assertEquals("TIMESTAMP_NTZ(9)", declared("CAST(s AS TIMESTAMP)"));
        assertEquals("TIMESTAMP_NTZ(9)", declared("s::TIMESTAMP"));
        assertEquals("TIMESTAMP_NTZ(9)", declared("TO_TIMESTAMP(s)"));
        assertEquals("TIMESTAMP_NTZ(9)", declared("CAST(s AS DATETIME)"));
        assertEquals("TIMESTAMP_NTZ(9)", column("TIMESTAMP"));
        assertEquals("TIMESTAMP_NTZ(9)", column("DATETIME"));
    }

    /** Under TIMESTAMP_LTZ the bare word means LTZ, on every spelling that carries it. */
    @Test
    public void theBareWordFollowsTheLtzMapping() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals("TIMESTAMP_LTZ(9)", declared("CAST(s AS TIMESTAMP)"));
        assertEquals("TIMESTAMP_LTZ(9)", declared("s::TIMESTAMP"));
        assertEquals("TIMESTAMP_LTZ(9)", declared("TO_TIMESTAMP(s)"));
        assertEquals("TIMESTAMP_LTZ(9)", column("TIMESTAMP"));
    }

    /** Under TIMESTAMP_TZ it means TZ — so the mapping is read, not merely switched on. */
    @Test
    public void theBareWordFollowsTheTzMappingToo() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_TZ'");
        assertEquals("TIMESTAMP_TZ(9)", declared("CAST(s AS TIMESTAMP)"));
        assertEquals("TIMESTAMP_TZ(9)", declared("s::TIMESTAMP"));
        assertEquals("TIMESTAMP_TZ(9)", declared("TO_TIMESTAMP(s)"));
        assertEquals("TIMESTAMP_TZ(9)", column("TIMESTAMP"));
    }

    /** ★ DATETIME and the explicit spellings do NOT follow it. */
    @Test
    public void datetimeAndTheExplicitSpellingsDoNotFollowIt() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals("TIMESTAMP_NTZ(9)", declared("CAST(s AS DATETIME)"),
            "DATETIME is an alias for TIMESTAMP_NTZ, not for the mapped TIMESTAMP");
        assertEquals("TIMESTAMP_NTZ(9)", column("DATETIME"));
        assertEquals("TIMESTAMP_NTZ(9)", declared("CAST(s AS TIMESTAMP_NTZ)"));
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_TZ'");
        assertEquals("TIMESTAMP_NTZ(9)", declared("CAST(s AS DATETIME)"));
    }

    /** The VALUE follows the type, or the column would lie about what it holds. */
    @Test
    public void theValueFollowsTheTypeItResolvedTo() {
        assertEquals("2020-01-01 10:00:00.000",
            answer("SELECT TO_VARCHAR(CAST(s AS TIMESTAMP)) FROM mapsrc"));
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(CAST(s AS TIMESTAMP)) FROM mapsrc"),
            "an LTZ carries the session's offset");
        assertEquals("2020-01-01 10:00:00.000",
            answer("SELECT TO_VARCHAR(CAST(s AS DATETIME)) FROM mapsrc"),
            "while the DATETIME beside it stays naive");
    }

    /** ★ A column resolves ONCE, when it is created, and keeps that flavour. */
    @Test
    public void aColumnResolvesOnceAndKeepsIt() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_NTZ'");
        engine.execute("CREATE OR REPLACE TABLE map_pinned (c TIMESTAMP)");
        assertEquals("TIMESTAMP_NTZ(9)", firstType("DESCRIBE TABLE map_pinned"));
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals("TIMESTAMP_NTZ(9)", firstType("DESCRIBE TABLE map_pinned"),
            "the switch does not reach back into a column that is already resolved");
    }

    /** The switch is reversible. */
    @Test
    public void theSwitchIsReversible() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals("TIMESTAMP_LTZ(9)", declared("CAST(s AS TIMESTAMP)"));
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_NTZ'");
        assertEquals("TIMESTAMP_NTZ(9)", declared("CAST(s AS TIMESTAMP)"));
    }

    /** A routine's parameter and return follow it as well. */
    @Test
    public void aRoutineParameterAndReturnFollowIt() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        engine.execute("CREATE OR REPLACE FUNCTION f_map_ts(x TIMESTAMP) RETURNS TIMESTAMP"
            + " AS $$ x $$");
        assertEquals("(X TIMESTAMP_LTZ)", secondColumn("DESCRIBE FUNCTION f_map_ts(TIMESTAMP)"));
        assertEquals("2020-01-01 10:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(f_map_ts('2020-01-01 10:00:00'::TIMESTAMP))"),
            "and the value comes back through the body with its offset intact");
    }

    /** A mapping the parameter does not know is refused. */
    @Test
    public void anUnknownMappingIsRefused() {
        assertEquals("SQL compilation error:|invalid value [NOT_A_TYPE] for parameter"
            + " 'TIMESTAMP_TYPE_MAPPING'",
            answer("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'NOT_A_TYPE'"));
    }

    private String firstType(final String describeSql) {
        final ResultSet rs = engine.executeQuery(describeSql);
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no rows>";
    }

    private String secondColumn(final String describeSql) {
        final ResultSet rs = engine.executeQuery(describeSql);
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no rows>";
    }
}
