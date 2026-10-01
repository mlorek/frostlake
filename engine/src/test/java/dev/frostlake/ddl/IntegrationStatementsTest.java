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

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE of API, catalog and notification integrations: the objects are kept as
 * configuration, every kind shares one namespace, and secrets never come back.
 */
public class IntegrationStatementsTest extends BaseDatabaseTest {

    private String status(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private String describeValue(final String sql, final String property) {
        final ResultSet rs = engine.executeQuery(sql);
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
    public void anApiIntegrationIsCreatedShownDescribedAndDropped() {
        assertEquals("Integration IT_API1 successfully created.", status("CREATE API INTEGRATION it_api1"
            + " API_PROVIDER = aws_api_gateway API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r' API_KEY = 'k1'"
            + " API_ALLOWED_PREFIXES = ('https://a.example.com/', 'https://b.example.com/') ENABLED = TRUE"
            + " COMMENT = 'c1'"));
        final ResultSet shown = engine.executeQuery("SHOW API INTEGRATIONS LIKE 'IT_API1'");
        assertEquals(1, shown.getRowCount());
        final Row row = shown.getRows().get(0);
        assertEquals("EXTERNAL_API", row.getValue(shown.getColumnIndex("type")));
        assertEquals("API", row.getValue(shown.getColumnIndex("category")));
        assertEquals("true", row.getValue(shown.getColumnIndex("enabled")));
        assertEquals("c1", row.getValue(shown.getColumnIndex("comment")));

        final String describe = "DESC API INTEGRATION it_api1";
        assertEquals("AWS_API_GATEWAY", describeValue(describe, "API_PROVIDER"));
        assertEquals("https://a.example.com/,https://b.example.com/", describeValue(describe, "API_ALLOWED_PREFIXES"));
        assertEquals("\u263A\u263A", describeValue(describe, "API_KEY"), "a secret is masked");
        assertEquals("true", describeValue(describe, "ENABLED"));

        assertEquals("IT_API1 successfully dropped.", status("DROP INTEGRATION it_api1"));
        assertEquals("Drop statement executed successfully (IT_API1 already dropped).",
            status("DROP INTEGRATION IF EXISTS it_api1"));
        refused("DESCRIBE INTEGRATION it_api1", "Integration 'IT_API1' does not exist or not authorized.");
    }

    @Test
    public void createModesAndTheSharedNamespace() {
        engine.executeQuery("CREATE NOTIFICATION INTEGRATION it_ns TYPE = EMAIL ENABLED = FALSE");
        assertEquals("IT_NS already exists, statement succeeded.", status("CREATE API INTEGRATION IF NOT EXISTS"
            + " it_ns API_PROVIDER = git_https_api API_ALLOWED_PREFIXES = ('https://github.com/') ENABLED = TRUE"));
        refused("CREATE API INTEGRATION it_ns API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://github.com/') ENABLED = TRUE", "Object 'IT_NS' already exists.");
        refused("CREATE OR REPLACE API INTEGRATION IF NOT EXISTS it_ns API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('x') ENABLED = TRUE", "incompatible");
        engine.executeQuery("CREATE OR REPLACE API INTEGRATION it_ns API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://github.com/') ENABLED = TRUE");
        assertEquals("[IT_NS]", column("SHOW API INTEGRATIONS LIKE 'IT_NS'", "name").toString());
        assertEquals("[]", column("SHOW NOTIFICATION INTEGRATIONS LIKE 'IT_NS'", "name").toString());
        refused("DROP NOTIFICATION INTEGRATION it_ns", "Integration IT_NS is not a NOTIFICATION integration.");
        engine.executeQuery("DROP API INTEGRATION it_ns");
    }

    @Test
    public void propertiesAreCheckedPerKind() {
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = aws_api_gateway ENABLED = TRUE",
            "Missing option(s): API_ALLOWED_PREFIXES");
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = nowhere API_ALLOWED_PREFIXES = ('x') ENABLED = TRUE",
            "invalid value [nowhere] for parameter 'API_PROVIDER'");
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = git_https_api API_ALLOWED_PREFIXES = ('x')"
            + " ENABLED = TRUE WEBHOOK_URL = 'x'", "invalid value ['x'] for parameter 'WEBHOOK_URL'");
        refused("CREATE CATALOG INTEGRATION it_bad CATALOG_SOURCE = GLUE TABLE_FORMAT = ICEBERG ENABLED = TRUE",
            "Missing option(s): GLUE_AWS_ROLE_ARN");
        refused("CREATE NOTIFICATION INTEGRATION it_bad TYPE = WEBHOOK ENABLED = TRUE",
            "Missing option(s): WEBHOOK_URL");
        refused("CREATE NOTIFICATION INTEGRATION it_bad TYPE = EMAIL ENABLED = TRUE ENABLED = FALSE",
            "duplicate property 'ENABLED'");
        refused("CREATE STORAGE INTEGRATION it_bad TYPE = EXTERNAL_STAGE", "syntax error");
    }

    @Test
    public void alterSetsAndUnsetsWhatTheKindAllows() {
        engine.executeQuery("CREATE API INTEGRATION it_alter API_PROVIDER = azure_api_management"
            + " AZURE_TENANT_ID = 't' AZURE_AD_APPLICATION_ID = 'a' API_ALLOWED_PREFIXES = ('https://z/')"
            + " API_KEY = 'k' ENABLED = TRUE COMMENT = 'before'");
        assertEquals("Statement executed successfully.", status("ALTER API INTEGRATION it_alter SET ENABLED = FALSE"
            + " API_BLOCKED_PREFIXES = ('https://z/private/') COMMENT = 'after'"));
        final String describe = "DESC INTEGRATION it_alter";
        assertEquals("false", describeValue(describe, "ENABLED"));
        assertEquals("https://z/private/", describeValue(describe, "API_BLOCKED_PREFIXES"));
        assertEquals("after", describeValue(describe, "COMMENT"));
        engine.executeQuery("ALTER INTEGRATION it_alter UNSET API_KEY, COMMENT");
        assertEquals("", describeValue(describe, "API_KEY"));
        assertEquals("", describeValue(describe, "COMMENT"));
        refused("ALTER API INTEGRATION it_alter SET API_PROVIDER = git_https_api",
            "invalid property 'API_PROVIDER' for 'INTEGRATION - EXTERNAL_API'");
        refused("ALTER INTEGRATION it_alter UNSET API_ALLOWED_PREFIXES",
            "Cannot unset property 'API_ALLOWED_PREFIXES' on integration 'IT_ALTER'.");
        assertEquals("Statement executed successfully.", status("ALTER INTEGRATION IF EXISTS it_missing SET"
            + " ENABLED = TRUE"));
        refused("ALTER INTEGRATION it_missing SET ENABLED = TRUE", "does not exist or not authorized");
        engine.executeQuery("DROP INTEGRATION it_alter");
    }

    @Test
    public void catalogAndNotificationIntegrationsKeepTheirVariantProperties() {
        engine.executeQuery("CREATE CATALOG INTEGRATION it_polaris CATALOG_SOURCE = POLARIS TABLE_FORMAT = ICEBERG"
            + " CATALOG_NAMESPACE = 'ns' REST_CONFIG = (CATALOG_URI = 'https://p/api/catalog' CATALOG_NAME = 'c')"
            + " REST_AUTHENTICATION = (TYPE = OAUTH OAUTH_CLIENT_ID = 'id' OAUTH_CLIENT_SECRET = 's'"
            + " OAUTH_ALLOWED_SCOPES = ('PRINCIPAL_ROLE:ALL')) ENABLED = TRUE");
        final String catalog = "DESCRIBE CATALOG INTEGRATION it_polaris";
        assertEquals("{\"CATALOG_URI\":\"https://p/api/catalog\",\"CATALOG_NAME\":\"c\"}",
            describeValue(catalog, "REST_CONFIG"));
        assertTrue(describeValue(catalog, "REST_AUTHENTICATION").contains("\"OAUTH_CLIENT_SECRET\":\"\u263A\""));
        engine.executeQuery("ALTER CATALOG INTEGRATION it_polaris SET REST_AUTHENTICATION ="
            + " (OAUTH_CLIENT_SECRET = 'rotated') REFRESH_INTERVAL_SECONDS = 60");
        assertTrue(describeValue(catalog, "REST_AUTHENTICATION").contains("\"OAUTH_CLIENT_ID\":\"id\""),
            "setting the secret keeps the other entries");
        assertEquals("60", describeValue(catalog, "REFRESH_INTERVAL_SECONDS"));

        engine.executeQuery("CREATE NOTIFICATION INTEGRATION it_hook TYPE = WEBHOOK ENABLED = TRUE"
            + " WEBHOOK_URL = 'https://hooks.example.com/x' WEBHOOK_HEADERS = ('Content-Type'='application/json')");
        engine.executeQuery("CREATE NOTIFICATION INTEGRATION it_sns ENABLED = TRUE TYPE = QUEUE DIRECTION = OUTBOUND"
            + " NOTIFICATION_PROVIDER = AWS_SNS AWS_SNS_TOPIC_ARN = 'arn:t' AWS_SNS_ROLE_ARN = 'arn:r'");
        assertEquals("[WEBHOOK, QUEUE - AWS_SNS]",
            column("SHOW NOTIFICATION INTEGRATIONS LIKE 'IT_%'", "type").toString());
        assertEquals("{Content-Type=application/json}",
            describeValue("DESC NOTIFICATION INTEGRATION it_hook", "WEBHOOK_HEADERS"));
        engine.executeQuery("DROP INTEGRATION it_polaris");
        engine.executeQuery("DROP INTEGRATION it_hook");
        engine.executeQuery("DROP INTEGRATION it_sns");
    }

    @Test
    public void theNewKeywordsStillNameColumns() {
        final ResultSet rs = engine.executeQuery("SELECT 1 AS catalog, 2 AS event, 3 AS volume, 4 AS api,"
            + " 5 AS storage, 6 AS security, 7 AS external, 8 AS notification, 9 AS managed");
        assertEquals(9, rs.getColumns().size());
        assertEquals("CATALOG", rs.getColumns().get(0).getName());
    }

    @Test
    public void alterTakesTheVariantsOwnPropertiesAndUnsetRestoresTheDefaults() {
        engine.executeQuery("CREATE NOTIFICATION INTEGRATION it_mail2 TYPE = EMAIL ENABLED = FALSE");
        refused("ALTER NOTIFICATION INTEGRATION it_mail2 SET WEBHOOK_URL = 'https://h/x'",
            "invalid property 'WEBHOOK_URL' for 'INTEGRATION - EMAIL'");
        engine.executeQuery("ALTER INTEGRATION it_mail2 UNSET ENABLED");
        assertEquals("true", describeValue("DESC INTEGRATION it_mail2", "ENABLED"), "ENABLED defaults to TRUE");
        engine.executeQuery("CREATE CATALOG INTEGRATION it_obj2 CATALOG_SOURCE = OBJECT_STORE TABLE_FORMAT = ICEBERG"
            + " ENABLED = TRUE");
        engine.executeQuery("ALTER CATALOG INTEGRATION it_obj2 SET ENABLED = FALSE");
        assertEquals("false", describeValue("DESC CATALOG INTEGRATION it_obj2", "ENABLED"));
        assertEquals("30", describeValue("DESC CATALOG INTEGRATION it_obj2", "REFRESH_INTERVAL_SECONDS"));
        refused("ALTER CATALOG INTEGRATION it_obj2 SET TABLE_FORMAT = DELTA",
            "invalid property 'TABLE_FORMAT' for 'INTEGRATION - CATALOG'");
        refused("DESC NOTIFICATION INTEGRATION it_obj2", "Integration IT_OBJ2 is not a NOTIFICATION integration.");
        engine.executeQuery("DROP INTEGRATION it_mail2");
        engine.executeQuery("DROP INTEGRATION it_obj2");
    }
}
