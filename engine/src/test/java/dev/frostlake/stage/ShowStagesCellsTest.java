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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW STAGES tells a stage's kind, its cloud and whether it carries credentials or an encryption key. The engine
 * told INTERNAL or EXTERNAL alone and N for both flags.
 *
 * <p>On the account {@code type} is EXTERNAL or INTERNAL, with TEMPORARY after it for a temporary stage and NO CSE
 * for an internal stage encrypted on the server side alone; {@code cloud} names an external stage's provider and is
 * NULL for an internal one; {@code has_credentials} is Y for a stage given CREDENTIALS and {@code
 * has_encryption_key} for one whose ENCRYPTION names a master key or a KMS key. Every cell is live-verified.
 */
public class ShowStagesCellsTest extends BaseDatabaseTest {

    private static final String KEY = "eSxX0jzYfIamtnBKOEOwq80Au6NbSgPH5r4BDDwOaO8=";

    /** type, cloud, has_credentials and has_encryption_key of one stage. */
    private String shown(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW STAGES IN SCHEMA test_db.test_schema");
        final Row row = soleRowWhere(rs, "name", name);
        return cell(rs, row, "type") + "|" + cell(rs, row, "cloud") + "|" + cell(rs, row, "has_credentials") + "|"
            + cell(rs, row, "has_encryption_key");
    }

    @Test
    public void anInternalStageTellsItsKind() {
        engine.execute("CREATE STAGE p_int");
        engine.execute("CREATE TEMPORARY STAGE p_tmp");
        engine.execute("CREATE STAGE p_sse ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        assertEquals("INTERNAL|null|N|N", shown("P_INT"));
        assertEquals("INTERNAL TEMPORARY|null|N|N", shown("P_TMP"));
        assertEquals("INTERNAL NO CSE|null|N|N", shown("P_SSE"));
    }

    @Test
    public void anExternalStageTellsItsCloudAndItsSecrets() {
        engine.execute("CREATE STAGE p_ext URL = 's3://fl-test-bucket/path/'");
        engine.execute("CREATE STAGE p_cred URL = 's3://fl-test-bucket/path/' CREDENTIALS = (AWS_KEY_ID ="
            + " 'AKIAXXXXXXXXXXXXXXXX' AWS_SECRET_KEY = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx')");
        engine.execute("CREATE STAGE p_enc URL = 's3://fl-test-bucket/path/' ENCRYPTION = (TYPE = 'AWS_SSE_KMS'"
            + " KMS_KEY_ID = 'k')");
        engine.execute("CREATE STAGE p_mk URL = 's3://fl-test-bucket/path/' ENCRYPTION = (MASTER_KEY = '" + KEY + "')");
        engine.execute("CREATE TEMPORARY STAGE p_text URL = 's3://fl-test-bucket/t/'");
        engine.execute("CREATE STAGE p_az URL = 'azure://fltest.blob.core.windows.net/c/path/'");
        assertEquals("EXTERNAL|AWS|N|N", shown("P_EXT"));
        assertEquals("EXTERNAL|AWS|Y|N", shown("P_CRED"));
        assertEquals("EXTERNAL|AWS|N|Y", shown("P_ENC"));
        assertEquals("EXTERNAL|AWS|N|Y", shown("P_MK"));
        assertEquals("EXTERNAL TEMPORARY|AWS|N|N", shown("P_TEXT"));
        assertEquals("EXTERNAL|AZURE|N|N", shown("P_AZ"));
    }
}
