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
import dev.frostlake.storage.ResultSetColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The integration a pipe, a task, a secret or an artifact repository names has to exist and to be of the kind the
 * clause takes: each clause refuses a miss and a wrong kind in its own words.
 */
public class IntegrationReferenceTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    private static final String[] INTEGRATIONS = {"ir_sns", "ir_hook", "ir_pub", "ir_sec", "ir_secoff", "ir_iam",
        "ir_pypi", "ir_pypioff", "ir_git"};

    @BeforeEach
    public void theIntegrations() {
        engine.execute("CREATE OR REPLACE TABLE pt (a INT)");
        engine.execute("CREATE OR REPLACE STAGE ps");
        engine.execute("CREATE OR REPLACE NOTIFICATION INTEGRATION ir_sns ENABLED = TRUE TYPE = QUEUE"
            + " DIRECTION = OUTBOUND NOTIFICATION_PROVIDER = AWS_SNS"
            + " AWS_SNS_TOPIC_ARN = 'arn:aws:sns:us-east-2:111122223333:sns_topic'"
            + " AWS_SNS_ROLE_ARN = 'arn:aws:iam::111122223333:role/error_sns_role'");
        engine.execute("CREATE OR REPLACE NOTIFICATION INTEGRATION ir_hook TYPE = WEBHOOK ENABLED = TRUE"
            + " WEBHOOK_URL = 'https://hooks.slack.com/services/T0/B0/x'");
        engine.execute("CREATE OR REPLACE NOTIFICATION INTEGRATION ir_pub TYPE = QUEUE"
            + " NOTIFICATION_PROVIDER = GCP_PUBSUB ENABLED = TRUE"
            + " GCP_PUBSUB_SUBSCRIPTION_NAME = 'projects/project-1234/subscriptions/sub2'");
    }

    /** The integrations are the account's, not the test database's: none is left behind. */
    @AfterEach
    public void dropIntegrations() {
        // A secret keeps the integration it names from being dropped.
        for (final String secret : new String[] {"ir_s1", "ir_s2", "ir_s3"}) {
            engine.execute("DROP SECRET IF EXISTS test_db.test_schema." + secret);
        }
        for (final String name : INTEGRATIONS) {
            engine.execute("DROP INTEGRATION IF EXISTS " + name);
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private Object pipeCell(final String pipe, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW PIPES LIKE '" + pipe + "'");
        return rs.getRows().get(0).getValue(rs.getColumnIndex(column));
    }

    @Test
    public void aPipesErrorIntegrationIsAQueueTheAccountWritesTo() {
        final String pipe = "CREATE OR REPLACE PIPE ir_p1 ERROR_INTEGRATION = ";
        final String copy = " AS COPY INTO pt FROM @ps";
        assertEquals(hinted(PREFIX + "Integration 'NOSUCH_INT' does not exist or not authorized."),
            refusal(pipe + "nosuch_int" + copy));
        // A string is the integration's exact name.
        assertEquals(hinted(PREFIX + "Integration '\"ir_sns\"' does not exist or not authorized."),
            refusal(pipe + "'ir_sns'" + copy));
        assertEquals(PREFIX + "Integration 'IR_HOOK' is not a notification integration.",
            refusal(pipe + "ir_hook" + copy));
        assertEquals(PREFIX + "Invalid direction type of error_integration IR_PUB, expected OUTBOUND, got INBOUND.",
            refusal(pipe + "ir_pub" + copy));
        assertEquals("Pipe IR_P1 successfully created.", status(pipe + "ir_sns" + copy));
        assertEquals("IR_SNS", pipeCell("IR_P1", "error_integration"));
        assertEquals("Pipe IR_P1 successfully created.", status(pipe + "'IR_SNS'" + copy));
    }

    @Test
    public void alterPipeSetsAndUnsetsTheErrorIntegration() {
        engine.execute("CREATE OR REPLACE PIPE ir_p2 AS COPY INTO pt FROM @ps");
        assertNull(pipeCell("IR_P2", "error_integration"));
        assertEquals(hinted(PREFIX + "Integration 'NOSUCH_INT' does not exist or not authorized."),
            refusal("ALTER PIPE ir_p2 SET ERROR_INTEGRATION = nosuch_int"));
        assertEquals(PREFIX + "Integration 'IR_HOOK' is not a notification integration.",
            refusal("ALTER PIPE ir_p2 SET ERROR_INTEGRATION = ir_hook"));
        assertEquals(hinted(PREFIX + "Integration '\"ir_sns\"' does not exist or not authorized."),
            refusal("ALTER PIPE ir_p2 SET ERROR_INTEGRATION = 'ir_sns'"));
        engine.execute("ALTER PIPE ir_p2 SET ERROR_INTEGRATION = ir_sns");
        assertEquals("IR_SNS", pipeCell("IR_P2", "error_integration"));
        engine.execute("ALTER PIPE ir_p2 UNSET ERROR_INTEGRATION");
        assertNull(pipeCell("IR_P2", "error_integration"));
        engine.execute("ALTER PIPE ir_p2 UNSET COMMENT, ERROR_INTEGRATION");
        assertEquals(PREFIX + "invalid property 'BOGUS' for 'PIPE'", refusal("ALTER PIPE ir_p2 UNSET BOGUS"));
        assertEquals(PREFIX + "invalid property 'BOGUS' for 'PIPE'", refusal("ALTER PIPE ir_p2 SET BOGUS = 'x'"));
    }

    @Test
    public void aPipesIntegrationIsAQueueTheAccountReadsInTheStagesCloud() {
        final String pipe = "CREATE OR REPLACE PIPE ir_p3 ";
        final String copy = " AS COPY INTO pt FROM @ps";
        assertEquals(hinted(PREFIX + "Integration 'NOSUCH_INT' does not exist or not authorized."),
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'NOSUCH_INT'" + copy));
        assertEquals(hinted(PREFIX + "Integration '\"nosuch_int\"' does not exist or not authorized."),
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'nosuch_int'" + copy));
        assertEquals(hinted(PREFIX + "Integration 'NOSUCH_INT' does not exist or not authorized."),
            refusal(pipe + "INTEGRATION = 'NOSUCH_INT'" + copy));
        assertEquals(PREFIX + "Integration 'IR_HOOK' is not a notification integration.",
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'IR_HOOK'" + copy));
        assertEquals(PREFIX + "Invalid direction type of integration IR_SNS, expected INBOUND, got OUTBOUND.",
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'IR_SNS'" + copy));
        assertEquals("SQL compilation error: \nPROPERTY AUTO_INGEST must be supplied if INTEGRATION is used to create"
            + " a pipe.", refusal(pipe + "INTEGRATION = 'IR_PUB'" + copy));
        assertEquals("SQL compilation error: \nPROPERTY AUTO_INGEST must be set to true if INTEGRATION is used to"
            + " create a pipe.", refusal(pipe + "AUTO_INGEST = FALSE INTEGRATION = 'IR_PUB'" + copy));
        assertEquals("Pipe Notifications bind failure \"Integration and stage should be both from the same cloud"
            + " provider. Integration is in GCP cloud and stage is in AWS cloud.\"",
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'IR_PUB'" + copy));
        assertEquals("Pipe Notifications bind failure \"Integration and stage should be both from the same cloud"
            + " provider. Integration is in GCP cloud and stage is in AWS cloud.\"",
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'IR_PUB' AS COPY INTO pt FROM @%pt"));
        engine.execute("CREATE OR REPLACE STAGE paz URL = 'azure://myaccount.blob.core.windows.net/mycontainer/path/'");
        assertEquals("Pipe Notifications bind failure \"Integration and stage should be both from the same cloud"
            + " provider. Integration is in GCP cloud and stage is in AZURE cloud.\"",
            refusal(pipe + "AUTO_INGEST = TRUE INTEGRATION = 'IR_PUB' AS COPY INTO pt FROM @paz"));
        assertEquals("Pipe Notifications bind failure \"Integration cannot be null for an auto-ingest pipe that uses"
            + " a stage in AZURE.\"", refusal(pipe + "AUTO_INGEST = TRUE AS COPY INTO pt FROM @paz"));
        assertEquals("Pipe IR_P3 successfully created.", status(pipe + "AUTO_INGEST = TRUE" + copy));
        assertEquals("Pipe IR_P3 successfully created.", status(pipe + "AS COPY INTO pt FROM @paz"));
    }

    @Test
    public void aTasksIntegrationIsAQueue() {
        final String task = "CREATE OR REPLACE TASK ir_t1 SCHEDULE = '60 MINUTE' ";
        assertEquals(PREFIX + "Integration 'IR_HOOK' is not a valid notification integration for UserTasks.",
            refusal(task + "ERROR_INTEGRATION = ir_hook AS SELECT 1"));
        assertEquals(PREFIX + "Integration 'IR_HOOK' is not a valid notification integration for UserTasks.",
            refusal(task + "SUCCESS_INTEGRATION = ir_hook AS SELECT 1"));
        assertEquals(hinted(PREFIX + "Integration '\"ir_sns\"' does not exist or not authorized."),
            refusal(task + "ERROR_INTEGRATION = 'ir_sns' AS SELECT 1"));
        assertEquals("Task IR_T1 successfully created.", status(task + "ERROR_INTEGRATION = ir_sns AS SELECT 1"));
        assertEquals("Task IR_T1 successfully created.", status(task + "SUCCESS_INTEGRATION = ir_sns AS SELECT 1"));
        // A queue the account reads from is taken too.
        assertEquals("Task IR_T1 successfully created.", status(task + "ERROR_INTEGRATION = ir_pub AS SELECT 1"));
        assertEquals(PREFIX + "Integration 'IR_HOOK' is not a valid notification integration for UserTasks.",
            refusal("ALTER TASK ir_t1 SET ERROR_INTEGRATION = ir_hook"));
        assertEquals(hinted(PREFIX + "Integration 'NOSUCH_INT' does not exist or not authorized."),
            refusal("ALTER TASK ir_t1 SET ERROR_INTEGRATION = nosuch_int"));
    }

    @Test
    public void aSecretAuthenticatesThroughASecurityIntegrationOfItsKind() {
        engine.execute("CREATE OR REPLACE SECURITY INTEGRATION ir_sec TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs' OAUTH_ALLOWED_SCOPES = ('a', 'b') ENABLED = TRUE");
        engine.execute("CREATE OR REPLACE SECURITY INTEGRATION ir_secoff TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs' ENABLED = FALSE");
        engine.execute("CREATE OR REPLACE SECURITY INTEGRATION ir_iam TYPE = API_AUTHENTICATION AUTH_TYPE = AWS_IAM"
            + " AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/my_role' ENABLED = TRUE");
        final String oauth = "CREATE OR REPLACE SECRET ir_s1 TYPE = OAUTH2 API_AUTHENTICATION = ";
        final String cloud = "CREATE OR REPLACE SECRET ir_s2 TYPE = CLOUD_PROVIDER_TOKEN API_AUTHENTICATION = ";
        assertEquals(PREFIX + "Integration 'NOSUCH_INT' does not exist or not authorized.",
            refusal(oauth + "nosuch_int OAUTH_SCOPES = ('a')"));
        assertEquals(PREFIX + "Integration 'ir_sec' does not exist or not authorized.",
            refusal(oauth + "'ir_sec' OAUTH_SCOPES = ('a')"));
        assertEquals(PREFIX + "Integration 'ir_sec' does not exist or not authorized.",
            refusal(oauth + "\"ir_sec\" OAUTH_SCOPES = ('a')"));
        assertEquals(PREFIX + "Integration 'nosuch_int' does not exist or not authorized.",
            refusal(cloud + "'nosuch_int'"));
        assertEquals("SQL compilation error: Integration 'IR_HOOK' is not of type API Authentication.",
            refusal(oauth + "ir_hook OAUTH_SCOPES = ('a')"));
        assertEquals("SQL compilation error: Integration 'IR_HOOK' is not of type API Authentication.",
            refusal(cloud + "ir_hook"));
        assertEquals(PREFIX + "invalid value [IR_IAM] for parameter 'API_AUTHENTICATION'",
            refusal(oauth + "ir_iam OAUTH_REFRESH_TOKEN = 'x' OAUTH_REFRESH_TOKEN_EXPIRY_TIME = '2030-01-01 00:00:00'"));
        assertEquals(PREFIX + "invalid value [IR_SEC] for parameter 'API_AUTHENTICATION'", refusal(cloud + "ir_sec"));
        assertEquals(PREFIX + "invalid value '[c]' for property 'OAUTH_SCOPES'",
            refusal(oauth + "ir_sec OAUTH_SCOPES = ('c')"));
        assertEquals(PREFIX + "invalid value '[a, c]' for property 'OAUTH_SCOPES'",
            refusal(oauth + "ir_sec OAUTH_SCOPES = ('a', 'c')"));
        assertEquals(PREFIX + "Integration 'IR_SECOFF' is not enabled.", refusal(oauth + "ir_secoff"));
        assertEquals(PREFIX + "invalid property 'ENABLED' for 'SECRET'", refusal(cloud + "ir_iam ENABLED = TRUE"));
        assertEquals("Secret IR_S1 successfully created.", status(oauth + "ir_sec OAUTH_SCOPES = ('a', 'b')"));
        assertEquals("Secret IR_S1 successfully created.", status(oauth + "'IR_SEC' OAUTH_SCOPES = ('a')"));
        assertEquals("Secret IR_S2 successfully created.", status(cloud + "ir_iam"));
        assertEquals("Secret IR_S3 successfully created.", status("CREATE OR REPLACE SECRET ir_s3 TYPE = OAUTH2"
            + " API_AUTHENTICATION = ir_sec"));
        final ResultSet described = engine.executeQuery("DESCRIBE SECRET ir_s1");
        assertEquals("IR_SEC", described.getRows().get(0).getValue(described.getColumnIndex("integration_name")));

        // The integration is held while a secret names it.
        final String held = "Cannot drop or disable integration that is referenced by the secrets"
            + " TEST_DB.TEST_SCHEMA.IR_S1,TEST_DB.TEST_SCHEMA.IR_S3.";
        assertEquals(held, refusal("DROP INTEGRATION ir_sec"));
        assertEquals(held, refusal("ALTER SECURITY INTEGRATION ir_sec SET ENABLED = FALSE"));
        engine.execute("ALTER SECURITY INTEGRATION ir_sec SET COMMENT = 'still there'");
        engine.execute("DROP SECRET ir_s1");
        engine.execute("DROP SECRET ir_s3");
        assertEquals("IR_SEC successfully dropped.", status("DROP INTEGRATION ir_sec"));
    }

    @Test
    public void anArtifactRepositoryNamesAnEnabledPackageIndexIntegration() {
        engine.execute("CREATE OR REPLACE API INTEGRATION ir_pypi API_PROVIDER = pypi ENABLED = TRUE");
        engine.execute("CREATE OR REPLACE API INTEGRATION ir_pypioff API_PROVIDER = pypi ENABLED = FALSE");
        engine.execute("CREATE OR REPLACE API INTEGRATION ir_git API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://github.com/x') ENABLED = TRUE");
        final String repository = "CREATE OR REPLACE ARTIFACT REPOSITORY ir_ar TYPE = PYPI API_INTEGRATION = ";
        assertEquals(PREFIX + "Object 'NOSUCH_INT' does not exist or not authorized.", refusal(repository + "nosuch_int"));
        assertEquals(PREFIX + "Object 'ir_pypi' does not exist or not authorized.", refusal(repository + "'ir_pypi'"));
        assertEquals("SQL compilation error: Invalid API Provider for 'Artifact Repository' object.",
            refusal(repository + "ir_git"));
        assertEquals(PREFIX + "Integration 'IR_PYPIOFF' is not enabled.", refusal(repository + "ir_pypioff"));
        assertEquals("Artifact Repository IR_AR successfully created.", status(repository + "ir_pypi COMMENT = 'c1'"));
        assertEquals("Artifact Repository IR_AR successfully created.", status(repository + "'IR_PYPI' COMMENT = 'c1'"));
        for (final String listing : new String[] {"SHOW ARTIFACT REPOSITORIES LIKE 'IR_AR'",
            "DESCRIBE ARTIFACT REPOSITORY ir_ar"}) {
            final ResultSet rs = engine.executeQuery(listing);
            final List<String> names = new ArrayList<>();
            for (final ResultSetColumn column : rs.getColumns()) {
                names.add(column.getName());
            }
            assertEquals(Arrays.asList("created_on", "name", "database_name", "schema_name", "api_integration", "type",
                "index_url", "authentication_secret", "owner", "owner_role_type", "comment"), names, listing);
            assertEquals("IR_PYPI", rs.getRows().get(0).getValue(rs.getColumnIndex("api_integration")));
            assertEquals("PYPI", rs.getRows().get(0).getValue(rs.getColumnIndex("type")));
            assertNull(rs.getRows().get(0).getValue(rs.getColumnIndex("index_url")));
            assertNull(rs.getRows().get(0).getValue(rs.getColumnIndex("authentication_secret")));
            assertEquals("c1", rs.getRows().get(0).getValue(rs.getColumnIndex("comment")));
        }
        // Dropping the integration leaves the repository naming it.
        engine.execute("DROP ARTIFACT REPOSITORY ir_ar");
    }
}
