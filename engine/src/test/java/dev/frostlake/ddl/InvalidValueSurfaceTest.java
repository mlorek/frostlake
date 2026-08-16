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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The invalid-VALUE refusal families across object kinds, measured cell by cell on a real
 * account. The bracketed parameter family ({@code invalid value [X] for parameter 'NAME'})
 * covers retention at every level and the session parameters — whose echo STRIPS string quotes,
 * unlike the format-option family, which keeps them. A second, single-quoted property family
 * ({@code invalid value 'X' for property 'NAME'}) owns the warehouse cluster floor and the
 * sequence increment. A task schedule of any malformed shape — a zero interval included — gets
 * one fixed sentence, and an extension time past 90 days its own.
 */
public class InvalidValueSurfaceTest extends BaseDatabaseTest {

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    @Test
    public void negativeRetentionRefusesAtEveryLevel() {
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter"
                + " 'DATA_RETENTION_TIME_IN_DAYS'",
            refusal("ALTER DATABASE test_db SET DATA_RETENTION_TIME_IN_DAYS = -1").getMessage());
        assertEquals("SQL compilation error:\ninvalid value [-3] for parameter"
                + " 'DATA_RETENTION_TIME_IN_DAYS'",
            refusal("ALTER SCHEMA test_schema SET DATA_RETENTION_TIME_IN_DAYS = -3").getMessage());
        engine.execute("CREATE TABLE ret_t (a INTEGER)");
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter"
                + " 'DATA_RETENTION_TIME_IN_DAYS'",
            refusal("ALTER TABLE ret_t SET DATA_RETENTION_TIME_IN_DAYS = -1").getMessage());
        assertEquals("SQL compilation error:\ninvalid value [-2] for parameter"
                + " 'DATA_RETENTION_TIME_IN_DAYS'",
            refusal("CREATE TABLE ret_t2 (a INTEGER) DATA_RETENTION_TIME_IN_DAYS = -2").getMessage());
    }

    @Test
    public void sessionParameterValuesEchoWithoutTheirQuotes() {
        assertEquals("SQL compilation error:\ninvalid value [Mars/Olympus] for parameter 'TIMEZONE'",
            refusal("ALTER SESSION SET TIMEZONE = 'Mars/Olympus'").getMessage());
        assertEquals("SQL compilation error:\ninvalid value [BOGUS] for parameter"
                + " 'TIMESTAMP_TYPE_MAPPING'",
            refusal("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'BOGUS'").getMessage());
        // Known values keep working.
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
    }

    @Test
    public void warehouseClusterFloorSpeaksThePropertyShape() {
        assertEquals("SQL compilation error:\ninvalid value '0' for property 'MIN_CLUSTER_COUNT'",
            refusal("CREATE WAREHOUSE val_wh0 WITH WAREHOUSE_SIZE = 'XSMALL' MIN_CLUSTER_COUNT = 0")
                .getMessage());
        // A negative AUTO_SUSPEND is accepted on a real account.
        engine.execute("CREATE WAREHOUSE val_wh1 WITH WAREHOUSE_SIZE = 'XSMALL' AUTO_SUSPEND = -5");
    }

    @Test
    public void sequenceIncrementZeroRefusesWhileNegativesRun() {
        assertEquals("SQL compilation error:\ninvalid value '0' for property 'SEQUENCE_INCREMENT'",
            refusal("CREATE SEQUENCE sq0 INCREMENT = 0").getMessage());
        engine.execute("CREATE SEQUENCE sq_neg START = 1 INCREMENT = -2");
    }

    @Test
    public void malformedTaskSchedulesGetTheOneSentence() {
        final String sentence = "Invalid schedule was specified. Please refer to the docs on what"
            + " constitutes a valid schedule.";
        assertEquals(sentence,
            refusal("CREATE TASK tk_bad SCHEDULE = 'NONSENSE' AS SELECT 1").getMessage());
        assertEquals(sentence,
            refusal("CREATE TASK tk_zero SCHEDULE = '0 MINUTE' AS SELECT 1").getMessage());
        engine.execute("CREATE TASK tk_ok SCHEDULE = '5 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK tk_cron SCHEDULE = 'USING CRON 0 9 * * * UTC' AS SELECT 1");
    }

    @Test
    public void extensionTimePastTheCapRefuses() {
        assertEquals("SQL compilation error:\nExceeds maximum allowable extension time (90).",
            refusal("CREATE TABLE ext_t (a INTEGER) MAX_DATA_EXTENSION_TIME_IN_DAYS = 100")
                .getMessage());
        engine.execute("CREATE TABLE ext_ok (a INTEGER) MAX_DATA_EXTENSION_TIME_IN_DAYS = 90");
    }

    @Test
    public void formatOptionControlsKeepTheirQuotedEcho() {
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter 'SKIP_HEADER'",
            refusal("CREATE FILE FORMAT ffv TYPE = CSV SKIP_HEADER = -1").getMessage());
        assertEquals("SQL compilation error:\ninvalid value ['BOGUS'] for parameter 'TYPE'",
            refusal("CREATE STAGE stv FILE_FORMAT = (TYPE = 'BOGUS')").getMessage());
    }
}
