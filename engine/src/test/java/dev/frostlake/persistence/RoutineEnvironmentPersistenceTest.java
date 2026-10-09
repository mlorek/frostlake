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
package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a routine is given to run with survives a save and a reload - its packages, artifact repository, target
 * path and secrets, and a procedure's restricted caller's rights - and so do the contacts and tags of a task.
 */
public class RoutineEnvironmentPersistenceTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_routine_environment_");
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dataDir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void tearDown() {
        if (dataDir != null) {
            deleteRecursively(dataDir.toFile());
        }
    }

    private DatabaseEngine open(final boolean create) {
        final DatabaseEngine engine = new DatabaseEngine(config);
        if (create) {
            engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
            engine.execute("USE DATABASE test_db");
            engine.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
        } else {
            engine.execute("USE DATABASE test_db");
        }
        engine.execute("USE SCHEMA test_schema");
        return engine;
    }

    private static Schema schema(final DatabaseEngine engine) {
        return engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
    }

    @Test
    public void aRoutinesEnvironmentSurvivesAReload() {
        final DatabaseEngine first = open(true);
        first.execute("CREATE SECRET re_sec TYPE = GENERIC_STRING SECRET_STRING = 'v'");
        first.execute("CREATE STAGE re_st");
        first.execute("CREATE FUNCTION re_p(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " HANDLER = 'h' ARTIFACT_REPOSITORY = SNOWFLAKE.SNOWPARK.PYPI_SHARED_REPOSITORY PACKAGES = ('numpy')"
            + " SECRETS = ('k' = re_sec) AS $$\ndef h(x):\n    return x\n$$");
        first.execute("CREATE FUNCTION re_j(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' TARGET_PATH ="
            + " '@re_st/re.jar' AS $$ class H { public static long h(long x) { return x; } } $$");
        first.execute("CREATE PROCEDURE re_proc() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS RESTRICTED CALLER AS"
            + " 'BEGIN RETURN 1; END'");
        first.shutdown();

        final DatabaseEngine second = open(false);
        final Function python = schema(second).getFunctionOverloads("RE_P").get(0);
        assertEquals(Arrays.asList("numpy"), python.getEnvironment().getPackages());
        assertEquals("SNOWFLAKE.SNOWPARK.PYPI_SHARED_REPOSITORY", python.getEnvironment().getArtifactRepository());
        assertEquals("TEST_DB.TEST_SCHEMA.RE_SEC", python.getEnvironment().getSecrets().get("k"));
        assertEquals("{\"k\":\"RE_SEC\"}", python.getEnvironment().secretsText("TEST_DB", "TEST_SCHEMA"));
        final Function java = schema(second).getFunctionOverloads("RE_J").get(0);
        assertEquals("@TEST_DB.TEST_SCHEMA.RE_ST/re.jar", java.getEnvironment().getTargetPath());
        final Procedure procedure = schema(second).getProcedureOverloads("RE_PROC").get(0);
        assertEquals("RESTRICTED CALLER", procedure.getExecuteAs());
        second.shutdown();
    }

    @Test
    public void aTasksContactsAndTagsSurviveAReload() {
        final DatabaseEngine first = open(true);
        first.execute("CREATE TAG re_tag");
        first.execute("CREATE CONTACT re_contact EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        first.execute("CREATE TASK re_task WITH TAG (re_tag = 'v') WITH CONTACT (SUPPORT = re_contact) AS SELECT 1");
        first.shutdown();

        final DatabaseEngine second = open(false);
        final Task task = schema(second).getTask("RE_TASK");
        assertEquals("TEST_DB.TEST_SCHEMA.RE_CONTACT", task.getContacts().get("SUPPORT"));
        assertEquals("create or replace task RE_TASK\n WITH TAG (TEST_DB.TEST_SCHEMA.RE_TAG='v')\n"
            + " WITH CONTACT (SUPPORT=TEST_DB.TEST_SCHEMA.RE_CONTACT)\n\tas SELECT 1;",
            String.valueOf(second.executeQuery("SELECT GET_DDL('TASK', 're_task')").getRows().get(0).getValue(0)));
        second.shutdown();
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
