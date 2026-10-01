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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Image repositories, services, job services and artifact repositories: the Snowpark Container Services statements
 * over catalog metadata, where nothing runs and a service keeps its declared state.
 */
public class ContainerServicesTest extends BaseDatabaseTest {

    /** Services need an image repository and a compute pool the account does not provide. */
    private static final String NO_CONTAINERS = "a service needs an image repository, an artifact"
        + " repository and a compute pool, none of which this account carries";

    private static final String SPEC = """
        spec:
          containers:
          - name: main
            image: /test_db/test_schema/repo/app:1
          endpoints:
          - name: api
            port: 8080
            public: true
          - name: internal
            port: 9000
        serviceRoles:
        - name: viewer
          endpoints:
          - api
        """;

    @BeforeEach
    public void createPool() {
        engine.execute("CREATE COMPUTE POOL CS_POOL MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS");
    }

    @AfterEach
    public void dropPool() {
        engine.execute("DROP COMPUTE POOL IF EXISTS CS_POOL");
    }

    private static Object cell(final ResultSet rs, final int row, final String column) {
        return rs.getRows().get(row).getValue(rs.getColumnIndex(column));
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
    }

    @Test
    public void imageRepositoriesAreCreatedListedAndDropped() {
        engine.execute("CREATE IMAGE REPOSITORY repo COMMENT = 'images'");
        engine.execute("CREATE IMAGE REPOSITORY IF NOT EXISTS repo");
        engine.execute("CREATE IMAGE REPOSITORY repo2 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        final ResultSet rs = engine.executeQuery("SHOW IMAGE REPOSITORIES LIKE 'REPO%' IN SCHEMA test_db.test_schema");
        assertEquals(2, rs.getRows().size());
        assertEquals("REPO", cell(rs, 0, "name"));
        assertEquals("TEST_DB", cell(rs, 0, "database_name"));
        assertEquals("images", cell(rs, 0, "comment"));
        assertEquals("SNOWFLAKE_SSE", cell(rs, 1, "encryption"));
        assertTrue(String.valueOf(cell(rs, 0, "repository_url")).endsWith("/test_db/test_schema/repo"));
        assertEquals(0, engine.executeQuery("SHOW IMAGES IN IMAGE REPOSITORY repo").getRows().size());
        refused("CREATE IMAGE REPOSITORY repo", "already exists");
        refused("CREATE IMAGE REPOSITORY bad ENCRYPTION = (TYPE = 'OTHER')", "invalid value");
        refused("CREATE IMAGE REPOSITORY bad SIZE = 1", "invalid property 'SIZE' for 'STAGE'");
        engine.execute("ALTER IMAGE REPOSITORY repo SET COMMENT = 'changed'");
        assertEquals("changed", cell(engine.executeQuery("SHOW IMAGE REPOSITORIES LIKE 'REPO'"), 0, "comment"));
        engine.execute("DROP IMAGE REPOSITORY repo2");
        engine.execute("DROP IMAGE REPOSITORY IF EXISTS repo2");
        refused("DROP IMAGE REPOSITORY repo2", "Image repository 'TEST_DB.TEST_SCHEMA.REPO2' does not exist");
        refused("SHOW IMAGES IN IMAGE REPOSITORY repo2", "does not exist");
    }

    @Test
    public void aServiceKeepsItsDeclaredState() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_CONTAINERS);
        engine.execute("CREATE SERVICE svc IN COMPUTE POOL cs_pool FROM SPECIFICATION $$" + SPEC
            + "$$ MIN_INSTANCES = 1 MAX_INSTANCES = 2 COMMENT = 'svc'");
        ResultSet rs = engine.executeQuery("SHOW SERVICES LIKE 'SVC'");
        assertEquals("RUNNING", cell(rs, 0, "status"));
        assertEquals("CS_POOL", cell(rs, 0, "compute_pool"));
        assertEquals(2L, ((Number) cell(rs, 0, "max_instances")).longValue());
        assertEquals("false", cell(rs, 0, "is_job"));
        rs = engine.executeQuery("DESCRIBE SERVICE svc");
        assertTrue(String.valueOf(cell(rs, 0, "spec")).contains("endpoints:"));
        rs = engine.executeQuery("SHOW ENDPOINTS IN SERVICE svc");
        assertEquals(2, rs.getRows().size());
        assertEquals("api", cell(rs, 0, "name"));
        assertEquals("true", cell(rs, 0, "is_public"));
        rs = engine.executeQuery("SHOW ROLES IN SERVICE svc");
        assertEquals("ALL_ENDPOINTS_USAGE", cell(rs, 0, "name"));
        assertEquals("VIEWER", cell(rs, 1, "name"));
        assertEquals(0, engine.executeQuery("SHOW SERVICE CONTAINERS IN SERVICE svc").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW SERVICE INSTANCES IN SERVICE svc").getRows().size());
        engine.execute("ALTER SERVICE svc SUSPEND");
        assertEquals("SUSPENDED", cell(engine.executeQuery("SHOW SERVICES LIKE 'SVC'"), 0, "status"));
        engine.execute("ALTER SERVICE svc RESUME");
        engine.execute("ALTER SERVICE svc SET MAX_INSTANCES = 3 AUTO_RESUME = FALSE");
        engine.execute("ALTER SERVICE svc UNSET COMMENT");
        rs = engine.executeQuery("SHOW SERVICES IN COMPUTE POOL cs_pool");
        assertEquals("RUNNING", cell(rs, 0, "status"));
        assertEquals(3L, ((Number) cell(rs, 0, "max_instances")).longValue());
        assertEquals("false", cell(rs, 0, "auto_resume"));
        refused("ALTER SERVICE svc SET REPLICAS = 2", "invalid property 'REPLICAS'");
        refused("CREATE SERVICE svc IN COMPUTE POOL cs_pool FROM SPECIFICATION 'x'", "already exists");
        refused("CREATE SERVICE svc2 IN COMPUTE POOL no_pool FROM SPECIFICATION 'x'", "does not exist");
        engine.execute("DROP SERVICE svc");
        engine.execute("DROP SERVICE IF EXISTS svc");
        refused("DESCRIBE SERVICE svc", "Service 'TEST_DB.TEST_SCHEMA.SVC' does not exist");
    }

    @Test
    public void aJobServiceIsRecordedAsDone() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_CONTAINERS);
        final ResultSet status = engine.executeQuery("EXECUTE JOB SERVICE IN COMPUTE POOL cs_pool "
            + "FROM @stg SPECIFICATION_FILE = 'job.yaml' NAME = test_db.test_schema.job1");
        assertTrue(String.valueOf(status.getRows().get(0).getValue(0)).contains("JOB1"));
        final ResultSet rs = engine.executeQuery("SHOW JOB SERVICES");
        assertEquals("JOB1", cell(rs, 0, "name"));
        assertEquals("DONE", cell(rs, 0, "status"));
        assertEquals(0, engine.executeQuery("SHOW SERVICES EXCLUDE JOBS").getRows().size());
    }

    @Test
    public void artifactRepositoriesAreCreatedAlteredListedAndDropped() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_CONTAINERS);
        engine.execute("CREATE ARTIFACT REPOSITORY ar TYPE = PYPI API_INTEGRATION = 'pypi_int' COMMENT = 'x'");
        ResultSet rs = engine.executeQuery("SHOW ARTIFACT REPOSITORIES");
        assertEquals("AR", cell(rs, 0, "name"));
        assertEquals("PYPI", cell(rs, 0, "type"));
        assertEquals("x", cell(rs, 0, "comment"));
        engine.execute("ALTER ARTIFACT REPOSITORY ar SET COMMENT = 'y'");
        rs = engine.executeQuery("DESCRIBE ARTIFACT REPOSITORY ar");
        assertEquals("y", cell(rs, 0, "comment"));
        engine.execute("ALTER ARTIFACT REPOSITORY ar UNSET COMMENT");
        refused("CREATE ARTIFACT REPOSITORY ar2", "Missing option(s): [TYPE]");
        refused("CREATE ARTIFACT REPOSITORY ar2 TYPE = MAVEN", "Property 'API_INTEGRATION' must be specified");
        refused("CREATE ARTIFACT REPOSITORY ar2 TYPE = MAVEN API_INTEGRATION = 'i'", "invalid value [MAVEN]");
        engine.execute("DROP ARTIFACT REPOSITORY ar");
        refused("DROP ARTIFACT REPOSITORY ar", "Artifact Repository 'TEST_DB.TEST_SCHEMA.AR' does not exist");
        engine.execute("DROP ARTIFACT REPOSITORY IF EXISTS ar");
    }

    @Test
    public void aServiceFunctionIsRecordedAndRefusesCalls() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_CONTAINERS);
        engine.execute("CREATE SERVICE echo_svc IN COMPUTE POOL cs_pool FROM SPECIFICATION 'spec: {}'");
        engine.execute("CREATE FUNCTION echo(x VARCHAR) RETURNS VARCHAR SERVICE = echo_svc ENDPOINT = api "
            + "MAX_BATCH_ROWS = 10 AS '/echo'");
        final ResultSet rs = engine.executeQuery("DESCRIBE FUNCTION echo(VARCHAR)");
        boolean service = false;
        for (int i = 0; i < rs.getRows().size(); i++) {
            if ("service".equals(rs.getRows().get(i).getValue(0))) {
                service = "ECHO_SVC".equals(rs.getRows().get(i).getValue(1));
            }
        }
        assertTrue(service);
        refused("SELECT echo('a')", "Service function ECHO cannot be called");
        refused("CREATE FUNCTION e2(x VARCHAR) RETURNS VARCHAR SERVICE = no_svc ENDPOINT = api AS '/e'",
            "SERVICE 'NO_SVC' does not exist or not authorized.");
        refused("CREATE FUNCTION e3(x VARCHAR) RETURNS VARCHAR SERVICE = echo_svc AS '/e'",
            "Missing option(s): [ENDPOINT]");
    }

    @Test
    public void theNewKeywordsStayUsableAsNames() {
        engine.execute("CREATE TABLE kw (image INT, images INT, repository INT, repositories INT, artifact INT, "
            + "job INT, jobs INT, containers INT, instances INT, endpoints INT, specification INT, endpoint INT, "
            + "max_batch_rows INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)");
        final ResultSet rs = engine.executeQuery("SELECT image + job + endpoints AS total FROM kw");
        assertEquals(17L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
