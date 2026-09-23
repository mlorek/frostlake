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
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PIPE surface — CREATE (incl. AUTO_INGEST, OR REPLACE, IF NOT EXISTS), ALTER SET
 * PIPE_EXECUTION_PAUSED, DROP, SHOW/DESC PIPES, SYSTEM$PIPE_STATUS — over stages the fixture
 * really creates, so the cells run on every transport. A pipe whose COPY names a missing stage
 * refuses at CREATE with the stage's does-not-exist shape (live-verified). Engine-internal reads
 * ({@code engine.getCatalog()} pipe state) stay embedded-only beside the SQL-observable asserts;
 * cells naming notification integrations or SNS topics stay embedded-only because the test
 * account has no such integrations.
 */
public class PipesTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(PipesTest.class);

    private static final String ACCOUNT_HAS_NO_INTEGRATIONS =
        "names a notification/error integration (or SNS topic) that would have to exist on the "
        + "account for a real run to accept the statement";

    private static final String PIPE_REFRESH_NEEDS_CLOUD_STORAGE =
        "ALTER PIPE ... REFRESH makes the account read the stage's cloud storage; the URL here is "
        + "a placeholder bucket the account has no credentials for, so Snowflake answers with "
        + "Access Denied (403)";

    @BeforeEach
    public void createStages() {
        engine.execute("CREATE STAGE ps");
        engine.execute("CREATE STAGE ps2");
    }

    /** Engine-internal pipe state — meaningful on the embedded engine the catalog belongs to. */
    private Pipe catalogPipe(final String name) {
        final Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        return schema.getPipe(name);
    }

    private String pipeStatus(final String name) {
        return engine.executeQuery("SELECT SYSTEM$PIPE_STATUS('" + name + "')")
            .getRows().get(0).getValue(0).toString();
    }

    private String pipeDefinition(final String name) {
        final ResultSet rs = engine.executeQuery("DESCRIBE PIPE " + name);
        assertEquals(1, rs.getRowCount());
        // columns: created_on(0) name(1) database_name(2) schema_name(3) definition(4) ...
        return rs.getRows().get(0).getValue(4).toString();
    }

    @Test
    public void testCreateSimplePipe() {
        logger.info("Testing CREATE PIPE with basic configuration");

        engine.execute("CREATE TABLE target_table (id INTEGER, name VARCHAR, value DECIMAL(10,2))");
        engine.execute("""
            CREATE PIPE my_pipe
            AS COPY INTO target_table
            FROM @ps
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        assertTrue(pipeStatus("my_pipe").contains("RUNNING"));
        if (!isLiveSnowflake()) {
            final Pipe pipe = catalogPipe("my_pipe");
            assertEquals("my_pipe", pipe.getName().toLowerCase());
            assertFalse(pipe.isAutoIngest());
            assertFalse(pipe.isPaused());
            assertEquals("RUNNING", pipe.getStatus());
        }
    }

    @Test
    public void testCreatePipeOverMissingStageRefuses() {
        engine.execute("CREATE TABLE target_table (id INTEGER)");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PIPE bad_pipe AS COPY INTO target_table FROM @no_such_stage");
            }
        });
        assertEquals(hinted("SQL compilation error:\n"
            + "Stage 'TEST_DB.TEST_SCHEMA.NO_SUCH_STAGE' does not exist or not authorized."),
            e.getMessage());
    }

    /** The target is compiled at CREATE too — its own sentence, without "or not authorized". */
    @Test
    public void testCreatePipeOverMissingTargetTableRefuses() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PIPE bad_pipe AS COPY INTO no_such_table FROM @ps");
            }
        });
        assertEquals("SQL compilation error:\nTable 'NO_SUCH_TABLE' does not exist", e.getMessage());
    }

    @Test
    public void testCreatePipeOverUserStageRefuses() {
        engine.execute("CREATE TABLE target (id INTEGER)");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PIPE user_pipe AS COPY INTO target FROM @~");
            }
        });
        assertEquals("SQL compilation error: Stage: '~' cannot be a user stage in the pipe definition.",
            e.getMessage());
    }

    /** VALIDATION_MODE and FILES have no place in a pipe's COPY; a table stage is fine. */
    @Test
    public void testPipeOnlyCopyOptionsRefuse() {
        engine.execute("CREATE TABLE target (id INTEGER)");
        final RuntimeException validation = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(
                    "CREATE PIPE vm_pipe AS COPY INTO target FROM @ps VALIDATION_MODE = 'RETURN_ERRORS'");
            }
        });
        assertEquals("SQL compilation error: Invalid copy option 'VALIDATION_MODE' in pipe definition.",
            validation.getMessage());

        final RuntimeException files = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PIPE f_pipe AS COPY INTO target FROM @ps FILES = ('x.csv')");
            }
        });
        assertEquals("SQL compilation error: Invalid copy option 'FILES' in pipe definition.",
            files.getMessage());

        engine.execute("CREATE PIPE table_stage_pipe AS COPY INTO target FROM @%target");
        assertEquals(1, engine.executeQuery("SHOW PIPES LIKE 'table_stage_pipe'").getRowCount());
    }

    @Test
    public void testCreatePipeWithAutoIngest() {
        logger.info("Testing CREATE PIPE with AUTO_INGEST enabled");

        // A single VARIANT column: the pipe's COPY is compiled whole at CREATE on a real account,
        // so a JSON pipe must satisfy the one-column rule there and then.
        engine.execute("CREATE TABLE target_table (data VARIANT)");
        engine.execute("""
            CREATE PIPE auto_ingest_pipe
            AUTO_INGEST = TRUE
            AS COPY INTO target_table
            FROM @ps
            FILE_FORMAT = (TYPE = 'JSON')
            """);

        assertTrue(pipeStatus("auto_ingest_pipe").contains("RUNNING"));
        if (!isLiveSnowflake()) {
            assertTrue(catalogPipe("auto_ingest_pipe").isAutoIngest());
        }
    }

    @Test
    public void testCreatePipeWithNotificationChannel() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_HAS_NO_INTEGRATIONS);
        logger.info("Testing CREATE PIPE with AWS SNS notification channel");

        engine.execute("CREATE TABLE events (event_id INTEGER, event_data VARCHAR)");
        engine.execute("""
            CREATE PIPE notification_pipe
            AUTO_INGEST = TRUE
            AWS_SNS_TOPIC = 'arn:aws:sns:us-west-2:123456789012:my-topic'
            AS COPY INTO events
            FROM @ps
            FILE_FORMAT = (TYPE = 'JSON')
            """);

        final Pipe pipe = catalogPipe("notification_pipe");
        assertNotNull(pipe);
        assertTrue(pipe.isAutoIngest());
        assertEquals("arn:aws:sns:us-west-2:123456789012:my-topic", pipe.getAwsSnsTopicArn());
    }

    @Test
    public void testAlterPipePause() {
        logger.info("Testing ALTER PIPE ... SET PIPE_EXECUTION_PAUSED = TRUE");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE test_pipe AS COPY INTO target FROM @ps");

        engine.execute("ALTER PIPE test_pipe SET PIPE_EXECUTION_PAUSED = TRUE");

        assertTrue(pipeStatus("test_pipe").contains("PAUSED"));
        if (!isLiveSnowflake()) {
            assertTrue(catalogPipe("test_pipe").isPaused());
            assertEquals("PAUSED", catalogPipe("test_pipe").getStatus());
        }
    }

    @Test
    public void testAlterPipeResume() {
        logger.info("Testing ALTER PIPE ... SET PIPE_EXECUTION_PAUSED = FALSE");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE test_pipe AS COPY INTO target FROM @ps");

        engine.execute("ALTER PIPE test_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        engine.execute("ALTER PIPE test_pipe SET PIPE_EXECUTION_PAUSED = FALSE");

        assertTrue(pipeStatus("test_pipe").contains("RUNNING"));
        if (!isLiveSnowflake()) {
            assertFalse(catalogPipe("test_pipe").isPaused());
        }
    }

    @Test
    public void testAlterPipePauseResumeCycle() {
        logger.info("Testing multiple PAUSE/RESUME cycles");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE cycle_pipe AS COPY INTO target FROM @ps");

        engine.execute("ALTER PIPE cycle_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("cycle_pipe").contains("PAUSED"));

        engine.execute("ALTER PIPE cycle_pipe SET PIPE_EXECUTION_PAUSED = FALSE");
        assertTrue(pipeStatus("cycle_pipe").contains("RUNNING"));

        engine.execute("ALTER PIPE cycle_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("cycle_pipe").contains("PAUSED"));

        engine.execute("ALTER PIPE cycle_pipe SET PIPE_EXECUTION_PAUSED = FALSE");
        assertTrue(pipeStatus("cycle_pipe").contains("RUNNING"));
    }

    @Test
    public void testDropPipe() {
        logger.info("Testing DROP PIPE");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE test_pipe AS COPY INTO target FROM @ps");

        engine.execute("DROP PIPE test_pipe");

        assertEquals(0, engine.executeQuery("SHOW PIPES LIKE 'test_pipe'").getRowCount());
        if (!isLiveSnowflake()) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    catalogPipe("test_pipe");
                }
            });
        }
    }

    @Test
    public void testDropNonExistentPipe() {
        logger.info("Testing DROP PIPE on non-existent pipe");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP PIPE non_existent_pipe");
            }
        });
    }

    @Test
    public void testShowPipes() {
        logger.info("Testing SHOW PIPES");

        engine.execute("CREATE TABLE target1 (id INTEGER)");
        engine.execute("CREATE TABLE target2 (id INTEGER)");
        engine.execute("CREATE PIPE pipe1 AS COPY INTO target1 FROM @ps");
        engine.execute("""
            CREATE PIPE pipe2
            AUTO_INGEST = TRUE
            AS COPY INTO target2
            FROM @ps2
            """);

        assertEquals(2, engine.executeQuery("SHOW PIPES").getRowCount());
    }

    @Test
    public void testShowPipesEmpty() {
        logger.info("Testing SHOW PIPES with no pipes");

        assertEquals(0, engine.executeQuery("SHOW PIPES").getRowCount());
    }

    @Test
    public void testShowPipesAfterDrop() {
        logger.info("Testing SHOW PIPES after dropping a pipe");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE temp_pipe AS COPY INTO target FROM @ps");

        assertEquals(1, engine.executeQuery("SHOW PIPES").getRowCount());

        engine.execute("DROP PIPE temp_pipe");

        assertEquals(0, engine.executeQuery("SHOW PIPES").getRowCount());
    }

    @Test
    public void testDescribePipe() {
        logger.info("Testing DESCRIBE PIPE");

        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR)");
        engine.execute("""
            CREATE PIPE detailed_pipe
            AUTO_INGEST = TRUE
            AS COPY INTO target
            FROM @ps
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        final ResultSet result = engine.executeQuery("DESCRIBE PIPE detailed_pipe");
        assertNotNull(result);
        assertTrue(result.getRowCount() > 0);
    }

    @Test
    public void testDescribeNonExistentPipe() {
        logger.info("Testing DESCRIBE PIPE on non-existent pipe");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE PIPE non_existent_pipe");
            }
        });
    }

    @Test
    public void testCreatePipeDuplicateName() {
        logger.info("Testing CREATE PIPE with duplicate name");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE duplicate_pipe AS COPY INTO target FROM @ps");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PIPE duplicate_pipe AS COPY INTO target FROM @ps");
            }
        });
    }

    @Test
    public void testPipeWithComplexCopyStatement() {
        logger.info("Testing PIPE with complex COPY statement");

        engine.execute("""
            CREATE TABLE sales (
                sale_id INTEGER,
                product_name VARCHAR,
                quantity INTEGER,
                price DECIMAL(10,2),
                sale_date DATE
            )
            """);

        engine.execute("""
            CREATE PIPE complex_pipe
            AUTO_INGEST = TRUE
            AS COPY INTO sales (sale_id, product_name, quantity, price, sale_date)
            FROM @ps
            FILE_FORMAT = (
                TYPE = 'CSV',
                FIELD_DELIMITER = ',',
                SKIP_HEADER = 1,
                DATE_FORMAT = 'YYYY-MM-DD'
            )
            ON_ERROR = 'CONTINUE'
            """);

        assertTrue(pipeDefinition("complex_pipe").contains("COPY INTO"));
        if (!isLiveSnowflake()) {
            assertTrue(catalogPipe("complex_pipe").isAutoIngest());
            assertNotNull(catalogPipe("complex_pipe").getCopyStatement());
        }
    }

    @Test
    public void testPipeWithErrorIntegration() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_HAS_NO_INTEGRATIONS);
        logger.info("Testing PIPE with error integration");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE error_pipe
            AUTO_INGEST = TRUE
            ERROR_INTEGRATION = 'my_error_integration'
            AS COPY INTO target
            FROM @ps
            """);

        assertEquals("my_error_integration", catalogPipe("error_pipe").getErrorIntegration());
    }

    @Test
    public void testAlterNonExistentPipe() {
        logger.info("Testing ALTER PIPE on non-existent pipe");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER PIPE non_existent_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
            }
        });

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER PIPE non_existent_pipe SET PIPE_EXECUTION_PAUSED = FALSE");
            }
        });
    }

    @Test
    public void testPipeStatusTracking() {
        logger.info("Testing pipe status changes");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE status_pipe AS COPY INTO target FROM @ps");

        assertTrue(pipeStatus("status_pipe").contains("RUNNING"));

        engine.execute("ALTER PIPE status_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("status_pipe").contains("PAUSED"));

        engine.execute("ALTER PIPE status_pipe SET PIPE_EXECUTION_PAUSED = FALSE");
        assertTrue(pipeStatus("status_pipe").contains("RUNNING"));
    }

    @Test
    public void testMultiplePipesIndependence() {
        logger.info("Testing multiple pipes operate independently");

        engine.execute("CREATE TABLE target1 (id INTEGER)");
        engine.execute("CREATE TABLE target2 (id INTEGER)");
        engine.execute("CREATE PIPE pipe1 AS COPY INTO target1 FROM @ps");
        engine.execute("CREATE PIPE pipe2 AS COPY INTO target2 FROM @ps2");

        engine.execute("ALTER PIPE pipe1 SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("pipe1").contains("PAUSED"));
        assertTrue(pipeStatus("pipe2").contains("RUNNING"));

        engine.execute("ALTER PIPE pipe1 SET PIPE_EXECUTION_PAUSED = FALSE");
        engine.execute("ALTER PIPE pipe2 SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("pipe1").contains("RUNNING"));
        assertTrue(pipeStatus("pipe2").contains("PAUSED"));
    }

    @Test
    public void testPipeCaseInsensitivity() {
        logger.info("Testing pipe name case insensitivity");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE MyPipe AS COPY INTO target FROM @ps");

        assertEquals(1, engine.executeQuery("SHOW PIPES LIKE 'mypipe'").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW PIPES LIKE 'MYPIPE'").getRowCount());

        if (!isLiveSnowflake()) {
            assertNotNull(catalogPipe("MyPipe"));
            assertNotNull(catalogPipe("MYPIPE"));
            assertNotNull(catalogPipe("mypipe"));
        }
    }

    @Test
    public void testAlterPipeSetExecutionPaused() {
        logger.info("Testing ALTER PIPE ... SET PIPE_EXECUTION_PAUSED (real Snowflake syntax)");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE sep_pipe AS COPY INTO target FROM @ps");

        engine.execute("ALTER PIPE sep_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("sep_pipe").contains("PAUSED"));

        engine.execute("ALTER PIPE sep_pipe SET PIPE_EXECUTION_PAUSED = FALSE");
        assertTrue(pipeStatus("sep_pipe").contains("RUNNING"));
    }

    @Test
    public void testAlterPipeRefreshExecutesCopy() {
        Assumptions.assumeFalse(isLiveSnowflake(), PIPE_REFRESH_NEEDS_CLOUD_STORAGE);
        logger.info("Testing ALTER PIPE ... REFRESH triggers the pipe's COPY");

        // REFRESH runs the pipe's COPY INTO … FROM @stage. The engine's COPY is currently a simulation,
        // so this verifies the trigger wiring (no exception, pipe stays usable), not physical row loading.
        engine.execute("CREATE TABLE refresh_target (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STAGE refresh_stage URL='s3://bucket/data'");
        engine.execute("CREATE PIPE refresh_pipe AS COPY INTO refresh_target FROM @refresh_stage "
            + "FILE_FORMAT = (TYPE = 'CSV')");

        engine.execute("ALTER PIPE refresh_pipe REFRESH");

        assertTrue(pipeStatus("refresh_pipe").contains("RUNNING"));
    }

    @Test
    public void testSystemPipeStatusReflectsState() {
        logger.info("Testing SYSTEM$PIPE_STATUS reflects RUNNING/PAUSED");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE status_json_pipe AS COPY INTO target FROM @ps");

        assertTrue(pipeStatus("status_json_pipe").contains("RUNNING"));

        engine.execute("ALTER PIPE status_json_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(pipeStatus("status_json_pipe").contains("PAUSED"));
    }

    @Test
    public void testSystemPipeStatusUnknownPipeErrors() {
        logger.info("Testing SYSTEM$PIPE_STATUS on an unknown pipe errors (matches Snowflake)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SYSTEM$PIPE_STATUS('no_such_pipe')");
            }
        });
    }

    @Test
    public void testCreateOrReplacePipe() {
        logger.info("Testing CREATE OR REPLACE PIPE");

        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        engine.execute("CREATE PIPE or_replace_pipe AS COPY INTO t1 FROM @ps");
        engine.execute("CREATE OR REPLACE PIPE or_replace_pipe AUTO_INGEST = TRUE AS COPY INTO t2 FROM @ps2");

        assertTrue(pipeDefinition("or_replace_pipe").contains("t2"));
        if (!isLiveSnowflake()) {
            assertTrue(catalogPipe("or_replace_pipe").isAutoIngest());
        }
    }

    @Test
    public void testCreatePipeIfNotExists() {
        logger.info("Testing CREATE PIPE IF NOT EXISTS keeps the original definition");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE ine_pipe AS COPY INTO target FROM @ps");
        engine.execute("CREATE PIPE IF NOT EXISTS ine_pipe AS COPY INTO target FROM @ps2");

        assertTrue(pipeDefinition("ine_pipe").contains("@ps"));
        assertFalse(pipeDefinition("ine_pipe").contains("@ps2"));
    }

    @Test
    public void testDropPipeIfExists() {
        logger.info("Testing DROP PIPE IF EXISTS on a missing pipe does not throw");

        engine.execute("DROP PIPE IF EXISTS missing_pipe");
    }

    @Test
    public void testShowPipesLike() {
        logger.info("Testing SHOW PIPES LIKE filtering");

        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE PIPE load_alpha AS COPY INTO t FROM @ps");
        engine.execute("CREATE PIPE load_beta AS COPY INTO t FROM @ps");
        engine.execute("CREATE PIPE other_pipe AS COPY INTO t FROM @ps2");

        assertEquals(2, engine.executeQuery("SHOW PIPES LIKE 'load%'").getRowCount());
    }

    @Test
    public void testShowPipesColumnContent() {
        logger.info("Testing SHOW PIPES column contents (name / definition / kind / is_snowflake_managed)");

        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE PIPE content_pipe AUTO_INGEST = TRUE AS COPY INTO t FROM @ps");

        final ResultSet result = engine.executeQuery("SHOW PIPES LIKE 'content_pipe'");
        assertEquals(1, result.getRowCount());
        final Row pipe = result.getRows().get(0);
        assertEquals("content_pipe", pipe.getValue(result.getColumnIndex("name")).toString().toLowerCase());
        assertTrue(pipe.getValue(result.getColumnIndex("definition")).toString().contains("COPY INTO"));
        assertEquals("STAGE", pipe.getValue(result.getColumnIndex("kind")));
        assertEquals("false", String.valueOf(pipe.getValue(result.getColumnIndex("is_snowflake_managed"))));
    }

    @Test
    public void testCreatePipeWithIntegration() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_HAS_NO_INTEGRATIONS);
        logger.info("Testing CREATE PIPE with INTEGRATION (notification integration)");

        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE PIPE int_pipe AUTO_INGEST = TRUE INTEGRATION = 'my_notification_int' "
            + "AS COPY INTO t FROM @ps");

        assertEquals("my_notification_int", catalogPipe("int_pipe").getIntegration());
    }

    @Test
    public void testDescribePipeColumnarRow() {
        logger.info("Testing DESCRIBE PIPE returns a single columnar row (name / definition)");

        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE PIPE desc_pipe AS COPY INTO t FROM @ps");

        final ResultSet result = engine.executeQuery("DESCRIBE PIPE desc_pipe");
        assertEquals(1, result.getRowCount());
        // columns: created_on(0) name(1) database_name(2) schema_name(3) definition(4) ...
        assertEquals("desc_pipe", result.getRows().get(0).getValue(1).toString().toLowerCase());
        assertTrue(result.getRows().get(0).getValue(4).toString().contains("COPY INTO"));
    }
}
