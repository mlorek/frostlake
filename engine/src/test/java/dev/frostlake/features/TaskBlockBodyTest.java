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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task's body may be a whole Snowflake Scripting block, not only a single statement — live-verified
 * for a plain {@code BEGIN … END}, one with its own DECLARE section, one nesting another block, and
 * one running DML. The block is written UNQUOTED, exactly like the unquoted procedure body: a
 * {@code $$}-quoted body is a syntax error on the account, at the dollar-quoted token.
 *
 * <p>The block obeys the ordinary scripting rules once it is a body — a statement inside it that
 * lacks its terminating semicolon is refused, the same as anywhere else (see
 * {@code ScriptingSemicolonTerminationTest}).
 */
public class TaskBlockBodyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE task_target (n INT)");
    }

    /** One SHOW TASKS cell for a task created in this test's schema. */
    private String taskCell(final String name, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + name + "'");
        return cell(tasks, soleRowWhere(tasks, "name", name.toUpperCase()), column);
    }

    private void assertRefused(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(ex.getMessage()).contains("syntax error"),
            "expected a syntax error, got: " + ex.getMessage());
    }

    @Test
    public void aTaskBodyMayBeAScriptingBlock() {
        engine.execute("""
            CREATE TASK block_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS
            BEGIN
                INSERT INTO task_target VALUES (1);
            END;
            """);

        assertTrue(taskCell("block_task", "definition").contains("BEGIN"),
            "the block body should read back as the task's definition");
    }

    @Test
    public void aTaskBodyMayCarryItsOwnDeclareSection() {
        engine.execute("""
            CREATE TASK declare_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS
            DECLARE
                n INT;
            BEGIN
                n := 1;
                INSERT INTO task_target VALUES (:n);
            END;
            """);

        assertTrue(taskCell("declare_task", "definition").contains("DECLARE"),
            "the DECLARE section belongs to the stored body");
    }

    @Test
    public void aTaskBodyMayNestBlocks() {
        engine.execute("""
            CREATE TASK nested_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS
            BEGIN
                BEGIN
                    INSERT INTO task_target VALUES (2);
                END;
            END;
            """);

        assertTrue(taskCell("nested_task", "definition").contains("BEGIN"),
            "a nested block is part of the body like any other statement");
    }

    /** Tasks take their block RAW: the dollar-quoted spelling a procedure uses is a syntax error here. */
    @Test
    public void aTaskBodyMayNotBeDollarQuoted() {
        assertRefused("""
            CREATE TASK quoted_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS $$
            BEGIN
                INSERT INTO task_target VALUES (3);
            END;
            $$
            """);
    }

    @Test
    public void aTaskBlockObeysTheStatementTerminatorRule() {
        assertRefused("""
            CREATE TASK unterminated_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '60 MINUTES'
            AS
            BEGIN
                INSERT INTO task_target VALUES (4)
            END;
            """);
    }
}
