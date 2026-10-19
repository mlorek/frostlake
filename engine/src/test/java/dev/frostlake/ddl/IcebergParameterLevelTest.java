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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An Iceberg table takes the parameters of its own level alone, as any table does, and a refusal for want of access
 * names it by its kind: {@code Insufficient privileges to operate on iceberg_table 'NF_ICE'.} for a parameter a session
 * keeps, set or unset (live-verified; an Iceberg table needs an external volume, which the shared test account does not
 * let a test create, so the cells run on a private engine).
 */
public class IcebergParameterLevelTest {

    private static final String REFUSED = "SQL access control error:\n"
        + "Insufficient privileges to operate on iceberg_table 'NF_ICE'.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ipl_db");
        engine.execute("USE SCHEMA ipl_db.PUBLIC");
        engine.execute("CREATE EXTERNAL VOLUME ice_vol STORAGE_LOCATIONS = ((NAME = 'l1' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://ice/' STORAGE_AWS_ROLE_ARN = 'arn:r'))");
        engine.execute("CREATE ICEBERG TABLE nf_ice (a INT) EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'SNOWFLAKE'"
            + " BASE_LOCATION = 'nf_ice/'");
        engine.execute("CREATE TABLE nf_p (a INT)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String answer(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void anotherLevelsParameterIsRefusedByTheTablesKind() {
        assertEquals(REFUSED, answer("ALTER ICEBERG TABLE nf_ice SET TIMEZONE = 'UTC'"));
        assertEquals(REFUSED, answer("ALTER ICEBERG TABLE nf_ice UNSET ICEBERG_TIMESTAMP_DEFAULT_PRECISION"));
        assertEquals("SQL access control error:\nInsufficient privileges to operate on table 'NF_P'.",
            answer("ALTER TABLE nf_p SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 9"));
    }
}
