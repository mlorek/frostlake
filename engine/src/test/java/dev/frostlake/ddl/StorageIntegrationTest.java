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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CREATE, SHOW, DESCRIBE, ALTER and DROP of a storage integration, and the locations a stage that names one may
 * reach. An S3 integration's listing, its rows, a stage inside and outside its allowed locations and a DROP while a
 * stage names it are the account's (live-verified); the GCS and Azure variants, ALTER, UNSET and the blocked
 * locations follow their pages (documented). Engine only: integrations are not created on the live account.
 */
public class StorageIntegrationTest extends BaseDatabaseTest {

    private static final String S3 = "CREATE STORAGE INTEGRATION si_s3 TYPE = EXTERNAL_STAGE"
        + " STORAGE_PROVIDER = 'S3' ENABLED = TRUE STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/my_role'"
        + " STORAGE_ALLOWED_LOCATIONS = ('s3://bucket-1234/path/')";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "integrations are not created on the live account");
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** DESCRIBE's rows, each as {@code property|type|value|default}. */
    private List<String> described(final String name) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("DESCRIBE INTEGRATION " + name).getRows()) {
            out.add(row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3));
        }
        return out;
    }

    /** SHOW INTEGRATIONS' row for one integration: name, type, category, enabled and comment. */
    private String listed(final String like) {
        final Row row = engine.executeQuery("SHOW INTEGRATIONS LIKE '" + like + "'").getRows().get(0);
        return row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|"
            + row.getValue(4);
    }

    @Test
    public void anS3IntegrationIsListedAndDescribedAsTheAccountDoes() {
        assertEquals("Integration SI_S3 successfully created.", status(S3));
        assertEquals("SI_S3|EXTERNAL_STAGE|STORAGE|true|null", listed("SI_S3"));
        assertEquals("SI_S3|EXTERNAL_STAGE|STORAGE|true|null", listed("si_s3"));
        final List<String> rows = described("si_s3");
        assertEquals(9, rows.size(), rows.toString());
        assertEquals("ENABLED|Boolean|true|true", rows.get(0));
        assertEquals("STORAGE_PROVIDER|String|S3|", rows.get(1));
        assertEquals("STORAGE_ALLOWED_LOCATIONS|List|s3://bucket-1234/path/|[]", rows.get(2));
        assertEquals("STORAGE_BLOCKED_LOCATIONS|List||[]", rows.get(3));
        assertTrue(rows.get(4).startsWith("STORAGE_AWS_IAM_USER_ARN|String|arn:aws:iam::"), rows.get(4));
        assertEquals("STORAGE_AWS_ROLE_ARN|String|arn:aws:iam::123456789012:role/my_role|", rows.get(5));
        assertTrue(rows.get(6).startsWith("STORAGE_AWS_EXTERNAL_ID|String|") && rows.get(6).contains("_SFCRole="),
            rows.get(6));
        assertEquals("USE_PRIVATELINK_ENDPOINT|Boolean|false|false", rows.get(7));
        assertEquals("COMMENT|String||", rows.get(8));
        assertEquals("SI_S3|EXTERNAL_STAGE|STORAGE|true|null", listed("SI_S3"));
        assertEquals(1, engine.executeQuery("SHOW STORAGE INTEGRATIONS LIKE 'SI_S3'").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW API INTEGRATIONS LIKE 'SI_S3'").getRows().size());
    }

    @Test
    public void aWrittenExternalIdIsKeptAndTheProviderIsReadInAnyCase() {
        engine.execute("CREATE STORAGE INTEGRATION si_ext TYPE = external_stage STORAGE_PROVIDER = 's3'"
            + " STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/r' STORAGE_AWS_EXTERNAL_ID = 'my-id'"
            + " ENABLED = FALSE STORAGE_ALLOWED_LOCATIONS = ('s3://b1/p/', 's3://b2/p/') COMMENT = 'c'");
        final List<String> rows = described("si_ext");
        assertEquals("ENABLED|Boolean|false|true", rows.get(0));
        assertEquals("STORAGE_PROVIDER|String|S3|", rows.get(1));
        assertEquals("STORAGE_ALLOWED_LOCATIONS|List|s3://b1/p/,s3://b2/p/|[]", rows.get(2));
        assertEquals("STORAGE_AWS_EXTERNAL_ID|String|my-id|", rows.get(6));
        assertEquals("COMMENT|String|c|", rows.get(rows.size() - 1));
        assertEquals("SI_EXT|EXTERNAL_STAGE|STORAGE|false|c", listed("SI_EXT"));
    }

    @Test
    public void aStageReachesOnlyTheLocationsItsIntegrationAllows() {
        engine.execute(S3);
        assertEquals("Stage area ST_IN successfully created.",
            status("CREATE STAGE st_in URL = 's3://bucket-1234/path/' STORAGE_INTEGRATION = si_s3"));
        assertEquals("Stage area ST_SUB successfully created.",
            status("CREATE STAGE st_sub URL = 's3://bucket-1234/path/sub/' STORAGE_INTEGRATION = si_s3"));
        assertEquals("SQL compilation error:\nLocation 's3://other-bucket/path/' is not allowed by integration SI_S3."
            + " Please use DESC INTEGRATION to check out allowed and blocked locations.",
            refusal("CREATE STAGE st_out URL = 's3://other-bucket/path/' STORAGE_INTEGRATION = si_s3"));
        assertEquals("SQL compilation error:\nLocation 's3://other-bucket/path/' is not allowed by integration SI_S3."
            + " Please use DESC INTEGRATION to check out allowed and blocked locations.",
            refusal("CREATE STAGE st_out STORAGE_INTEGRATION = si_s3 URL = 's3://other-bucket/path/'"));
        engine.execute("CREATE STORAGE INTEGRATION si_any TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'S3'"
            + " STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/r' ENABLED = TRUE"
            + " STORAGE_ALLOWED_LOCATIONS = ('*') STORAGE_BLOCKED_LOCATIONS = ('s3://bucket-1234/secret/')");
        assertEquals("Stage area ST_ANY successfully created.",
            status("CREATE STAGE st_any URL = 's3://elsewhere/x/' STORAGE_INTEGRATION = si_any"));
        assertEquals("SQL compilation error:\nLocation 's3://bucket-1234/secret/a/' is not allowed by integration"
            + " SI_ANY. Please use DESC INTEGRATION to check out allowed and blocked locations.",
            refusal("CREATE STAGE st_blocked URL = 's3://bucket-1234/secret/a/' STORAGE_INTEGRATION = si_any"));
        // A stage that names the integration does not keep it from being dropped.
        assertEquals("SI_S3 successfully dropped.", status("DROP INTEGRATION si_s3"));
    }

    @Test
    public void eachProviderTakesAndRequiresItsOwnProperties() {
        assertEquals("SQL compilation error:\nMissing option(s): STORAGE_PROVIDER",
            refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE ENABLED = TRUE"));
        assertEquals("SQL compilation error:\nMissing option(s): STORAGE_AWS_ROLE_ARN",
            refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'S3' ENABLED = TRUE"
                + " STORAGE_ALLOWED_LOCATIONS = ('s3://b/')"));
        assertEquals("SQL compilation error:\nMissing option(s): STORAGE_ALLOWED_LOCATIONS",
            refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'GCS'"
                + " ENABLED = TRUE"));
        assertEquals("SQL compilation error:\nMissing option(s): ENABLED",
            refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'GCS'"
                + " STORAGE_ALLOWED_LOCATIONS = ('gcs://b/')"));
        assertEquals("SQL compilation error:\nMissing option(s): TYPE",
            refusal("CREATE STORAGE INTEGRATION si_bad STORAGE_PROVIDER = 'GCS' ENABLED = TRUE"
                + " STORAGE_ALLOWED_LOCATIONS = ('gcs://b/')"));
        assertTrue(refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'S4'"
            + " ENABLED = TRUE STORAGE_ALLOWED_LOCATIONS = ('s3://b/')").endsWith("for parameter 'STORAGE_PROVIDER'"));
        assertTrue(refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'GCS'"
            + " STORAGE_AWS_ROLE_ARN = 'arn' ENABLED = TRUE STORAGE_ALLOWED_LOCATIONS = ('gcs://b/')")
            .endsWith("for parameter 'STORAGE_AWS_ROLE_ARN'"));
        assertTrue(refusal("CREATE STORAGE INTEGRATION si_bad TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'S3'"
            + " STORAGE_AWS_ROLE_ARN = 'arn' AZURE_TENANT_ID = 't' ENABLED = TRUE"
            + " STORAGE_ALLOWED_LOCATIONS = ('s3://b/')").endsWith("for parameter 'AZURE_TENANT_ID'"));
        assertEquals("Unsupported feature 'STORAGE INTEGRATION of type POSTGRES_INTERNAL_STORAGE'.",
            refusal("CREATE STORAGE INTEGRATION si_pg TYPE = POSTGRES_INTERNAL_STORAGE POSTGRES_INSTANCE = 'p'"));
    }

    @Test
    public void aGcsAndAnAzureIntegrationTellHowTheyAreReached() {
        engine.execute("CREATE STORAGE INTEGRATION si_gcs TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'GCS'"
            + " ENABLED = TRUE STORAGE_ALLOWED_LOCATIONS = ('gcs://b1/p/')");
        final List<String> gcs = described("si_gcs");
        assertEquals(6, gcs.size(), gcs.toString());
        assertEquals("STORAGE_PROVIDER|String|GCS|", gcs.get(1));
        assertTrue(gcs.get(4).startsWith("STORAGE_GCP_SERVICE_ACCOUNT|String|") && gcs.get(4).contains("@"),
            gcs.get(4));
        engine.execute("CREATE STORAGE INTEGRATION si_az TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'AZURE'"
            + " AZURE_TENANT_ID = 'a123b4c5-1234-123a-a12b-1a23b45678c9' ENABLED = TRUE"
            + " STORAGE_ALLOWED_LOCATIONS = ('azure://acct.blob.core.windows.net/c/p/')");
        final List<String> azure = described("si_az");
        assertEquals(9, azure.size(), azure.toString());
        assertEquals("AZURE_TENANT_ID|String|a123b4c5-1234-123a-a12b-1a23b45678c9|", azure.get(4));
        assertTrue(azure.get(5).startsWith("AZURE_CONSENT_URL|String|https://login.microsoftonline.com/"
            + "a123b4c5-1234-123a-a12b-1a23b45678c9/oauth2/authorize?client_id="), azure.get(5));
        assertTrue(azure.get(6).startsWith("AZURE_MULTI_TENANT_APP_NAME|String|"), azure.get(6));
        assertEquals("USE_PRIVATELINK_ENDPOINT|Boolean|false|false", azure.get(7));
    }

    @Test
    public void alterSetsAndUnsetsWhatItsPageAllows() {
        engine.execute(S3);
        assertEquals("Statement executed successfully.", status("ALTER STORAGE INTEGRATION si_s3 SET"
            + " STORAGE_ALLOWED_LOCATIONS = ('s3://b2/') STORAGE_BLOCKED_LOCATIONS = ('s3://b2/x/')"
            + " USE_PRIVATELINK_ENDPOINT = TRUE COMMENT = 'c'"));
        List<String> rows = described("si_s3");
        assertEquals("STORAGE_ALLOWED_LOCATIONS|List|s3://b2/|[]", rows.get(2));
        assertEquals("STORAGE_BLOCKED_LOCATIONS|List|s3://b2/x/|[]", rows.get(3));
        assertEquals("USE_PRIVATELINK_ENDPOINT|Boolean|true|false", rows.get(7));
        assertEquals("Statement executed successfully.",
            status("ALTER INTEGRATION si_s3 UNSET STORAGE_BLOCKED_LOCATIONS, COMMENT"));
        rows = described("si_s3");
        assertEquals("STORAGE_BLOCKED_LOCATIONS|List||[]", rows.get(3));
        assertEquals("COMMENT|String||", rows.get(8));
        assertEquals("SQL compilation error:\ninvalid property 'STORAGE_PROVIDER' for 'INTEGRATION - EXTERNAL_STAGE'",
            refusal("ALTER STORAGE INTEGRATION si_s3 SET STORAGE_PROVIDER = 'GCS'"));
        assertEquals("SQL compilation error:\ninvalid property 'AZURE_TENANT_ID' for 'INTEGRATION - EXTERNAL_STAGE'",
            refusal("ALTER STORAGE INTEGRATION si_s3 SET AZURE_TENANT_ID = 't'"));
        assertEquals("SQL compilation error:\nCannot unset property 'STORAGE_ALLOWED_LOCATIONS' on integration"
            + " 'SI_S3'.", refusal("ALTER STORAGE INTEGRATION si_s3 UNSET STORAGE_ALLOWED_LOCATIONS"));
        assertEquals("Integration SI_S3 is not a API integration.",
            refusal("ALTER API INTEGRATION si_s3 SET ENABLED = TRUE"));
        engine.execute("CREATE NOTIFICATION INTEGRATION si_mail TYPE = EMAIL ENABLED = TRUE");
        assertEquals("Integration SI_MAIL is not a STORAGE integration.",
            refusal("ALTER STORAGE INTEGRATION si_mail SET ENABLED = TRUE"));
    }
}
