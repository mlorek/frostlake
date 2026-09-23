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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CREATE, ALTER, DROP, UNDROP, SHOW and DESCRIBE EXTERNAL VOLUME. */
public class ExternalVolumeStatementsTest extends BaseDatabaseTest {

    private static final String LOCATIONS = "STORAGE_LOCATIONS = ((NAME = 'loc1' STORAGE_PROVIDER = 'S3'"
        + " STORAGE_BASE_URL = 's3://bucket/path/' STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'"
        + " ENCRYPTION = (TYPE = 'AWS_SSE_KMS' KMS_KEY_ID = 'key1')),"
        + " (NAME = 'loc2' STORAGE_PROVIDER = 'GCS' STORAGE_BASE_URL = 'gcs://bucket/'))";

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String describeValue(final String volume, final String property) {
        final ResultSet rs = engine.executeQuery("DESCRIBE EXTERNAL VOLUME " + volume);
        for (final Row row : rs.getRows()) {
            if (property.equals(row.getValue(rs.getColumnIndex("property")))) {
                return String.valueOf(row.getValue(rs.getColumnIndex("property_value")));
            }
        }
        return null;
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
    public void aVolumeIsCreatedShownAndDescribed() {
        assertEquals("EV_A successfully created.",
            status("CREATE EXTERNAL VOLUME ev_a " + LOCATIONS + " ALLOW_WRITES = FALSE COMMENT = 'vol'"));
        final ResultSet shown = engine.executeQuery("SHOW EXTERNAL VOLUMES LIKE 'EV_A'");
        assertEquals(1, shown.getRowCount());
        assertEquals("false", shown.getRows().get(0).getValue(shown.getColumnIndex("allow_writes")));
        assertEquals("vol", shown.getRows().get(0).getValue(shown.getColumnIndex("comment")));

        assertEquals("false", describeValue("ev_a", "ALLOW_WRITES"));
        assertEquals("{\"NAME\":\"loc1\",\"STORAGE_PROVIDER\":\"S3\",\"STORAGE_BASE_URL\":\"s3://bucket/path/\","
            + "\"STORAGE_ALLOWED_LOCATIONS\":[\"s3://bucket/path/*\"],\"STORAGE_AWS_ROLE_ARN\":\"arn:aws:iam::1:role/r\","
            + "\"ENCRYPTION_TYPE\":\"AWS_SSE_KMS\",\"ENCRYPTION_KMS_KEY_ID\":\"key1\"}",
            describeValue("ev_a", "STORAGE_LOCATION_1"));
        assertEquals("", describeValue("ev_a", "ACTIVE"));
        assertEquals("vol", describeValue("ev_a", "COMMENT"));
        engine.executeQuery("DROP EXTERNAL VOLUME ev_a");
    }

    @Test
    public void alterAddsRemovesAndSets() {
        engine.executeQuery("CREATE EXTERNAL VOLUME ev_b " + LOCATIONS);
        engine.executeQuery("ALTER EXTERNAL VOLUME ev_b REMOVE STORAGE_LOCATION 'loc2'");
        engine.executeQuery("ALTER EXTERNAL VOLUME ev_b ADD STORAGE_LOCATION = (NAME = 'loc3'"
            + " STORAGE_PROVIDER = 'AZURE' AZURE_TENANT_ID = 't' STORAGE_BASE_URL = 'azure://a.blob.core.windows.net/c/')");
        assertTrue(describeValue("ev_b", "STORAGE_LOCATION_2").contains("\"NAME\":\"loc3\""));
        engine.executeQuery("ALTER EXTERNAL VOLUME ev_b SET ALLOW_WRITES = FALSE");
        engine.executeQuery("ALTER EXTERNAL VOLUME ev_b SET COMMENT = 'changed'");
        assertEquals("false", describeValue("ev_b", "ALLOW_WRITES"));
        assertEquals("changed", describeValue("ev_b", "COMMENT"));
        refused("ALTER EXTERNAL VOLUME ev_b REMOVE STORAGE_LOCATION 'nosuch'", "Storage location 'nosuch' does not"
            + " exist. Please supply the name of an existing storage location.");
        refused("ALTER EXTERNAL VOLUME ev_b SET STORAGE_BASE_URL = 'x'",
            "invalid property 'STORAGE_BASE_URL' for 'EXTERNAL_VOLUME'");
        engine.executeQuery("DROP EXTERNAL VOLUME ev_b");
    }

    @Test
    public void dropAndUndrop() {
        engine.executeQuery("CREATE EXTERNAL VOLUME ev_c " + LOCATIONS);
        assertEquals("EV_C successfully dropped.", status("DROP EXTERNAL VOLUME ev_c"));
        assertEquals(0, engine.executeQuery("SHOW EXTERNAL VOLUMES LIKE 'EV_C'").getRowCount());
        assertEquals("Drop statement executed successfully (EV_C already dropped).",
            status("DROP EXTERNAL VOLUME IF EXISTS ev_c"));
        refused("DESCRIBE EXTERNAL VOLUME ev_c", "External volume 'EV_C' does not exist or not authorized.");
        assertEquals("External_volume EV_C successfully restored.", status("UNDROP EXTERNAL VOLUME ev_c"));
        assertEquals(1, engine.executeQuery("SHOW EXTERNAL VOLUMES LIKE 'EV_C'").getRowCount());
        refused("UNDROP EXTERNAL VOLUME ev_c", "already exists");
        engine.executeQuery("DROP EXTERNAL VOLUME ev_c");
    }

    @Test
    public void createRefusals() {
        refused("CREATE EXTERNAL VOLUME ev_d ALLOW_WRITES = TRUE", "External volume EV_D must have STORAGE_LOCATIONS"
            + " defined.");
        refused("CREATE EXTERNAL VOLUME ev_d STORAGE_LOCATIONS = ((NAME = 'x' STORAGE_PROVIDER = 'S3'))",
            "Property 'STORAGE_BASE_URL' is required for all storage locations on an external volume.");
        refused("CREATE EXTERNAL VOLUME ev_d STORAGE_LOCATIONS = ((NAME = 'x' STORAGE_PROVIDER = 'FTP'"
            + " STORAGE_BASE_URL = 'ftp://x/'))", "invalid value ''FTP'' for property 'STORAGE_PROVIDER'");
        engine.executeQuery("CREATE EXTERNAL VOLUME ev_d " + LOCATIONS);
        refused("CREATE EXTERNAL VOLUME ev_d " + LOCATIONS, "Object 'EV_D' already exists.");
        assertEquals("EV_D already exists, statement succeeded.",
            status("CREATE EXTERNAL VOLUME IF NOT EXISTS ev_d " + LOCATIONS));
        engine.executeQuery("CREATE OR REPLACE EXTERNAL VOLUME ev_d " + LOCATIONS + " COMMENT = 'replaced'");
        assertEquals("replaced", describeValue("ev_d", "COMMENT"));
        engine.executeQuery("DROP EXTERNAL VOLUME ev_d");
    }

    @Test
    public void locationRefusals() {
        engine.executeQuery("CREATE EXTERNAL VOLUME ev_e STORAGE_LOCATIONS = ((NAME = 'only' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://b/' STORAGE_AWS_ROLE_ARN = 'arn:r'))");
        refused("ALTER EXTERNAL VOLUME ev_e ADD STORAGE_LOCATION = (NAME = 'only' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://c/' STORAGE_AWS_ROLE_ARN = 'arn:r')", "Storage location 'only' already"
            + " exists. Please supply a different name or remove the existing storage location first.");
        refused("ALTER EXTERNAL VOLUME ev_e REMOVE STORAGE_LOCATION 'only'", "Storage location 'only' is the only"
            + " storage location on this external volume and hence cannot be removed.");
        refused("UNDROP EXTERNAL VOLUME ev_never", "External_volume EV_NEVER did not exist or was purged.");
        engine.executeQuery("DROP EXTERNAL VOLUME ev_e");
    }
}
