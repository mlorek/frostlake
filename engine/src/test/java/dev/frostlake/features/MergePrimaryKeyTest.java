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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MergePrimaryKeyTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA IF NOT EXISTS base_transform");
        engine.execute("USE SCHEMA base_transform");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testMergeIntoTableWithPrimaryKeyConstraint() {
        engine.execute(
            "CREATE OR REPLACE TABLE base_transform.config_remediation_maturity_severity_bound(" +
            "    severity_level   NUMBER(38,0) NOT NULL," +
            "    days_lower_bound NUMBER(38,0)," +
            "    days_upper_bound NUMBER(38,0)," +
            "    CONSTRAINT pk_config PRIMARY KEY (severity_level) RELY" +
            ")"
        );

        assertDoesNotThrow(() -> engine.execute(
            "MERGE INTO base_transform.config_remediation_maturity_severity_bound AS t " +
            "USING (" +
            "    SELECT $1, $2, $3 FROM VALUES" +
            "        (1, 1, 181), (2, 30, 92), (3, 7, 62), (4, 3, 32)" +
            ") AS s (severity_level, days_lower_bound, days_upper_bound) " +
            "ON s.severity_level = t.severity_level " +
            "WHEN MATCHED AND (s.days_lower_bound != t.days_lower_bound OR s.days_upper_bound != t.days_upper_bound) THEN UPDATE " +
            "    SET days_lower_bound = s.days_lower_bound, days_upper_bound = s.days_upper_bound " +
            "WHEN NOT MATCHED THEN INSERT (severity_level, days_lower_bound, days_upper_bound) " +
            "    VALUES (s.severity_level, s.days_lower_bound, s.days_upper_bound)"
        ), "First MERGE should insert 4 rows without duplicate key error");

        ResultSet rs = engine.executeQuery(
            "SELECT COUNT(*) FROM base_transform.config_remediation_maturity_severity_bound");
        assertEquals(4L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "Table should have 4 rows after first MERGE");

        // Second MERGE — no changes, should not throw
        assertDoesNotThrow(() -> engine.execute(
            "MERGE INTO base_transform.config_remediation_maturity_severity_bound AS t " +
            "USING (" +
            "    SELECT $1, $2, $3 FROM VALUES" +
            "        (1, 1, 181), (2, 30, 92), (3, 7, 62), (4, 3, 32)" +
            ") AS s (severity_level, days_lower_bound, days_upper_bound) " +
            "ON s.severity_level = t.severity_level " +
            "WHEN MATCHED AND (s.days_lower_bound != t.days_lower_bound OR s.days_upper_bound != t.days_upper_bound) THEN UPDATE " +
            "    SET days_lower_bound = s.days_lower_bound, days_upper_bound = s.days_upper_bound " +
            "WHEN NOT MATCHED THEN INSERT (severity_level, days_lower_bound, days_upper_bound) " +
            "    VALUES (s.severity_level, s.days_lower_bound, s.days_upper_bound)"
        ), "Second MERGE with same data should not throw or insert duplicates");

        ResultSet rs2 = engine.executeQuery(
            "SELECT COUNT(*) FROM base_transform.config_remediation_maturity_severity_bound");
        assertEquals(4L, ((Number) rs2.getRows().get(0).getValue(0)).longValue(),
            "Table should still have 4 rows after second MERGE (no changes)");
    }
}
