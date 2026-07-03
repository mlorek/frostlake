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
 * Tests for PIPE feature - continuous data loading
 */
public class PipesTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(PipesTest.class);

    @Test
    public void testCreateSimplePipe() {
        logger.info("Testing CREATE PIPE with basic configuration");

        engine.execute("CREATE TABLE target_table (id INTEGER, name VARCHAR, value DECIMAL(10,2))");

        engine.execute("""
            CREATE PIPE my_pipe
            AS COPY INTO target_table
            FROM @my_stage
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("my_pipe");

        assertNotNull(pipe);
        assertEquals("my_pipe", pipe.getName().toLowerCase());
        assertFalse(pipe.isAutoIngest());
        assertFalse(pipe.isPaused());
        assertEquals("RUNNING", pipe.getStatus());
    }

    @Test
    public void testCreatePipeWithAutoIngest() {
        logger.info("Testing CREATE PIPE with AUTO_INGEST enabled");

        engine.execute("CREATE TABLE target_table (id INTEGER, data VARCHAR)");

        engine.execute("""
            CREATE PIPE auto_ingest_pipe
            AUTO_INGEST = TRUE
            AS COPY INTO target_table
            FROM @s3_stage
            FILE_FORMAT = (TYPE = 'JSON')
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("auto_ingest_pipe");

        assertNotNull(pipe);
        assertTrue(pipe.isAutoIngest());
        assertEquals("RUNNING", pipe.getStatus());
    }

    @Test
    public void testCreatePipeWithNotificationChannel() {
        logger.info("Testing CREATE PIPE with AWS SNS notification channel");

        engine.execute("CREATE TABLE events (event_id INTEGER, event_data VARCHAR)");

        engine.execute("""
            CREATE PIPE notification_pipe
            AUTO_INGEST = TRUE
            AWS_SNS_TOPIC = 'arn:aws:sns:us-west-2:123456789012:my-topic'
            AS COPY INTO events
            FROM @s3_events_stage
            FILE_FORMAT = (TYPE = 'JSON')
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("notification_pipe");

        assertNotNull(pipe);
        assertTrue(pipe.isAutoIngest());
        assertNotNull(pipe.getAwsSnsTopicArn());
        assertEquals("arn:aws:sns:us-west-2:123456789012:my-topic", pipe.getAwsSnsTopicArn());
    }

    @Test
    public void testAlterPipePause() {
        logger.info("Testing ALTER PIPE ... PAUSE");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE test_pipe
            AS COPY INTO target
            FROM @stage
            """);

        engine.execute("ALTER PIPE test_pipe PAUSE");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("test_pipe");

        assertTrue(pipe.isPaused());
        assertEquals("PAUSED", pipe.getStatus());
    }

    @Test
    public void testAlterPipeResume() {
        logger.info("Testing ALTER PIPE ... RESUME");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE test_pipe
            AS COPY INTO target
            FROM @stage
            """);

        engine.execute("ALTER PIPE test_pipe PAUSE");
        engine.execute("ALTER PIPE test_pipe RESUME");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("test_pipe");

        assertFalse(pipe.isPaused());
        assertEquals("RUNNING", pipe.getStatus());
    }

    @Test
    public void testAlterPipePauseResumeCycle() {
        logger.info("Testing multiple PAUSE/RESUME cycles");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE cycle_pipe
            AS COPY INTO target
            FROM @stage
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        // Pause
        engine.execute("ALTER PIPE cycle_pipe PAUSE");
        assertTrue(schema.getPipe("cycle_pipe").isPaused());

        // Resume
        engine.execute("ALTER PIPE cycle_pipe RESUME");
        assertFalse(schema.getPipe("cycle_pipe").isPaused());

        // Pause again
        engine.execute("ALTER PIPE cycle_pipe PAUSE");
        assertTrue(schema.getPipe("cycle_pipe").isPaused());

        // Resume again
        engine.execute("ALTER PIPE cycle_pipe RESUME");
        assertFalse(schema.getPipe("cycle_pipe").isPaused());
    }

    @Test
    public void testDropPipe() {
        logger.info("Testing DROP PIPE");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE test_pipe
            AS COPY INTO target
            FROM @stage
            """);

        engine.execute("DROP PIPE test_pipe");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        assertThrows(RuntimeException.class, () -> {
            schema.getPipe("test_pipe");
        });
    }

    @Test
    public void testDropNonExistentPipe() {
        logger.info("Testing DROP PIPE on non-existent pipe");

        assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP PIPE non_existent_pipe");
        });
    }

    @Test
    public void testShowPipes() {
        logger.info("Testing SHOW PIPES");

        engine.execute("CREATE TABLE target1 (id INTEGER)");
        engine.execute("CREATE TABLE target2 (id INTEGER)");

        engine.execute("""
            CREATE PIPE pipe1
            AS COPY INTO target1
            FROM @stage1
            """);

        engine.execute("""
            CREATE PIPE pipe2
            AUTO_INGEST = TRUE
            AS COPY INTO target2
            FROM @stage2
            """);

        ResultSet result = engine.executeQuery("SHOW PIPES");

        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testShowPipesEmpty() {
        logger.info("Testing SHOW PIPES with no pipes");

        ResultSet result = engine.executeQuery("SHOW PIPES");

        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testShowPipesAfterDrop() {
        logger.info("Testing SHOW PIPES after dropping a pipe");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE temp_pipe
            AS COPY INTO target
            FROM @stage
            """);

        ResultSet result1 = engine.executeQuery("SHOW PIPES");
        assertEquals(1, result1.getRowCount());

        engine.execute("DROP PIPE temp_pipe");

        ResultSet result2 = engine.executeQuery("SHOW PIPES");
        assertEquals(0, result2.getRowCount());
    }

    @Test
    public void testDescribePipe() {
        logger.info("Testing DESCRIBE PIPE");

        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR)");
        engine.execute("""
            CREATE PIPE detailed_pipe
            AUTO_INGEST = TRUE
            AS COPY INTO target
            FROM @s3_stage
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        ResultSet result = engine.executeQuery("DESCRIBE PIPE detailed_pipe");

        assertNotNull(result);
        assertTrue(result.getRowCount() > 0);
    }

    @Test
    public void testDescribeNonExistentPipe() {
        logger.info("Testing DESCRIBE PIPE on non-existent pipe");

        assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("DESCRIBE PIPE non_existent_pipe");
        });
    }

    @Test
    public void testCreatePipeDuplicateName() {
        logger.info("Testing CREATE PIPE with duplicate name");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE duplicate_pipe
            AS COPY INTO target
            FROM @stage
            """);

        assertThrows(RuntimeException.class, () -> {
            engine.execute("""
                CREATE PIPE duplicate_pipe
                AS COPY INTO target
                FROM @stage
                """);
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
            FROM @s3_sales_stage
            FILE_FORMAT = (
                TYPE = 'CSV',
                FIELD_DELIMITER = ',',
                SKIP_HEADER = 1,
                DATE_FORMAT = 'YYYY-MM-DD'
            )
            ON_ERROR = 'CONTINUE'
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("complex_pipe");

        assertNotNull(pipe);
        assertTrue(pipe.isAutoIngest());
        assertNotNull(pipe.getCopyStatement());
    }

    @Test
    public void testPipeWithErrorIntegration() {
        logger.info("Testing PIPE with error integration");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE error_pipe
            AUTO_INGEST = TRUE
            ERROR_INTEGRATION = 'my_error_integration'
            AS COPY INTO target
            FROM @stage
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("error_pipe");

        assertNotNull(pipe);
        assertNotNull(pipe.getErrorIntegration());
        assertEquals("my_error_integration", pipe.getErrorIntegration());
    }

    @Test
    public void testAlterNonExistentPipe() {
        logger.info("Testing ALTER PIPE on non-existent pipe");

        assertThrows(RuntimeException.class, () -> {
            engine.execute("ALTER PIPE non_existent_pipe PAUSE");
        });

        assertThrows(RuntimeException.class, () -> {
            engine.execute("ALTER PIPE non_existent_pipe RESUME");
        });
    }

    @Test
    public void testPipeStatusTracking() {
        logger.info("Testing pipe status changes");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE status_pipe
            AS COPY INTO target
            FROM @stage
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("status_pipe");

        // Initial status should be RUNNING
        assertEquals("RUNNING", pipe.getStatus());
        assertFalse(pipe.isPaused());

        // After pause
        engine.execute("ALTER PIPE status_pipe PAUSE");
        pipe = schema.getPipe("status_pipe");
        assertEquals("PAUSED", pipe.getStatus());
        assertTrue(pipe.isPaused());

        // After resume
        engine.execute("ALTER PIPE status_pipe RESUME");
        pipe = schema.getPipe("status_pipe");
        assertEquals("RUNNING", pipe.getStatus());
        assertFalse(pipe.isPaused());
    }

    @Test
    public void testMultiplePipesIndependence() {
        logger.info("Testing multiple pipes operate independently");

        engine.execute("CREATE TABLE target1 (id INTEGER)");
        engine.execute("CREATE TABLE target2 (id INTEGER)");

        engine.execute("""
            CREATE PIPE pipe1
            AS COPY INTO target1
            FROM @stage1
            """);

        engine.execute("""
            CREATE PIPE pipe2
            AS COPY INTO target2
            FROM @stage2
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        // Pause pipe1
        engine.execute("ALTER PIPE pipe1 PAUSE");

        Pipe pipe1 = schema.getPipe("pipe1");
        Pipe pipe2 = schema.getPipe("pipe2");

        assertTrue(pipe1.isPaused());
        assertFalse(pipe2.isPaused());

        // Resume pipe1 and pause pipe2
        engine.execute("ALTER PIPE pipe1 RESUME");
        engine.execute("ALTER PIPE pipe2 PAUSE");

        pipe1 = schema.getPipe("pipe1");
        pipe2 = schema.getPipe("pipe2");

        assertFalse(pipe1.isPaused());
        assertTrue(pipe2.isPaused());
    }

    @Test
    public void testPipeCaseInsensitivity() {
        logger.info("Testing pipe name case insensitivity");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("""
            CREATE PIPE MyPipe
            AS COPY INTO target
            FROM @stage
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        // Should be accessible with different case variations
        Pipe pipe1 = schema.getPipe("MyPipe");
        Pipe pipe2 = schema.getPipe("MYPIPE");
        Pipe pipe3 = schema.getPipe("mypipe");

        assertNotNull(pipe1);
        assertNotNull(pipe2);
        assertNotNull(pipe3);

        // All should reference the same pipe
        assertEquals(pipe1.getName().toUpperCase(), pipe2.getName().toUpperCase());
        assertEquals(pipe2.getName().toUpperCase(), pipe3.getName().toUpperCase());
    }

    @Test
    public void testAlterPipeSetExecutionPaused() {
        logger.info("Testing ALTER PIPE ... SET PIPE_EXECUTION_PAUSED (real Snowflake syntax)");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE sep_pipe AS COPY INTO target FROM @stage");
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        engine.execute("ALTER PIPE sep_pipe SET PIPE_EXECUTION_PAUSED = TRUE");
        assertTrue(schema.getPipe("sep_pipe").isPaused());

        engine.execute("ALTER PIPE sep_pipe SET PIPE_EXECUTION_PAUSED = FALSE");
        assertFalse(schema.getPipe("sep_pipe").isPaused());
    }

    @Test
    public void testAlterPipeRefreshExecutesCopy() {
        logger.info("Testing ALTER PIPE ... REFRESH triggers the pipe's COPY");

        // REFRESH runs the pipe's COPY INTO … FROM @stage. The engine's COPY is currently a simulation,
        // so this verifies the trigger wiring (no exception, pipe stays usable), not physical row loading.
        engine.execute("CREATE TABLE refresh_target (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STAGE refresh_stage URL='s3://bucket/data'");
        engine.execute("CREATE PIPE refresh_pipe AS COPY INTO refresh_target FROM @refresh_stage "
            + "FILE_FORMAT = (TYPE = 'CSV')");

        engine.execute("ALTER PIPE refresh_pipe REFRESH");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        assertEquals("RUNNING", schema.getPipe("refresh_pipe").getStatus());
    }

    @Test
    public void testSystemPipeStatusReflectsState() {
        logger.info("Testing SYSTEM$PIPE_STATUS reflects RUNNING/PAUSED");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE status_json_pipe AS COPY INTO target FROM @stage");

        ResultSet running = engine.executeQuery("SELECT SYSTEM$PIPE_STATUS('status_json_pipe')");
        assertTrue(running.getRows().get(0).getValue(0).toString().contains("RUNNING"));

        engine.execute("ALTER PIPE status_json_pipe PAUSE");
        ResultSet paused = engine.executeQuery("SELECT SYSTEM$PIPE_STATUS('status_json_pipe')");
        assertTrue(paused.getRows().get(0).getValue(0).toString().contains("PAUSED"));
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
        engine.execute("CREATE PIPE or_replace_pipe AS COPY INTO t1 FROM @stage1");
        engine.execute("CREATE OR REPLACE PIPE or_replace_pipe AUTO_INGEST = TRUE AS COPY INTO t2 FROM @stage2");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Pipe pipe = schema.getPipe("or_replace_pipe");
        assertTrue(pipe.isAutoIngest());
        assertTrue(pipe.getCopyStatement().contains("t2"));
    }

    @Test
    public void testCreatePipeIfNotExists() {
        logger.info("Testing CREATE PIPE IF NOT EXISTS keeps the original definition");

        engine.execute("CREATE TABLE target (id INTEGER)");
        engine.execute("CREATE PIPE ine_pipe AS COPY INTO target FROM @stage_a");
        engine.execute("CREATE PIPE IF NOT EXISTS ine_pipe AS COPY INTO target FROM @stage_b");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        assertTrue(schema.getPipe("ine_pipe").getCopyStatement().contains("stage_a"));
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
        engine.execute("CREATE PIPE load_alpha AS COPY INTO t FROM @s1");
        engine.execute("CREATE PIPE load_beta AS COPY INTO t FROM @s2");
        engine.execute("CREATE PIPE other_pipe AS COPY INTO t FROM @s3");

        ResultSet result = engine.executeQuery("SHOW PIPES LIKE 'load%'");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testShowPipesColumnContent() {
        logger.info("Testing SHOW PIPES column contents (name / definition / auto_ingest / status)");

        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE PIPE content_pipe AUTO_INGEST = TRUE AS COPY INTO t FROM @s");

        ResultSet result = engine.executeQuery("SHOW PIPES LIKE 'content_pipe'");
        assertEquals(1, result.getRowCount());
        // columns: created_on(0) name(1) database_name(2) schema_name(3) owner(4) comment(5)
        // notification_channel(6) definition(7) auto_ingest(8) integration(9) error_integration(10)
        // aws_sns_topic_arn(11) status(12)
        assertEquals("content_pipe", result.getRows().get(0).getValue(1).toString().toLowerCase());
        assertTrue(result.getRows().get(0).getValue(7).toString().contains("COPY INTO"));
        assertEquals("true", result.getRows().get(0).getValue(8).toString());
        assertEquals("RUNNING", result.getRows().get(0).getValue(12).toString());
    }

    @Test
    public void testCreatePipeWithIntegration() {
        logger.info("Testing CREATE PIPE with INTEGRATION (notification integration)");

        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("CREATE PIPE int_pipe AUTO_INGEST = TRUE INTEGRATION = 'my_notification_int' "
            + "AS COPY INTO t FROM @s");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        assertEquals("my_notification_int", schema.getPipe("int_pipe").getIntegration());
    }

    @Test
    public void testDescribePipeColumnarRow() {
        logger.info("Testing DESCRIBE PIPE returns a single columnar row (name / definition)");

        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE PIPE desc_pipe AS COPY INTO t FROM @s");

        ResultSet result = engine.executeQuery("DESCRIBE PIPE desc_pipe");
        assertEquals(1, result.getRowCount());
        // columns: created_on(0) name(1) database_name(2) schema_name(3) definition(4) ...
        assertEquals("desc_pipe", result.getRows().get(0).getValue(1).toString().toLowerCase());
        assertTrue(result.getRows().get(0).getValue(4).toString().contains("COPY INTO"));
    }
}
