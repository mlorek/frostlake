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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a listing's {@code LIMIT n FROM 'name'} starts (live-verified, listing by listing): tables, views and
 * schemas after the name; tasks at it; streams, tags and alerts at the name's own row and on through the listing —
 * which shows when a listing spans schemas, whose rows come schema by schema; and masking, row access and password
 * policies read the name and keep every row.
 */
public class ShowLimitFromStartTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE SCHEMA pc_aa");
        engine.execute("CREATE SCHEMA pc_zz");
        engine.execute("USE SCHEMA test_db.test_schema");
        for (final String name : new String[] {"pc_aa.pc_x2", "pc_zz.pc_x1", "pc_zz.pc_x3"}) {
            engine.execute("CREATE TABLE " + name + "t (a INT)");
            engine.execute("CREATE VIEW " + name + "v AS SELECT 1 AS a");
            engine.execute("CREATE TASK " + name + "k AS SELECT 1");
            engine.execute("CREATE STREAM " + name + "s ON TABLE " + name + "t");
            engine.execute("CREATE TAG " + name + "g");
        }
    }

    /** The rows a listing answers, each as SCHEMA.NAME. */
    private List<String> listed(final String show) {
        final ResultSet rs = engine.executeQuery(show + " ->> SELECT \"schema_name\" || '.' || \"name\" FROM $1");
        final List<String> names = new ArrayList<>();
        while (rs.next()) {
            names.add(String.valueOf(rs.getValue(0)));
        }
        return names;
    }

    private List<String> across(final String kind, final String from) {
        return listed("SHOW " + kind + " LIKE 'PC_X%' IN DATABASE test_db LIMIT 10 FROM '" + from + "'");
    }

    @Test
    public void tablesAndViewsStartAfterTheName() {
        assertEquals(Arrays.asList("PC_AA.PC_X2T", "PC_ZZ.PC_X3T"), across("TABLES", "PC_X1T"));
        assertEquals(Arrays.asList("PC_ZZ.PC_X3T"), across("TABLES", "PC_X2T"));
        assertEquals(Arrays.asList("PC_AA.PC_X2V", "PC_ZZ.PC_X3V"), across("VIEWS", "PC_X1V"));
        assertEquals(Arrays.asList("PC_ZZ"), listedSchemas("PC_AA"));
    }

    private List<String> listedSchemas(final String from) {
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS LIKE 'PC_%' IN DATABASE test_db LIMIT 10 FROM '" + from
            + "' ->> SELECT \"name\" FROM $1");
        final List<String> names = new ArrayList<>();
        while (rs.next()) {
            names.add(String.valueOf(rs.getValue(0)));
        }
        return names;
    }

    @Test
    public void tasksStartAtTheName() {
        assertEquals(Arrays.asList("PC_AA.PC_X2K", "PC_ZZ.PC_X3K"), across("TASKS", "PC_X2K"),
            "the tasks named at or after it, whichever schema lists them");
        assertEquals(Arrays.asList("PC_ZZ.PC_X3K"), across("TASKS", "PC_X3K"));
        assertEquals(Arrays.asList("PC_ZZ.PC_X3K"), across("TASKS", "PC_X2Z"));
    }

    @Test
    public void streamsAndTagsStartAtTheNamesRowAndRunOn() {
        assertEquals(Arrays.asList("PC_AA.PC_X2S", "PC_ZZ.PC_X1S", "PC_ZZ.PC_X3S"), across("STREAMS", "PC_X2S"),
            "the listing from the first row named at or after it, the rows after that one whatever their name");
        assertEquals(Arrays.asList("PC_AA.PC_X2G", "PC_ZZ.PC_X1G", "PC_ZZ.PC_X3G"), across("TAGS", "PC_X2G"));
    }

    /** The three policy listings read the name and start nothing: every row is kept, up to the limit. */
    @Test
    public void policiesReadTheNameAndKeepEveryRow() {
        engine.execute("CREATE MASKING POLICY lf_ma AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE MASKING POLICY lf_mb AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE MASKING POLICY pc_aa.lf_mc AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE ROW ACCESS POLICY lf_ra AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE ROW ACCESS POLICY lf_rb AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE PASSWORD POLICY lf_pa");
        engine.execute("CREATE PASSWORD POLICY lf_pb");
        final String in = " IN SCHEMA test_db.test_schema LIMIT 5 FROM ";
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_MA", "TEST_SCHEMA.LF_MB"),
            listed("SHOW MASKING POLICIES" + in + "'LF_MB'"));
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_RA", "TEST_SCHEMA.LF_RB"),
            listed("SHOW ROW ACCESS POLICIES" + in + "'LF_RB'"));
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_PA", "TEST_SCHEMA.LF_PB"),
            listed("SHOW PASSWORD POLICIES" + in + "'LF_PB'"));
        assertEquals(Arrays.asList("PC_AA.LF_MC", "TEST_SCHEMA.LF_MA", "TEST_SCHEMA.LF_MB"),
            listed("SHOW MASKING POLICIES LIKE 'LF_M%' IN DATABASE test_db LIMIT 10 FROM 'LF_MB'"));
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_PA"),
            listed("SHOW PASSWORD POLICIES IN SCHEMA test_db.test_schema LIMIT 1"));
    }

    @Test
    public void aPageFromTheFirstNameKeepsEveryRow() {
        engine.execute("CREATE ALERT lf_aa SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("CREATE ALERT lf_ab SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("CREATE MASKING POLICY lf_ma AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE MASKING POLICY lf_mb AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE ROW ACCESS POLICY lf_ra AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE ROW ACCESS POLICY lf_rb AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        final String in = " IN SCHEMA test_db.test_schema LIMIT 5 FROM ";
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_AA", "TEST_SCHEMA.LF_AB"), listed("SHOW ALERTS" + in + "'LF_AA'"));
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_MA", "TEST_SCHEMA.LF_MB"),
            listed("SHOW MASKING POLICIES" + in + "'LF_MA'"));
        assertEquals(Arrays.asList("TEST_SCHEMA.LF_RA", "TEST_SCHEMA.LF_RB"),
            listed("SHOW ROW ACCESS POLICIES" + in + "'LF_RA'"));
    }
}
