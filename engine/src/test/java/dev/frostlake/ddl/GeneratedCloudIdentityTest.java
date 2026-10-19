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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identity an account generates for an integration or a storage location that assumes a cloud role: DESCRIBE
 * shows the IAM user and the external ID beside the role, in the row order the account uses.
 */
public class GeneratedCloudIdentityTest extends BaseDatabaseTest {

    private static final String ROLE = "'arn:aws:iam::123456789012:role/fl_ident'";
    private static final String USER_ARN = "arn:aws:iam::\\d{12}:user/\\S+";
    private static final String EXTERNAL_ID = "[A-Z0-9]+_SFCRole=\\d+_[A-Za-z0-9+/]{27}=";
    private static final String SERVICE_ACCOUNT = "\\S+@\\S+\\.iam\\.gserviceaccount\\.com";
    private static final Pattern JSON_KEY = Pattern.compile("\"([A-Z_]+)\":");
    private static final Pattern JSON_TEXT = Pattern.compile("\"([A-Z_]+)\":\"([^\"]*)\"");

    @Override
    protected void teardownTest() {
        for (final String name : new String[] {"gci_api", "gci_gg", "gci_glue", "gci_glue2", "gci_sns"}) {
            engine.executeQuery("DROP INTEGRATION IF EXISTS " + name);
        }
        for (final String name : new String[] {"gci_vol", "gci_vol_g"}) {
            engine.executeQuery("DROP EXTERNAL VOLUME IF EXISTS " + name);
        }
    }

    /** The rows of a DESCRIBE INTEGRATION as property to value, in the order shown. */
    private Map<String, String> properties(final String describe) {
        final ResultSet rs = engine.executeQuery(describe);
        final Map<String, String> out = new LinkedHashMap<>();
        for (final Row row : rs.getRows()) {
            out.put(String.valueOf(row.getValue(rs.getColumnIndex("property"))),
                String.valueOf(row.getValue(rs.getColumnIndex("property_value"))));
        }
        return out;
    }

    private static List<String> keys(final String json) {
        final List<String> out = new ArrayList<>();
        final Matcher matcher = JSON_KEY.matcher(json);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    private static String member(final String json, final String key) {
        final Matcher matcher = JSON_TEXT.matcher(json);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private static void assertMatches(final String pattern, final String value) {
        assertTrue(value != null && value.matches(pattern), value + " is no " + pattern);
    }

    @Test
    public void anApiIntegrationShowsTheIdentityThatAssumesItsRole() {
        engine.executeQuery("CREATE API INTEGRATION gci_api API_PROVIDER = aws_api_gateway API_AWS_ROLE_ARN = " + ROLE
            + " API_ALLOWED_PREFIXES = ('https://x.execute-api.us-east-1.amazonaws.com/') ENABLED = TRUE");
        final Map<String, String> shown = properties("DESC API INTEGRATION gci_api");
        assertEquals("[ENABLED, API_KEY, API_PROVIDER, API_AWS_IAM_USER_ARN, API_AWS_ROLE_ARN, API_AWS_EXTERNAL_ID,"
            + " API_ALLOWED_PREFIXES, API_BLOCKED_PREFIXES, COMMENT]", shown.keySet().toString());
        assertMatches(USER_ARN, shown.get("API_AWS_IAM_USER_ARN"));
        assertMatches(EXTERNAL_ID, shown.get("API_AWS_EXTERNAL_ID"));
        // The identity is the integration's for as long as it stands.
        engine.executeQuery("ALTER API INTEGRATION gci_api SET COMMENT = 'c'");
        assertEquals(shown.get("API_AWS_EXTERNAL_ID"),
            properties("DESC API INTEGRATION gci_api").get("API_AWS_EXTERNAL_ID"));
        engine.executeQuery("CREATE API INTEGRATION gci_gg API_PROVIDER = google_api_gateway GOOGLE_AUDIENCE = 'aud'"
            + " API_ALLOWED_PREFIXES = ('https://x.uc.gateway.dev/') ENABLED = TRUE");
        final Map<String, String> google = properties("DESC API INTEGRATION gci_gg");
        assertEquals("[ENABLED, API_KEY, API_PROVIDER, API_GCP_SERVICE_ACCOUNT, GOOGLE_AUDIENCE, API_ALLOWED_PREFIXES,"
            + " API_BLOCKED_PREFIXES, COMMENT]", google.keySet().toString());
        assertMatches(SERVICE_ACCOUNT, google.get("API_GCP_SERVICE_ACCOUNT"));
    }

    /**
     * The external ID is the creation's own: an ALTER keeps it, a CREATE OR REPLACE and a DROP followed by a CREATE
     * make another, while the IAM user stays the account's one.
     */
    @Test
    public void anExternalIdIsGeneratedForEachCreation() {
        final String api = "API INTEGRATION gci_api API_PROVIDER = aws_api_gateway API_AWS_ROLE_ARN = " + ROLE
            + " API_ALLOWED_PREFIXES = ('https://x.execute-api.us-east-1.amazonaws.com/') ENABLED = TRUE";
        engine.executeQuery("CREATE OR REPLACE " + api);
        final Map<String, String> first = properties("DESC API INTEGRATION gci_api");
        engine.executeQuery("CREATE OR REPLACE " + api);
        final Map<String, String> replaced = properties("DESC API INTEGRATION gci_api");
        assertNotEquals(first.get("API_AWS_EXTERNAL_ID"), replaced.get("API_AWS_EXTERNAL_ID"));
        assertEquals(first.get("API_AWS_IAM_USER_ARN"), replaced.get("API_AWS_IAM_USER_ARN"));
        engine.executeQuery("ALTER API INTEGRATION gci_api SET API_AWS_ROLE_ARN ="
            + " 'arn:aws:iam::123456789012:role/fl_ident2'");
        assertEquals(replaced.get("API_AWS_EXTERNAL_ID"),
            properties("DESC API INTEGRATION gci_api").get("API_AWS_EXTERNAL_ID"));
        engine.executeQuery("DROP INTEGRATION gci_api");
        engine.executeQuery("CREATE " + api);
        final Map<String, String> again = properties("DESC API INTEGRATION gci_api");
        assertMatches(EXTERNAL_ID, again.get("API_AWS_EXTERNAL_ID"));
        assertNotEquals(replaced.get("API_AWS_EXTERNAL_ID"), again.get("API_AWS_EXTERNAL_ID"));
    }

    /** A volume's external ID likewise: another after CREATE OR REPLACE, the same after a DROP that is undone. */
    @Test
    public void aVolumesExternalIdOutlivesADropUndone() {
        final String volume = "EXTERNAL VOLUME gci_vol STORAGE_LOCATIONS = ((NAME = 'l1' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://fl-ident/' STORAGE_AWS_ROLE_ARN = " + ROLE + "))";
        engine.executeQuery("CREATE OR REPLACE " + volume);
        final String first = member(properties("DESCRIBE EXTERNAL VOLUME gci_vol").get("STORAGE_LOCATION_1"),
            "STORAGE_AWS_EXTERNAL_ID");
        engine.executeQuery("CREATE OR REPLACE " + volume);
        final String replaced = member(properties("DESCRIBE EXTERNAL VOLUME gci_vol").get("STORAGE_LOCATION_1"),
            "STORAGE_AWS_EXTERNAL_ID");
        assertMatches(EXTERNAL_ID, replaced);
        assertNotEquals(first, replaced);
        engine.executeQuery("DROP EXTERNAL VOLUME gci_vol");
        engine.executeQuery("UNDROP EXTERNAL VOLUME gci_vol");
        assertEquals(replaced, member(properties("DESCRIBE EXTERNAL VOLUME gci_vol").get("STORAGE_LOCATION_1"),
            "STORAGE_AWS_EXTERNAL_ID"));
        engine.executeQuery("DROP EXTERNAL VOLUME gci_vol");
        engine.executeQuery("CREATE " + volume);
        assertNotEquals(replaced, member(properties("DESCRIBE EXTERNAL VOLUME gci_vol").get("STORAGE_LOCATION_1"),
            "STORAGE_AWS_EXTERNAL_ID"));
    }

    @Test
    public void aGlueCatalogAndAnSnsTopicShowTheirs() {
        engine.executeQuery("CREATE CATALOG INTEGRATION gci_glue CATALOG_SOURCE = GLUE CATALOG_NAMESPACE = 'ns'"
            + " TABLE_FORMAT = ICEBERG GLUE_AWS_ROLE_ARN = " + ROLE + " GLUE_CATALOG_ID = '123456789012' ENABLED = TRUE");
        final Map<String, String> glue = properties("DESC CATALOG INTEGRATION gci_glue");
        assertEquals("[ENABLED, CATALOG_SOURCE, CATALOG_NAMESPACE, TABLE_FORMAT, REFRESH_INTERVAL_SECONDS,"
            + " GLUE_AWS_ROLE_ARN, GLUE_CATALOG_ID, GLUE_REGION, GLUE_AWS_IAM_USER_ARN, GLUE_AWS_EXTERNAL_ID, COMMENT]",
            glue.keySet().toString());
        assertMatches(USER_ARN, glue.get("GLUE_AWS_IAM_USER_ARN"));
        assertMatches(EXTERNAL_ID, glue.get("GLUE_AWS_EXTERNAL_ID"));
        // A region left out is the account's own, as its cloud names it.
        assertMatches("[a-z]+(-[a-z]+)+-\\d", glue.get("GLUE_REGION"));
        engine.executeQuery("CREATE CATALOG INTEGRATION gci_glue2 CATALOG_SOURCE = GLUE CATALOG_NAMESPACE = 'ns'"
            + " TABLE_FORMAT = ICEBERG GLUE_AWS_ROLE_ARN = " + ROLE + " GLUE_CATALOG_ID = '123456789012'"
            + " GLUE_REGION = 'us-east-2' ENABLED = TRUE");
        final Map<String, String> second = properties("DESC CATALOG INTEGRATION gci_glue2");
        assertEquals("us-east-2", second.get("GLUE_REGION"));
        assertNotEquals(glue.get("GLUE_AWS_EXTERNAL_ID"), second.get("GLUE_AWS_EXTERNAL_ID"));
        assertEquals(glue.get("GLUE_AWS_IAM_USER_ARN"), second.get("GLUE_AWS_IAM_USER_ARN"));
        engine.executeQuery("CREATE NOTIFICATION INTEGRATION gci_sns TYPE = QUEUE NOTIFICATION_PROVIDER = AWS_SNS"
            + " DIRECTION = OUTBOUND AWS_SNS_TOPIC_ARN = 'arn:aws:sns:us-east-1:123456789012:fl_ident'"
            + " AWS_SNS_ROLE_ARN = " + ROLE + " ENABLED = TRUE");
        final Map<String, String> sns = properties("DESC NOTIFICATION INTEGRATION gci_sns");
        assertEquals("[ENABLED, NOTIFICATION_PROVIDER, DIRECTION, AWS_SNS_TOPIC_ARN, AWS_SNS_ROLE_ARN,"
            + " SF_AWS_IAM_USER_ARN, SF_AWS_EXTERNAL_ID, COMMENT]", sns.keySet().toString());
        assertEquals(glue.get("GLUE_AWS_IAM_USER_ARN"), sns.get("SF_AWS_IAM_USER_ARN"));
        assertMatches(EXTERNAL_ID, sns.get("SF_AWS_EXTERNAL_ID"));
    }

    @Test
    public void aStorageLocationShowsItsOwn() {
        engine.executeQuery("CREATE OR REPLACE EXTERNAL VOLUME gci_vol STORAGE_LOCATIONS = ((NAME = 'l1'"
            + " STORAGE_PROVIDER = 'S3' STORAGE_BASE_URL = 's3://fl-ident/' STORAGE_AWS_ROLE_ARN = " + ROLE
            + " STORAGE_AWS_EXTERNAL_ID = 'my_ext_id'), (NAME = 'l2' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://fl-ident2/' STORAGE_AWS_ROLE_ARN = " + ROLE + "), (NAME = 'l3'"
            + " STORAGE_PROVIDER = 'S3' STORAGE_BASE_URL = 's3://fl-ident3/' STORAGE_AWS_ROLE_ARN = " + ROLE + "))");
        final Map<String, String> shown = properties("DESCRIBE EXTERNAL VOLUME gci_vol");
        final String first = shown.get("STORAGE_LOCATION_1");
        final String second = shown.get("STORAGE_LOCATION_2");
        final String third = shown.get("STORAGE_LOCATION_3");
        final String layout = "[NAME, STORAGE_PROVIDER, STORAGE_BASE_URL, STORAGE_ALLOWED_LOCATIONS,"
            + " STORAGE_AWS_ROLE_ARN, STORAGE_AWS_IAM_USER_ARN, STORAGE_AWS_EXTERNAL_ID, ENCRYPTION_TYPE,"
            + " ENCRYPTION_KMS_KEY_ID]";
        assertEquals(layout, keys(first).toString());
        assertEquals(layout, keys(second).toString());
        // An external ID that was written is the location's; one left out is the one generated for the volume.
        assertEquals("my_ext_id", member(first, "STORAGE_AWS_EXTERNAL_ID"));
        assertMatches(EXTERNAL_ID, member(second, "STORAGE_AWS_EXTERNAL_ID"));
        assertEquals(member(second, "STORAGE_AWS_EXTERNAL_ID"), member(third, "STORAGE_AWS_EXTERNAL_ID"));
        assertMatches(USER_ARN, member(first, "STORAGE_AWS_IAM_USER_ARN"));
        assertEquals(member(first, "STORAGE_AWS_IAM_USER_ARN"), member(second, "STORAGE_AWS_IAM_USER_ARN"));
        engine.executeQuery("CREATE OR REPLACE EXTERNAL VOLUME gci_vol_g STORAGE_LOCATIONS = ((NAME = 'g1'"
            + " STORAGE_PROVIDER = 'GCS' STORAGE_BASE_URL = 'gcs://fl-ident/'))");
        final String google = properties("DESCRIBE EXTERNAL VOLUME gci_vol_g").get("STORAGE_LOCATION_1");
        assertEquals("[NAME, STORAGE_PROVIDER, STORAGE_BASE_URL, STORAGE_ALLOWED_LOCATIONS,"
            + " STORAGE_GCP_SERVICE_ACCOUNT, ENCRYPTION_TYPE, ENCRYPTION_KMS_KEY_ID]", keys(google).toString());
        assertMatches(SERVICE_ACCOUNT, member(google, "STORAGE_GCP_SERVICE_ACCOUNT"));
    }
}
