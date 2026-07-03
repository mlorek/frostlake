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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Demonstration of TASK suspend and resume functionality
 */
public class TaskSuspendResumeDemo extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskSuspendResumeDemo.class);

    @Test
    public void testTaskLifecycle() {
        logger.info("Creating a task...");
        engine.execute("""
            CREATE TASK my_scheduled_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS DELETE FROM logs WHERE age > 90
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("my_scheduled_task");

        // Initially, task is SUSPENDED
        logger.info("Initial state: {}", task.getState());
        assertEquals(TaskState.SUSPENDED, task.getState());

        // Resume the task
        logger.info("Resuming task...");
        engine.execute("ALTER TASK my_scheduled_task RESUME");
        task = schema.getTask("my_scheduled_task");
        logger.info("After RESUME: {}", task.getState());
        assertEquals(TaskState.STARTED, task.getState());

        // Suspend the task
        logger.info("Suspending task...");
        engine.execute("ALTER TASK my_scheduled_task SUSPEND");
        task = schema.getTask("my_scheduled_task");
        logger.info("After SUSPEND: {}", task.getState());
        assertEquals(TaskState.SUSPENDED, task.getState());

        // Resume again
        logger.info("Resuming task again...");
        engine.execute("ALTER TASK my_scheduled_task RESUME");
        task = schema.getTask("my_scheduled_task");
        logger.info("After second RESUME: {}", task.getState());
        assertEquals(TaskState.STARTED, task.getState());

        logger.info("Task lifecycle test completed successfully!");
    }

    @Test
    public void testMultipleTasksIndependentStates() {
        logger.info("Creating multiple tasks...");

        engine.execute("""
            CREATE TASK task_a
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '30 MINUTES'
            AS SELECT 1
            """);

        engine.execute("""
            CREATE TASK task_b
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '15 MINUTES'
            AS SELECT 2
            """);

        engine.execute("""
            CREATE TASK task_c
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '45 MINUTES'
            AS SELECT 3
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        // Resume task_a and task_c, leave task_b suspended
        logger.info("Resuming task_a and task_c...");
        engine.execute("ALTER TASK task_a RESUME");
        engine.execute("ALTER TASK task_c RESUME");

        Task taskA = schema.getTask("task_a");
        Task taskB = schema.getTask("task_b");
        Task taskC = schema.getTask("task_c");

        logger.info("task_a state: {}", taskA.getState());
        logger.info("task_b state: {}", taskB.getState());
        logger.info("task_c state: {}", taskC.getState());

        assertEquals(TaskState.STARTED, taskA.getState());
        assertEquals(TaskState.SUSPENDED, taskB.getState());
        assertEquals(TaskState.STARTED, taskC.getState());

        // Now suspend task_a and resume task_b
        logger.info("Changing states: suspending task_a, resuming task_b...");
        engine.execute("ALTER TASK task_a SUSPEND");
        engine.execute("ALTER TASK task_b RESUME");

        taskA = schema.getTask("task_a");
        taskB = schema.getTask("task_b");
        taskC = schema.getTask("task_c");

        logger.info("After changes - task_a state: {}", taskA.getState());
        logger.info("After changes - task_b state: {}", taskB.getState());
        logger.info("After changes - task_c state: {}", taskC.getState());

        assertEquals(TaskState.SUSPENDED, taskA.getState());
        assertEquals(TaskState.STARTED, taskB.getState());
        assertEquals(TaskState.STARTED, taskC.getState());

        logger.info("Multiple tasks test completed successfully!");
    }

    @Test
    public void testSuspendAndResumeWithExecution() {
        logger.info("Creating table and task for execution test...");

        engine.execute("CREATE TABLE task_executions (executed_at VARCHAR, task_name VARCHAR)");

        engine.execute("""
            CREATE TASK execution_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS INSERT INTO task_executions VALUES ('2024-01-01', 'execution_task')
            """);

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Task task = schema.getTask("execution_task");

        // Task starts suspended
        assertEquals(TaskState.SUSPENDED, task.getState());
        logger.info("Task created in SUSPENDED state");

        // Resume to allow execution
        engine.execute("ALTER TASK execution_task RESUME");
        task = schema.getTask("execution_task");
        assertEquals(TaskState.STARTED, task.getState());
        logger.info("Task RESUMED - ready to execute");

        // Execute the task
        String qualifiedName = "test_db.test_schema.execution_task";
        engine.getTaskScheduler().executeTaskNow(qualifiedName, task);
        logger.info("Task executed");

        // Verify task still in STARTED state after execution
        task = schema.getTask("execution_task");
        assertEquals(TaskState.STARTED, task.getState());
        assertEquals(1, task.getExecutionHistory().size());
        logger.info("Task remains STARTED after execution, history size: {}", task.getExecutionHistory().size());

        // Suspend to prevent further executions
        engine.execute("ALTER TASK execution_task SUSPEND");
        task = schema.getTask("execution_task");
        assertEquals(TaskState.SUSPENDED, task.getState());
        logger.info("Task SUSPENDED - will not execute on schedule");

        logger.info("Execution test completed successfully!");
    }
}
