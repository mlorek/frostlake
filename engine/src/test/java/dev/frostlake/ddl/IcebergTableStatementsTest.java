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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake-managed Iceberg tables: CREATE in its four shapes, what it judges and in which order, SHOW ICEBERG TABLES,
 * ALTER, DROP and UNDROP. The account writes a test file to a table's external volume before it makes the table, so a
 * statement that gets that far on an invented volume is left to the embedded engine.
 */
public class IcebergTableStatementsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.executeQuery("CREATE OR REPLACE EXTERNAL VOLUME ice_vol STORAGE_LOCATIONS = ((NAME = 'l1'"
            + " STORAGE_PROVIDER = 'S3' STORAGE_BASE_URL = 's3://ice/'"
            + " STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'))");
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

    /** The cell of a CATALOG parameter's level and value, as {@code level|value}. */
    private String catalogParameter() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE 'CATALOG' IN SCHEMA");
        final Row row = rs.getRows().get(0);
        return row.getValue(rs.getColumnIndex("level")) + "|" + row.getValue(rs.getColumnIndex("value"));
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
        engine.executeQuery("CREATE ICEBERG TABLE ice1 (a INT, b VARCHAR) CATALOG = 'SNOWFLAKE' COMMENT = 'managed'");
        engine.executeQuery("INSERT INTO ice1 VALUES (1, 'x'), (2, 'y')");
        engine.executeQuery("CREATE ICEBERG TABLE ice2 CLONE ice1");
        engine.executeQuery("CREATE ICEBERG TABLE ice3 LIKE ice1");
        engine.executeQuery("CREATE ICEBERG TABLE ice4 CATALOG = 'SNOWFLAKE' AS SELECT a FROM ice1");
        assertEquals("[ICE1, ICE2, ICE3, ICE4]", column("SHOW ICEBERG TABLES LIKE 'ICE%'", "name").toString());
        assertEquals("[SNOWFLAKE_MANAGED, SNOWFLAKE_MANAGED, SNOWFLAKE_MANAGED, SNOWFLAKE_MANAGED]",
            column("SHOW ICEBERG TABLES LIKE 'ICE%'", "external_volume_name").toString());
        assertEquals("[SNOWFLAKE]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "catalog_name").toString());
        assertEquals("[MANAGED]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "iceberg_table_type").toString());
        assertEquals("[managed]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "comment").toString());
        assertEquals("[2]", column("SELECT COUNT(*) AS n FROM ice2", "N").toString());
        assertEquals("[0]", column("SELECT COUNT(*) AS n FROM ice3", "N").toString());
        assertEquals("[2]", column("SELECT COUNT(*) AS n FROM ice4", "N").toString());
        assertEquals("[Y]", column("SHOW TABLES LIKE 'ICE1'", "is_iceberg").toString());
    }

    @Test
    public void aTableOnAVolumeKeepsItsVolumeAndItsLocation() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account writes to the volume, which is invented here");
        engine.executeQuery("CREATE ICEBERG TABLE ice1 (a INT, b VARCHAR) EXTERNAL_VOLUME = 'ice_vol'"
            + " CATALOG = 'SNOWFLAKE' BASE_LOCATION = 'ice1/' COMMENT = 'on a volume'");
        engine.executeQuery("INSERT INTO ice1 VALUES (1, 'x'), (2, 'y')");
        engine.executeQuery("CREATE ICEBERG TABLE ice2 CLONE ice1");
        engine.executeQuery("CREATE ICEBERG TABLE ice3 LIKE ice1");
        engine.executeQuery("CREATE ICEBERG TABLE ice4 EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'SNOWFLAKE'"
            + " AS SELECT a FROM ice1");
        assertEquals("[ICE_VOL, ICE_VOL, ICE_VOL, ICE_VOL]",
            column("SHOW ICEBERG TABLES LIKE 'ICE%'", "external_volume_name").toString());
        assertEquals("[ice1/]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "base_location").toString());
        assertEquals("[2]", column("SELECT COUNT(*) AS n FROM ice2", "N").toString());
        assertEquals("[on a volume]", column("SHOW ICEBERG TABLES LIKE 'ICE1'", "comment").toString());
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
    public void aTableOnAVolumeNamesACatalog() {
        final String volume = "CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'ice_vol'";
        final String none = "SQL compilation error: Iceberg tables require a catalog integration to be specified";
        refused(volume, none);
        refused(volume + " CATALOG = ''", none);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = ice_vol", none);
        refused(volume + " BASE_LOCATION = 'x/'", none);
        refused(volume + " STORAGE_SERIALIZATION_POLICY = FAST", none);
        refused(volume + " CHANGE_TRACKING = FALSE", none);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) CLUSTER BY (a) EXTERNAL_VOLUME = 'ice_vol'", none);
        refused("CREATE ICEBERG TABLE ice_bad EXTERNAL_VOLUME = 'ice_vol' AS SELECT 1 AS a", none);
        // A name that is none is refused as it is written, and only once the volume is found.
        final String noCatalog = " either does not exist or is not of type CATALOG."
            + " Please verify that catalog integration is valid.";
        refused(volume + " CATALOG = 'no_catalog'", "Catalog integration no_catalog" + noCatalog);
        refused(volume + " CATALOG = 'No_Catalog'", "Catalog integration No_Catalog" + noCatalog);
        refused(volume + " CATALOG = no_catalog", "Catalog integration NO_CATALOG" + noCatalog);
        refused(volume + " CATALOG = \"no_catalog\"", "Catalog integration no_catalog" + noCatalog);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'nosuch' CATALOG = 'no_catalog'",
            "External volume 'NOSUCH' does not exist or not authorized.");
    }

    @Test
    public void createRefusalsComeInTheirOrder() {
        final String noVolume = "External volume 'NOSUCH' does not exist or not authorized.";
        final String policy = "SQL Compilation error:\nInvalid value for storage serialization policy."
            + " Supported are COMPATIBLE,OPTIMIZED.";
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'nosuch'", noVolume);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'nosuch' STORAGE_SERIALIZATION_POLICY = FAST",
            noVolume);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'SNOWFLAKE'"
            + " STORAGE_SERIALIZATION_POLICY = FAST", policy);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'SNOWFLAKE'"
            + " STORAGE_SERIALIZATION_POLICY = FAST CHANGE_TRACKING = FALSE", policy);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) STORAGE_SERIALIZATION_POLICY = FAST", policy);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) STORAGE_SERIALIZATION_POLICY = ''", policy);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) BASE_LOCATION = 'x/' STORAGE_SERIALIZATION_POLICY = FAST", policy);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) STORAGE_SERIALIZATION_POLICY = FAST CHANGE_TRACKING = FALSE",
            policy);
        refused("CREATE ICEBERG TABLE ice_bad (a INT) CATALOG = 'no_catalog' STORAGE_SERIALIZATION_POLICY = FAST",
            "Iceberg table ICE_BAD must have the table parameter EXTERNAL_VOLUME");
        refused("CREATE ICEBERG TABLE ice_bad (a INT) STORAGE_SERIALIZATION_POLICY = 1",
            "invalid value [1] for parameter 'STORAGE_SERIALIZATION_POLICY'");
        refused("CREATE ICEBERG TABLE ice_bad (a INT) BASE_LOCATION = 'x/' CHANGE_TRACKING = FALSE",
            "BASE_LOCATION property is not supported for Iceberg tables using Snowflake Managed Storage.");
        final String managed = "SQL Compilation Error: Iceberg tables using Snowflake managed storage must specify"
            + " 'SNOWFLAKE' as the catalog.";
        refused("CREATE ICEBERG TABLE ice_bad (a INT) EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' CATALOG = 'no_catalog'",
            managed);
        // The empty text names no volume, whatever else the statement says.
        engine.executeQuery("CREATE ICEBERG TABLE ice_e1 (a INT) EXTERNAL_VOLUME = ''");
        engine.executeQuery("CREATE ICEBERG TABLE ice_e2 (a INT) EXTERNAL_VOLUME = '' CATALOG = 'SNOWFLAKE'");
        assertEquals("[SNOWFLAKE_MANAGED, SNOWFLAKE_MANAGED]",
            column("SHOW ICEBERG TABLES LIKE 'ICE_E%'", "external_volume_name").toString());
        for (final String word : new String[] {"optimized", "'OPTIMIZED'", "'Compatible'"}) {
            engine.executeQuery("CREATE OR REPLACE ICEBERG TABLE ice_e3 (a INT) STORAGE_SERIALIZATION_POLICY = " + word);
        }
    }

    @Test
    public void aContainerNamesACatalogTheAccountHolds() {
        final String invalid = "Catalog integration nosuch_cat is not valid. Please ensure required properties are valid.";
        refused("ALTER SCHEMA test_schema SET CATALOG = 'nosuch_cat'", invalid);
        refused("ALTER SCHEMA test_schema SET CATALOG = nosuch_cat",
            "Catalog integration NOSUCH_CAT is not valid. Please ensure required properties are valid.");
        refused("ALTER DATABASE test_db SET CATALOG = 'nosuch_cat'", invalid);
        refused("ALTER ACCOUNT SET CATALOG = 'nosuch_cat'", invalid);
        refused("CREATE SCHEMA s_cat CATALOG = 'nosuch_cat'", invalid);
        refused("CREATE DATABASE d_cat_m CATALOG = 'nosuch_cat'", invalid);
        assertEquals("|", catalogParameter().replace("null", ""));
        engine.executeQuery("ALTER SCHEMA test_schema SET CATALOG = 'snowflake'");
        assertEquals("SCHEMA|snowflake", catalogParameter());
        engine.executeQuery("ALTER SCHEMA test_schema SET CATALOG = ''");
        assertEquals("SCHEMA|", catalogParameter());
        engine.executeQuery("ALTER SCHEMA test_schema UNSET CATALOG");
        engine.executeQuery("CREATE CATALOG INTEGRATION ice_cat_p CATALOG_SOURCE = OBJECT_STORE TABLE_FORMAT = ICEBERG"
            + " ENABLED = TRUE");
        try {
            engine.executeQuery("ALTER SCHEMA test_schema SET CATALOG = 'ice_cat_p'");
            assertEquals("SCHEMA|ice_cat_p", catalogParameter());
            engine.executeQuery("ALTER SCHEMA test_schema SET CATALOG = ice_cat_p");
            assertEquals("SCHEMA|ICE_CAT_P", catalogParameter());
            // A table takes its catalog from the parameter as it would from the statement.
            refused("CREATE ICEBERG TABLE ice_p (a INT)", "Iceberg table ICE_P must have the table parameter"
                + " EXTERNAL_VOLUME defined on the table, schema, database, or account.");
            refused("CREATE ICEBERG TABLE ice_p (a INT) EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED'",
                "Iceberg tables using Snowflake managed storage must specify 'SNOWFLAKE' as the catalog.");
            refused("CREATE ICEBERG TABLE ice_p (a INT) EXTERNAL_VOLUME = 'ice_vol'", "SQL compilation error: Column"
                + " specifications are only allowed for Iceberg tables using the Snowflake catalog or Iceberg tables"
                + " created within a catalog-linked database.");
            engine.executeQuery("CREATE ICEBERG TABLE ice_p (a INT) CATALOG = 'SNOWFLAKE'");
            assertEquals("[SNOWFLAKE_MANAGED]",
                column("SHOW ICEBERG TABLES LIKE 'ICE_P'", "external_volume_name").toString());
            engine.executeQuery("ALTER SCHEMA test_schema UNSET CATALOG");
            refused("CREATE ICEBERG TABLE ice_q (a INT) EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'ice_cat_p'",
                "Column specifications are only allowed for Iceberg tables");
        } finally {
            engine.executeQuery("ALTER SCHEMA test_schema UNSET CATALOG");
            engine.executeQuery("DROP ICEBERG TABLE IF EXISTS ice_p");
            engine.executeQuery("DROP INTEGRATION IF EXISTS ice_cat_p");
        }
    }

    @Test
    public void anEmptyCatalogStopsWhatTheContainerAboveSets() {
        final String none = "SQL compilation error: Iceberg tables require a catalog integration to be specified";
        final String onVolume = "CREATE ICEBERG TABLE ice_e (a INT) EXTERNAL_VOLUME = 'ice_vol'";
        refused(onVolume, none);
        engine.executeQuery("ALTER DATABASE test_db SET CATALOG = 'SNOWFLAKE'");
        try {
            engine.executeQuery("ALTER SCHEMA test_schema SET CATALOG = ''");
            refused(onVolume, none);
            engine.executeQuery("ALTER SCHEMA test_schema UNSET EXTERNAL_VOLUME");
            engine.executeQuery("ALTER SCHEMA test_schema SET EXTERNAL_VOLUME = 'ice_vol'");
            refused("CREATE ICEBERG TABLE ice_e (a INT)", none);
        } finally {
            engine.executeQuery("ALTER SCHEMA test_schema UNSET EXTERNAL_VOLUME, CATALOG");
            engine.executeQuery("ALTER DATABASE test_db UNSET CATALOG");
        }
    }

    @Test
    public void aCatalogSetOnAContainerStandsInForTheStatements() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account writes to the volume, which is invented here");
        engine.executeQuery("ALTER SCHEMA test_schema SET CATALOG = 'snowflake'");
        engine.executeQuery("CREATE ICEBERG TABLE ice_s (a INT) EXTERNAL_VOLUME = 'ice_vol'");
        assertEquals("[SNOWFLAKE]", column("SHOW ICEBERG TABLES LIKE 'ICE_S'", "catalog_name").toString());
        engine.executeQuery("ALTER SCHEMA test_schema UNSET CATALOG");
        engine.executeQuery("ALTER DATABASE test_db SET CATALOG = 'SNOWFLAKE'");
        engine.executeQuery("CREATE ICEBERG TABLE ice_d (a INT) EXTERNAL_VOLUME = 'ice_vol'");
        assertEquals("[ICE_VOL]", column("SHOW ICEBERG TABLES LIKE 'ICE_D'", "external_volume_name").toString());
        engine.executeQuery("ALTER DATABASE test_db UNSET CATALOG");
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
        engine.executeQuery("CREATE ICEBERG TABLE ice5 (a INT) CLUSTER BY (a)");
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
        assertEquals("[SNOWFLAKE_MANAGED]",
            column("SHOW ICEBERG TABLES LIKE 'ICE5'", "external_volume_name").toString());
    }

    @Test
    public void snowflakeManagedStorageRules() {
        refused("CREATE ICEBERG TABLE ice_loc (a INT) BASE_LOCATION = 'mine/'",
            "BASE_LOCATION property is not supported for Iceberg tables using Snowflake Managed Storage.");
        refused("CREATE ICEBERG TABLE ice_ct (a INT) CHANGE_TRACKING = FALSE",
            "Change Tracking cannot be turned off for Iceberg tables");
        engine.executeQuery("CREATE CATALOG INTEGRATION ice_cat CATALOG_SOURCE = OBJECT_STORE TABLE_FORMAT = ICEBERG"
            + " ENABLED = TRUE");
        try {
            refused("CREATE ICEBERG TABLE ice_ext (a INT) CATALOG = 'ice_cat'", "Iceberg table ICE_EXT must have the"
                + " table parameter EXTERNAL_VOLUME defined on the table, schema, database, or account.");
            refused("CREATE ICEBERG TABLE ice_ext (a INT) EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' CATALOG = 'ICE_CAT'",
                "Iceberg tables using Snowflake managed storage must specify 'SNOWFLAKE' as the catalog.");
        } finally {
            engine.executeQuery("DROP INTEGRATION ice_cat");
        }
        engine.executeQuery("CREATE ICEBERG TABLE ice_src (a INT)");
        engine.executeQuery("CREATE ICEBERG TABLE ice_copy CLONE ice_src");
        assertEquals(column("SHOW ICEBERG TABLES LIKE 'ICE_SRC'", "base_location"),
            column("SHOW ICEBERG TABLES LIKE 'ICE_COPY'", "base_location"), "a clone keeps its source's files");
        assertEquals("[ON]", column("SHOW TABLES LIKE 'ICE_SRC'", "change_tracking").toString());
    }
}
