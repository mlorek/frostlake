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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Snowflake-managed Iceberg tables: CREATE in its four shapes, SHOW ICEBERG TABLES, ALTER, DROP and UNDROP. */
public class IcebergTableStatementsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.executeQuery("CREATE OR REPLACE EXTERNAL VOLUME ice_vol STORAGE_LOCATIONS = ((NAME = 'l1'"
            + " STORAGE_PROVIDER = 'S3' STORAGE_BASE_URL = 's3://ice/' STORAGE_AWS_ROLE_ARN = 'arn:r'))");
    }

    @Override
    protected void teardownTest() {
        engine.executeQuery("DROP EXTERNAL VOLUME IF EXISTS ice_vol");
    }

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(refusal.getMessage().contains(fragment), refusal.getMessage());
    }

    @Test
    public void theFourCreateShapesMakeIcebergTables() {
        engine.executeQuery("CREATE ICEBERG TABLE ice1 (a INT, b VARCHAR) EXTERNAL_VOLUME = 'ice_vol'"
            + " CATALOG = 'SNOWFLAKE' BASE_LOCATION = 'ice1/' COMMENT = 'managed'");
        engine.executeQuery("INSERT INTO ice1 VALUES (1, 'x'), (2, 'y')");
        engine.executeQuery("CREATE ICEBERG TABLE ice2 CLONE ice1");
        engine.executeQuery("CREATE ICEBERG TABLE ice3 LIKE ice1");
        engine.executeQuery("CREATE ICEBERG TABLE ice4 EXTERNAL_VOLUME = 'ice_vol' AS SELECT a FROM ice1");
        assertEquals("[ICE1, ICE2, ICE3, ICE4]", column("SHOW ICEBERG TABLES LIKE 'ICE%'", "name").toString());
        assertEquals("[ICE_VOL, ICE_VOL, ICE_VOL, ICE_VOL]",
            column("SHOW ICEBERG TABLES LIKE 'ICE%'", "external_volume_name").toString());
        assertEquals("[SNOWFLAKE]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "catalog_name").toString());
        assertEquals("[MANAGED]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "iceberg_table_type").toString());
        assertEquals("[ice1/]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "base_location").toString());
        assertEquals("[managed]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "comment").toString());
        assertEquals("[2]", column("SELECT COUNT(*) AS n FROM ice2", "N").toString());
        assertEquals("[0]", column("SELECT COUNT(*) AS n FROM ice3", "N").toString());
        assertEquals("[2]", column("SELECT COUNT(*) AS n FROM ice4", "N").toString());
        assertEquals("[Y]", column("SHOW TABLES LIKE 'ICE1'", "is_iceberg").toString());
    }

    @Test
    public void anOrdinaryTableIsNoIcebergTable() {
        engine.executeQuery("CREATE TABLE plain_ice (a INT)");
        assertEquals("[]", column("SHOW ICEBERG TABLES LIKE 'PLAIN_ICE'", "name").toString());
        assertEquals("[N]", column("SHOW TABLES LIKE 'PLAIN_ICE'", "is_iceberg").toString());
        refused("DROP ICEBERG TABLE plain_ice", "not specified type 'ICEBERG_TABLE'");
        refused("ALTER ICEBERG TABLE plain_ice REFRESH", "The type of table PLAIN_ICE is NOT_ICEBERG.");
    }

    @Test
    public void createRefusals() {
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'nosuch'",
            "External volume 'NOSUCH' does not exist or not authorized.");
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'ice_vol' STORAGE_SERIALIZATION_POLICY = FAST",
            "for parameter 'STORAGE_SERIALIZATION_POLICY'");
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'no_catalog'",
            "Catalog integration NO_CATALOG either does not exist or is not of type CATALOG.");
    }

    @Test
    public void withoutAnExternalVolumeTheTableIsKeptInSnowflakeManagedStorage() {
        engine.executeQuery("CREATE ICEBERG TABLE ice_default (a INT)");
        assertEquals("[SNOWFLAKE_MANAGED]",
            column("SHOW ICEBERG TABLES LIKE 'ICE_DEFAULT'", "external_volume_name").toString());
        final String location = column("SHOW ICEBERG TABLES LIKE 'ICE_DEFAULT'", "base_location").get(0);
        assertTrue(location.matches("TEST_DB/TEST_SCHEMA/ICE_DEFAULT\\.[A-Za-z0-9]{8}/"), location);
    }

    @Test
    public void alterDropAndUndrop() {
        engine.executeQuery("CREATE ICEBERG TABLE ice5 (a INT) CLUSTER BY (a) EXTERNAL_VOLUME = 'ice_vol'");
        engine.executeQuery("ALTER ICEBERG TABLE ice5 SUSPEND RECLUSTER");
        assertEquals("[OFF]", column("SHOW TABLES LIKE 'ICE5'", "automatic_clustering").toString());
        engine.executeQuery("ALTER ICEBERG TABLE ice5 RESUME RECLUSTER");
        refused("ALTER ICEBERG TABLE ice5 REFRESH 'metadata/v1.metadata.json'",
            "to perform the command ICEBERG_TABLE_REFRESH. The type of table ICE5 is MANAGED.");
        refused("ALTER ICEBERG TABLE ice5 CONVERT TO MANAGED BASE_LOCATION = 'x'",
            "ICEBERG_TABLE_CONVERT_TO_MANAGED");
        engine.executeQuery("DROP ICEBERG TABLE ice5");
        assertEquals("[]", column("SHOW ICEBERG TABLES LIKE 'ICE5'", "name").toString());
        engine.executeQuery("UNDROP ICEBERG TABLE ice5");
        assertEquals("[ICE_VOL]", column("SHOW ICEBERG TABLES LIKE 'ICE5'", "external_volume_name").toString());
    }

    @Test
    public void snowflakeManagedStorageRules() {
        refused("CREATE ICEBERG TABLE ice_loc (a INT) BASE_LOCATION = 'mine/'",
            "BASE_LOCATION property is not supported for Iceberg tables using Snowflake Managed Storage.");
        refused("CREATE ICEBERG TABLE ice_ct (a INT) EXTERNAL_VOLUME = 'ice_vol' CHANGE_TRACKING = FALSE",
            "Change Tracking cannot be turned off for Iceberg tables");
        engine.executeQuery("CREATE CATALOG INTEGRATION ice_cat CATALOG_SOURCE = OBJECT_STORE TABLE_FORMAT = ICEBERG"
            + " ENABLED = TRUE");
        refused("CREATE ICEBERG TABLE ice_ext (a INT) CATALOG = 'ice_cat'", "Iceberg table ICE_EXT must have the"
            + " table parameter EXTERNAL_VOLUME defined on the table, schema, database, or account.");
        engine.executeQuery("DROP INTEGRATION ice_cat");
        engine.executeQuery("CREATE ICEBERG TABLE ice_src (a INT)");
        engine.executeQuery("CREATE ICEBERG TABLE ice_copy CLONE ice_src");
        assertEquals(column("SHOW ICEBERG TABLES LIKE 'ICE_SRC'", "base_location"),
            column("SHOW ICEBERG TABLES LIKE 'ICE_COPY'", "base_location"), "a clone keeps its source's files");
        assertEquals("[ON]", column("SHOW TABLES LIKE 'ICE_SRC'", "change_tracking").toString());
    }
}
