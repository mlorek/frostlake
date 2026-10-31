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
import java.util.TreeSet;

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
            + " API_ALLOWED_PREFIXES = ('https://a1.execute-api.us-east-1.amazonaws.com/',"
            + " 'https://b1.execute-api.us-east-1.amazonaws.com/prod/') ENABLED = TRUE COMMENT = 'c1'"));
        final ResultSet shown = engine.executeQuery("SHOW API INTEGRATIONS LIKE 'IT_API1'");
        assertEquals(1, shown.getRowCount());
        final Row row = shown.getRows().get(0);
        assertEquals("EXTERNAL_API", row.getValue(shown.getColumnIndex("type")));
        assertEquals("API", row.getValue(shown.getColumnIndex("category")));
        assertEquals("true", row.getValue(shown.getColumnIndex("enabled")));
        assertEquals("c1", row.getValue(shown.getColumnIndex("comment")));

        final String describe = "DESC API INTEGRATION it_api1";
        assertEquals("AWS_API_GATEWAY", describeValue(describe, "API_PROVIDER"));
        assertEquals("https://a1.execute-api.us-east-1.amazonaws.com/,https://b1.execute-api.us-east-1.amazonaws.com/prod/",
            describeValue(describe, "API_ALLOWED_PREFIXES"));
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
        // A subscription's project id runs to six characters or more, and Pub/Sub carries nothing out of this account.
        refused("CREATE NOTIFICATION INTEGRATION it_bad TYPE = QUEUE NOTIFICATION_PROVIDER = GCP_PUBSUB ENABLED = TRUE"
            + " GCP_PUBSUB_SUBSCRIPTION_NAME = 'projects/p/subscriptions/s'",
            "invalid value ['projects/p/subscriptions/s'] for parameter 'GCP_PUBSUB_SUBSCRIPTION_NAME'");
        refused("CREATE NOTIFICATION INTEGRATION it_bad TYPE = QUEUE DIRECTION = OUTBOUND"
            + " NOTIFICATION_PROVIDER = GCP_PUBSUB ENABLED = TRUE"
            + " GCP_PUBSUB_TOPIC_NAME = 'projects/project-1/topics/topic1'",
            "invalid value 'OUTBOUND' for property 'Direction'");
    }

    /** The statement is refused for one of the options it leaves out; the account does not always name the same one. */
    private void missesOneOf(final String sql, final String... options) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        boolean named = false;
        for (final String option : options) {
            named = named || refusal.getMessage().endsWith("Missing option(s): " + option);
        }
        assertTrue(named, sql + " => " + refusal.getMessage());
    }

    @Test
    public void aMissingOptionIsTold() {
        final String locations = " STORAGE_ALLOWED_LOCATIONS = ('s3://b/')";
        final String stage = "CREATE STORAGE INTEGRATION it_bad TYPE = EXTERNAL_STAGE";
        final String[][] missing = {
            {"CREATE STORAGE INTEGRATION it_bad", "TYPE"}, {"CREATE STORAGE INTEGRATION it_bad ENABLED = TRUE", "TYPE"},
            {stage + " STORAGE_PROVIDER = 'S3' ENABLED = TRUE", "STORAGE_ALLOWED_LOCATIONS"},
            {stage + " STORAGE_PROVIDER = 'S3'" + locations, "ENABLED"},
            {stage + " ENABLED = TRUE" + locations, "STORAGE_PROVIDER"},
            {"CREATE API INTEGRATION it_bad", "TYPE"}, {"CREATE CATALOG INTEGRATION it_bad", "TYPE"},
            {"CREATE NOTIFICATION INTEGRATION it_bad", "TYPE"},
            {"CREATE API INTEGRATION it_bad API_ALLOWED_PREFIXES = ('x')", "ENABLED"},
            {"CREATE API INTEGRATION it_bad ENABLED = TRUE", "API_ALLOWED_PREFIXES"},
            {"CREATE CATALOG INTEGRATION it_bad CATALOG_SOURCE = OBJECT_STORE TABLE_FORMAT = ICEBERG", "ENABLED"},
            {"CREATE CATALOG INTEGRATION it_bad CATALOG_SOURCE = OBJECT_STORE ENABLED = TRUE", "TABLE_FORMAT"},
            {"CREATE CATALOG INTEGRATION it_bad TABLE_FORMAT = ICEBERG ENABLED = TRUE", "CATALOG_SOURCE"},
        };
        for (final String[] cell : missing) {
            refused(cell[0], "Missing option(s): " + cell[1]);
        }
        // With several left out one of them is named.
        missesOneOf(stage, "STORAGE_ALLOWED_LOCATIONS", "ENABLED", "STORAGE_PROVIDER");
        missesOneOf(stage + " ENABLED = TRUE", "STORAGE_ALLOWED_LOCATIONS", "STORAGE_PROVIDER");
        missesOneOf(stage + " STORAGE_PROVIDER = 'S3'", "STORAGE_ALLOWED_LOCATIONS", "ENABLED");
        missesOneOf(stage + locations, "ENABLED", "STORAGE_PROVIDER");
        missesOneOf("CREATE API INTEGRATION it_bad API_PROVIDER = aws_api_gateway", "ENABLED", "API_ALLOWED_PREFIXES");
        missesOneOf("CREATE CATALOG INTEGRATION it_bad ENABLED = TRUE", "CATALOG_SOURCE", "TABLE_FORMAT");
        missesOneOf("CREATE CATALOG INTEGRATION it_bad TABLE_FORMAT = ICEBERG", "CATALOG_SOURCE", "ENABLED");
        missesOneOf("CREATE CATALOG INTEGRATION it_bad CATALOG_SOURCE = OBJECT_STORE", "ENABLED", "TABLE_FORMAT");
        missesOneOf("CREATE CATALOG INTEGRATION it_bad CATALOG_SOURCE = GLUE TABLE_FORMAT = ICEBERG", "ENABLED",
            "GLUE_AWS_ROLE_ARN", "GLUE_CATALOG_ID");
        missesOneOf("CREATE CATALOG INTEGRATION it_bad CATALOG_SOURCE = POLARIS TABLE_FORMAT = ICEBERG ENABLED = TRUE",
            "REST_CONFIG", "REST_AUTHENTICATION");
        // What a provider asks for comes last, in words of its own.
        final String allowed = " ENABLED = TRUE API_ALLOWED_PREFIXES = ('https://a.example.com/')";
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = aws_api_gateway" + allowed, "Missing required property"
            + " 'API_AWS_ROLE_ARN' on API integration with API provider AWS_API_GATEWAY.");
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = azure_api_management AZURE_AD_APPLICATION_ID = 'a'" + allowed,
            "Missing required property 'AZURE_TENANT_ID' on API integration with API provider AZURE_API_MANAGEMENT.");
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = azure_api_management AZURE_TENANT_ID = 't'" + allowed,
            "Missing required property 'AZURE_AD_APPLICATION_ID' on API integration with API provider"
                + " AZURE_API_MANAGEMENT.");
        refused("CREATE API INTEGRATION it_bad API_PROVIDER = google_api_gateway" + allowed,
            "Missing required property 'GOOGLE_AUDIENCE' on API integration with API provider GOOGLE_API_GATEWAY.");
        refused(stage + " STORAGE_PROVIDER = 'S3' ENABLED = TRUE" + locations, "Missing required property"
            + " 'STORAGE_AWS_ROLE_ARN' on storage integration with storage provider S3.");
        refused(stage + " STORAGE_PROVIDER = 'S3GOV' ENABLED = TRUE" + locations, "Missing required property"
            + " 'STORAGE_AWS_ROLE_ARN' on storage integration with storage provider S3GOV.");
        refused(stage + " STORAGE_PROVIDER = 'AZURE' ENABLED = TRUE STORAGE_ALLOWED_LOCATIONS ="
            + " ('azure://a.blob.core.windows.net/c/')", "Missing required property 'AZURE_TENANT_ID' on storage"
            + " integration with storage provider AZURE.");
    }

    private void takesPrefix(final String provider, final String url) {
        engine.executeQuery("CREATE OR REPLACE API INTEGRATION it_url API_PROVIDER = " + provider
            + " API_ALLOWED_PREFIXES = ('" + url + "') ENABLED = TRUE");
        engine.executeQuery("DROP INTEGRATION it_url");
    }

    private void refusesPrefix(final String provider, final String url, final String sentence) {
        refused("CREATE OR REPLACE API INTEGRATION it_url API_PROVIDER = " + provider + " API_ALLOWED_PREFIXES = ('" + url
            + "') ENABLED = TRUE", sentence);
    }

    private static String notAGateway(final String url, final String service) {
        return "SQL compilation error:\nURL '" + url + "' is invalid. Make sure the URL host is reachable and that the URL"
            + " is for " + service + " service.";
    }

    @Test
    public void anAwsGatewaysPrefixIsAnExecuteApiHost() {
        final String aws = "aws_api_gateway API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'";
        for (final String url : new String[] {"https://abc123.execute-api.us-east-1.amazonaws.com/",
            "https://abc123.execute-api.us-east-1.amazonaws.com/prod/", "https://abc123.execute-api.us-east-1.amazonaws.com",
            "http://abc123.execute-api.us-east-1.amazonaws.com/", "HTTPS://abc123.execute-api.us-east-1.amazonaws.com/",
            "ftp://abc123.execute-api.us-east-1.amazonaws.com/", " https://abc123.execute-api.us-east-1.amazonaws.com/",
            "https://abc123.execute-api.us-east-1.amazonaws.com:443/"}) {
            takesPrefix(aws, url);
        }
        for (final String url : new String[] {"https://a.example.com/", "abc123.execute-api.us-east-1.amazonaws.com",
            "https://execute-api.us-east-1.amazonaws.com/", "https://abc.def.execute-api.us-east-1.amazonaws.com/",
            "https://a-b.execute-api.us-east-1.amazonaws.com/", "https://ABC123.EXECUTE-API.US-EAST-1.AMAZONAWS.COM/",
            "https://abc123.execute-api.nosuch-1.amazonaws.com/", "https://abc123.execute-api.amazonaws.com/",
            "https://abc123.execute-api.us-east-1.amazonaws.com.evil.com/", "https://abc123.execute-api.eu-west-1.amazonaws.com/x?y=1",
            "https://abc123.execute-api.us-east-1.amazonaws.com#f", "https://x.azure-api.net/", "x", ""}) {
            refusesPrefix(aws, url, notAGateway(url, "Amazon API Gateway"));
        }
        for (final String url : new String[] {"https://abc123.execute-api.us-gov-west-1.amazonaws.com/",
            "https://abc123.execute-api.cn-north-1.amazonaws.com/"}) {
            refusesPrefix(aws, url, " in URL '" + url + "' is not a supported cloud region.");
        }
        refusesPrefix(aws, "https://abc.execute-api.cn-north-1.amazonaws.com.cn/",
            "'cn-north-1' in URL 'https://abc.execute-api.cn-north-1.amazonaws.com.cn/' is not supported in current region '");
        final String gov = "aws_gov_api_gateway API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'";
        refusesPrefix(gov, "https://a.example.com/", notAGateway("https://a.example.com/", "Amazon Gov API Gateway"));
        refusesPrefix(gov, "https://abc123.execute-api.us-east-1.amazonaws.com/",
            "'us-east-1' in URL 'https://abc123.execute-api.us-east-1.amazonaws.com/' is not a supported cloud region.");
    }

    @Test
    public void theOtherProvidersPrefixes() {
        final String google = "google_api_gateway GOOGLE_AUDIENCE = 'g'";
        for (final String url : new String[] {"https://x.uc.gateway.dev/", "https://x.uc.gateway.dev/p", "http://x.uc.gateway.dev/",
            "https://x.uc.gateway.dev"}) {
            takesPrefix(google, url);
        }
        for (final String url : new String[] {"https://a.example.com/", "https://x.gateway.dev/", "https://a.b.gateway.dev/",
            "https://gateway.dev/", "https://x.uc.gateway.dev.evil.com/", "https://X.UC.GATEWAY.DEV/", "x.uc.gateway.dev",
            "https://x-abc.apigateway.my-proj.cloud.goog/"}) {
            refusesPrefix(google, url, notAGateway(url, "Google API Gateway"));
        }
        refusesPrefix("azure_api_management AZURE_TENANT_ID = 't' AZURE_AD_APPLICATION_ID = 'a'", "https://a.example.com/",
            notAGateway("https://a.example.com/", "Azure API Management"));
        for (final String url : new String[] {"https://github.com/", "https://github.com", "https://a.example.com/",
            "https://github.com:443/", "https://github.com/org/repo.git", "https://github.com/?q=1",
            "https://x-abc.apigateway.my-proj.cloud.goog/"}) {
            takesPrefix("git_https_api", url);
        }
        for (final String url : new String[] {"https://a.b/", "https://a./", "https://.a/", "https://user@github.com/",
            "https://1.2.3.4/", "https://localhost/", "https://z/", "ftp://github.com/", "HTTPS://github.com/",
            "https://a.b.c.d.e/", "https:///github.com/", "https://a b.c/", "http://github.com/", "git://github.com/",
            "https://[::1]/", "github.com", "https://", "x", ""}) {
            refusesPrefix("git_https_api", url, "SQL compilation error: URL '" + url + "' is invalid or not allowed.");
        }
        // With no provider to read it by, the list is refused at its first URL.
        refused("CREATE API INTEGRATION it_url API_ALLOWED_PREFIXES = ('https://github.com/', 'x') ENABLED = TRUE",
            "SQL compilation error: URL 'https://github.com/' is invalid or not allowed.");
        refused("CREATE API INTEGRATION it_url API_PROVIDER = nowhere API_ALLOWED_PREFIXES = ('x') ENABLED = TRUE",
            "invalid value [nowhere] for parameter 'API_PROVIDER'");
        refused("CREATE API INTEGRATION it_url API_PROVIDER = git_https_api API_ALLOWED_PREFIXES = () ENABLED = TRUE",
            "invalid value [TOK_CONSTANT_LIST] for parameter 'API_ALLOWED_PREFIXES'");
        refused("CREATE API INTEGRATION it_url API_PROVIDER = git_https_api API_ALLOWED_PREFIXES = ('https://github.com/')"
            + " API_BLOCKED_PREFIXES = () ENABLED = TRUE", "invalid value [TOK_CONSTANT_LIST] for parameter"
            + " 'API_BLOCKED_PREFIXES'");
        refused("CREATE API INTEGRATION it_url API_PROVIDER = git_https_api API_KEY = 'k' ENABLED = TRUE"
            + " API_ALLOWED_PREFIXES = ('https://github.com/')", "Invalid option(s) for API integration: [API_KEY]");
    }

    @Test
    public void everyPrefixOfAListIsJudgedAtCreateAndAtAlter() {
        final String good = "https://abc123.execute-api.us-east-1.amazonaws.com/";
        final String bad = "https://a.example.com/";
        final String head = "CREATE OR REPLACE API INTEGRATION it_two API_PROVIDER = aws_api_gateway"
            + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r' ENABLED = TRUE";
        refused(head + " API_ALLOWED_PREFIXES = ('" + good + "', '" + bad + "')", notAGateway(bad, "Amazon API Gateway"));
        refused(head + " API_ALLOWED_PREFIXES = ('" + good + "') API_BLOCKED_PREFIXES = ('" + bad + "')",
            notAGateway(bad, "Amazon API Gateway"));
        engine.executeQuery(head + " API_ALLOWED_PREFIXES = ('" + good + "')");
        refused("ALTER API INTEGRATION it_two SET API_ALLOWED_PREFIXES = ('" + bad + "')",
            notAGateway(bad, "Amazon API Gateway"));
        refused("ALTER API INTEGRATION it_two SET API_BLOCKED_PREFIXES = ('" + bad + "')",
            notAGateway(bad, "Amazon API Gateway"));
        engine.executeQuery("ALTER API INTEGRATION it_two SET API_BLOCKED_PREFIXES = ('" + good + "private/')");
        assertEquals(good + "private/", describeValue("DESC INTEGRATION it_two", "API_BLOCKED_PREFIXES"));
        engine.executeQuery("DROP INTEGRATION it_two");
    }

    @Test
    public void aWebhookGoesToAServiceTheAccountKnows() {
        final String head = "CREATE OR REPLACE NOTIFICATION INTEGRATION it_hook2 TYPE = WEBHOOK ENABLED = TRUE WEBHOOK_URL = ";
        // Kept short on purpose: a full-length Slack URL, even a placeholder, is refused as a secret by push protection.
        for (final String url : new String[] {"https://hooks.slack.com/services/T0/B0/x",
            "https://hooks.slack.com/services/T0/B0/SNOWFLAKE_WEBHOOK_SECRET",
            "https://acme.webhook.office.com/webhookb2/a@b/IncomingWebhook/c/d", "https://events.pagerduty.com/v2/enqueue",
            "https://events.pagerduty.com/v2/enqueue?a=1", "HTTPS://events.pagerduty.com/v2/enqueue",
            "https://EVENTS.PAGERDUTY.COM/v2/enqueue"}) {
            engine.executeQuery(head + "'" + url + "'");
            assertEquals(url, describeValue("DESC NOTIFICATION INTEGRATION it_hook2", "WEBHOOK_URL"));
        }
        for (final String url : new String[] {"https://hooks.example.com/x", "https://hooks.slack.com/services/T/B/x",
            "https://hooks.slack.com/", "https://hooks.slack.com/services/SNOWFLAKE_WEBHOOK_SECRET",
            "https://acme.webhook.office.com/webhookb2/SNOWFLAKE_WEBHOOK_SECRET", "https://events.pagerduty.com/",
            "https://events.pagerduty.com/v2/enqueue/", "https://acme.pagerduty.com/v2/enqueue",
            "http://events.pagerduty.com/v2/enqueue", "https://api.opsgenie.com/v2/alerts"}) {
            refused(head + "'" + url + "'", "WEBHOOK_URL: " + url + " is invalid. Verify whether secret replacement makes it"
                + " invalid");
        }
        refused(head + "'x'", "invalid value ['x'] for parameter 'WEBHOOK_URL'");
        refused(head + "''", "invalid value [''] for parameter 'WEBHOOK_URL'");
        engine.executeQuery(head + "'https://events.pagerduty.com/v2/enqueue'");
        refused("ALTER NOTIFICATION INTEGRATION it_hook2 SET WEBHOOK_URL = 'https://hooks.example.com/x'",
            "WEBHOOK_URL: https://hooks.example.com/x is invalid.");
        refused("ALTER NOTIFICATION INTEGRATION it_hook2 SET WEBHOOK_URL = 'x'",
            "invalid value ['x'] for parameter 'WEBHOOK_URL'");
        engine.executeQuery("DROP INTEGRATION it_hook2");
    }

    @Test
    public void anSnsTopicAndItsRoleAreArns() {
        final String head = "CREATE OR REPLACE NOTIFICATION INTEGRATION it_sns2 ENABLED = TRUE TYPE = QUEUE DIRECTION = OUTBOUND"
            + " NOTIFICATION_PROVIDER = AWS_SNS ";
        final String role = " AWS_SNS_ROLE_ARN = 'arn:aws:iam::1:role/r'";
        refused(head + "AWS_SNS_TOPIC_ARN = 'arn:t' AWS_SNS_ROLE_ARN = 'arn:r'",
            "invalid value ['arn:t'] for parameter 'AWS_SNS_TOPIC_ARN'");
        refused(head + "AWS_SNS_TOPIC_ARN = 'arn:aws:sns:us-east-1:1:t' AWS_SNS_ROLE_ARN = 'arn:r'",
            "invalid value ['arn:r'] for parameter 'AWS_SNS_ROLE_ARN'");
        for (final String topic : new String[] {"arn:aws:sns:::t", "arn:aws:s3:us-east-1:1:t", "arn:aws:sns:us-east-1:abc:t"}) {
            refused(head + "AWS_SNS_TOPIC_ARN = '" + topic + "'" + role,
                "invalid value ['" + topic + "'] for parameter 'AWS_SNS_TOPIC_ARN'");
        }
        refused(head + "AWS_SNS_TOPIC_ARN = 'arn:aws-cn:sns:cn-north-1:1:t'" + role,
            "AWS China resources are supported only in China Snowflake deployments.");
        engine.executeQuery(head + "AWS_SNS_TOPIC_ARN = 'arn:aws:sns:us-east-1:1:t'" + role);
        refused("ALTER NOTIFICATION INTEGRATION it_sns2 SET AWS_SNS_TOPIC_ARN = 'arn:t'",
            "invalid value ['arn:t'] for parameter 'AWS_SNS_TOPIC_ARN'");
        refused("ALTER NOTIFICATION INTEGRATION it_sns2 SET AWS_SNS_ROLE_ARN = 'arn:r'",
            "invalid value ['arn:r'] for parameter 'AWS_SNS_ROLE_ARN'");
        engine.executeQuery("DROP INTEGRATION it_sns2");
    }

    @Test
    public void alterSetsAndUnsetsWhatTheKindAllows() {
        engine.executeQuery("CREATE API INTEGRATION it_alter API_PROVIDER = aws_api_gateway"
            + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r' API_ALLOWED_PREFIXES ="
            + " ('https://z1.execute-api.us-east-1.amazonaws.com/') API_KEY = 'k' ENABLED = TRUE COMMENT = 'before'");
        assertEquals("Statement executed successfully.", status("ALTER API INTEGRATION it_alter SET ENABLED = FALSE"
            + " API_BLOCKED_PREFIXES = ('https://z1.execute-api.us-east-1.amazonaws.com/private/') COMMENT = 'after'"));
        final String describe = "DESC INTEGRATION it_alter";
        assertEquals("false", describeValue(describe, "ENABLED"));
        assertEquals("https://z1.execute-api.us-east-1.amazonaws.com/private/",
            describeValue(describe, "API_BLOCKED_PREFIXES"));
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
    public void aRestCatalogKeepsItsVariantProperties() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account reaches for the catalog, which is invented here");
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
        engine.executeQuery("DROP INTEGRATION it_polaris");
    }

    @Test
    public void notificationIntegrationsKeepTheirVariantProperties() {
        try {
            engine.executeQuery("CREATE OR REPLACE NOTIFICATION INTEGRATION it_hook TYPE = WEBHOOK ENABLED = TRUE"
                + " WEBHOOK_URL = 'https://events.pagerduty.com/v2/enqueue'"
                + " WEBHOOK_HEADERS = ('Content-Type'='application/json')");
            engine.executeQuery("CREATE OR REPLACE NOTIFICATION INTEGRATION it_sns ENABLED = TRUE TYPE = QUEUE"
                + " DIRECTION = OUTBOUND NOTIFICATION_PROVIDER = AWS_SNS AWS_SNS_TOPIC_ARN = 'arn:aws:sns:us-east-1:1:t'"
                + " AWS_SNS_ROLE_ARN = 'arn:aws:iam::1:role/r'");
            assertEquals("[QUEUE - AWS_SNS, WEBHOOK]",
                new TreeSet<>(column("SHOW NOTIFICATION INTEGRATIONS LIKE 'IT_%'", "type")).toString());
            assertEquals("{Content-Type=application/json}",
                describeValue("DESC NOTIFICATION INTEGRATION it_hook", "WEBHOOK_HEADERS"));
        } finally {
            engine.executeQuery("DROP INTEGRATION IF EXISTS it_hook");
            engine.executeQuery("DROP INTEGRATION IF EXISTS it_sns");
        }
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
