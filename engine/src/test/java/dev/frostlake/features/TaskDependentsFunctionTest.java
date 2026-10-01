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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** INFORMATION_SCHEMA.TASK_DEPENDENTS: the named task first, then its children, recursively by default. */
public class TaskDependentsFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TASK dep_root AS SELECT 1");
        engine.execute("CREATE TASK dep_child AFTER dep_root AS SELECT 2");
        engine.execute("CREATE TASK dep_grandchild AFTER dep_child AS SELECT 3");
        engine.execute("CREATE TASK dep_other AS SELECT 4");
    }

    private String names(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(rs.getRows().get(i).getValue(rs.getColumnIndex("name")));
        }
        return out.toString();
    }

    @Test
    public void theTaskComesFirstThenEveryDescendant() {
        assertEquals("DEP_ROOT,DEP_CHILD,DEP_GRANDCHILD", names(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'test_db.test_schema.dep_root'))"));
    }

    @Test
    public void recursiveFalseKeepsTheDirectChildren() {
        assertEquals("DEP_ROOT,DEP_CHILD", names("SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS("
            + "TASK_NAME => 'dep_root', RECURSIVE => FALSE))"));
        assertEquals("DEP_OTHER", names(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'dep_other'))"));
    }

    @Test
    public void theRowsCarryTheShowTasksColumnsAndQualifiedPredecessors() {
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TASK_DEPENDENTS("
            + "TASK_NAME => 'dep_child'))");
        assertEquals(12, rs.getColumns().size());
        assertEquals("CREATED_ON", rs.getColumns().get(0).getName());
        assertEquals("CONDITION", rs.getColumns().get(11).getName());
        final String predecessors = String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("predecessors")));
        assertTrue(predecessors.contains("TEST_DB.TEST_SCHEMA.DEP_ROOT"), predecessors);
        assertEquals("suspended", rs.getRows().get(0).getValue(rs.getColumnIndex("state")));
    }

    @Test
    public void aMissingTaskIsRefused() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nope'))");
            }
        });
        assertTrue(refused.getMessage().contains("Invalid value [nope] for function 'TASK_DEPENDENTS_SCAN', "
            + "parameter 1: must be a valid task name"), refused.getMessage());
    }

    @Test
    public void theBareNameIsNoFunction() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(TASK_DEPENDENTS(TASK_NAME => 'dep_root'))");
            }
        });
    }
}
