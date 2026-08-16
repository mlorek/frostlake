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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES — the readback for metric attachments.
 *
 * <p>Two of live's twenty cells cannot be reproduced without inventing account state: REF_ID is a
 * per-attachment UUID, and live's REF_ARGUMENTS embeds an internal COLUMN ID. Frostlake answers null
 * for the first and omits the id field from the second, so those two are deliberately not asserted
 * here; every other cell is.
 */
public class DataMetricReferencesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE dr_t (id NUMBER, txt VARCHAR, d DATE)");
        engine.execute("ALTER TABLE dr_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
        engine.execute("ALTER TABLE dr_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.ROW_COUNT ON ()");
    }

    private ResultSet references(final String columns) {
        return engine.executeQuery("SELECT " + columns + " FROM TABLE("
            + "INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES("
            + "REF_ENTITY_NAME => 'DR_T', REF_ENTITY_DOMAIN => 'TABLE'))");
    }

    private String cellsOf(final ResultSet rs, final Row row) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            out.append(row.getValue(i)).append('|');
        }
        return out.toString();
    }

    @Test
    public void aColumnMetricNamesTheMetricAndItsOverload() {
        final ResultSet rs = references("METRIC_DATABASE_NAME, METRIC_SCHEMA_NAME, METRIC_NAME,"
            + " METRIC_SIGNATURE, METRIC_DATA_TYPE");
        assertEquals("SNOWFLAKE|CORE|NULL_COUNT|TABLE(NUMBER)|NUMBER(38,0)|",
            cellsOf(rs, rs.getRows().get(0)));
    }

    /** A table-level metric has an EMPTY signature. */
    @Test
    public void aTableMetricHasNoSignature() {
        final ResultSet rs = references("METRIC_NAME, METRIC_SIGNATURE");
        assertEquals("ROW_COUNT||", cellsOf(rs, rs.getRows().get(1)));
    }

    /** The string overload carries its length where the others are bare. */
    @Test
    public void theStringOverloadCarriesItsLength() {
        engine.execute("ALTER TABLE dr_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (txt)");
        final ResultSet rs = references("METRIC_NAME, METRIC_SIGNATURE");
        assertEquals("NULL_COUNT|TABLE(VARCHAR(16777216))|", cellsOf(rs, rs.getRows().get(1)));
    }

    /** The entity trio, with the domain spelled in mixed case. */
    @Test
    public void theEntityIsNamedInMixedCaseDomain() {
        final ResultSet rs = references("REF_ENTITY_DATABASE_NAME, REF_ENTITY_SCHEMA_NAME,"
            + " REF_ENTITY_NAME, REF_ENTITY_DOMAIN, LEVEL");
        assertEquals("TEST_DB|TEST_SCHEMA|DR_T|Table|Table|", cellsOf(rs, rs.getRows().get(0)));
    }

    @Test
    public void theFixedStatusCellsAreAllDisabled() {
        final ResultSet rs = references("DATA_QUALITY_NOTIFICATION_STATUS, ANOMALY_DETECTION_STATUS,"
            + " ANOMALY_DETECTION_SENSITIVITY_LEVEL, USE_ROLE, EXCLUDE_TABLE_TYPES");
        assertEquals("DISABLED|DISABLED|MEDIUM|||", cellsOf(rs, rs.getRows().get(0)));
    }

    /** DATA_METRIC_SCHEDULE reads back as cron: below an hour by minutes, above it by hours. */
    @Test
    public void theScheduleIsRenderedAsCron() {
        engine.execute("ALTER TABLE dr_t SET DATA_METRIC_SCHEDULE = '60 MINUTE'");
        assertEquals("0 */1 * * * UTC", references("SCHEDULE").getRows().get(0).getValue(0).toString());
        engine.execute("ALTER TABLE dr_t SET DATA_METRIC_SCHEDULE = '30 MINUTE'");
        assertEquals("*/30 * * * * UTC", references("SCHEDULE").getRows().get(0).getValue(0).toString());
    }

    /** SUSPEND moves ONE attachment's status, not the table's. */
    @Test
    public void suspendingOneAttachmentMovesOnlyItsStatus() {
        engine.execute("ALTER TABLE dr_t MODIFY DATA METRIC FUNCTION"
            + " SNOWFLAKE.CORE.NULL_COUNT ON (id) SUSPEND");
        final ResultSet rs = references("METRIC_NAME, SCHEDULE_STATUS");
        assertEquals("NULL_COUNT|SUSPENDED_BY_USER_ACTION|", cellsOf(rs, rs.getRows().get(0)));
        assertEquals("ROW_COUNT|STARTED|", cellsOf(rs, rs.getRows().get(1)));
    }

    /** The other call shape: one metric, every object it is attached to — named in FULL. */
    @Test
    public void aMetricListsTheObjectsItMeasures() {
        final ResultSet rs = engine.executeQuery("SELECT REF_ENTITY_NAME FROM TABLE("
            + "INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES("
            + "METRIC_NAME => 'SNOWFLAKE.CORE.NULL_COUNT'))");
        assertEquals(1, rs.getRows().size());
        assertEquals("DR_T", rs.getRows().get(0).getValue(0).toString());
    }

    /** A bare metric name resolves to nothing here, just as it does in the ALTER form. */
    @Test
    public void aBareMetricNameIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE("
                    + "INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'NULL_COUNT'))");
            }
        });
        assertEquals("SQL compilation error:\nData Metric Function 'NULL_COUNT'"
            + " does not exist or not authorized.", ex.getMessage());
    }

    @Test
    public void anObjectThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE("
                    + "INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES("
                    + "REF_ENTITY_NAME => 'NO_SUCH_T', REF_ENTITY_DOMAIN => 'TABLE'))");
            }
        });
        assertEquals("SQL compilation error:\nTable 'NO_SUCH_T' does not exist or not authorized.",
            ex.getMessage());
    }

    @Test
    public void oneOfTheTwoArgumentShapesIsRequired() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE("
                    + "INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES())");
            }
        });
        assertEquals("SQL compilation error: function"
            + " 'information_schema.data_metric_function_references' expects argument"
            + " (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            ex.getMessage());
    }
}
