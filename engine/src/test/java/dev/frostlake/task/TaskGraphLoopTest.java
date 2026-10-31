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

package dev.frostlake.task;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A task graph may close a loop through other tasks: every task of the loop then has a predecessor, so EXECUTE
 * TASK refuses each as a non-root task, while a task named as its own predecessor is still refused; a dropped task
 * leaves the predecessor lists it was in (all live-verified).
 */
public class TaskGraphLoopTest extends MeasuredCellsTestSupport {

    @Test
    public void aGraphMayCloseALoopThroughOtherTasks() {
        assertAnswer("Task A successfully created.", "CREATE TASK a SCHEDULE='60 MINUTE' AS SELECT 1");
        assertAnswer("Task B successfully created.", "CREATE TASK b AFTER a AS SELECT 2");
        assertAnswer("Task C successfully created.", "CREATE TASK c AFTER b AS SELECT 3");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER TASK a AFTER c AS SELECT 1");
        assertAnswer("A|[\"TEST_DB.TEST_SCHEMA.C\"] ;; B|[\"TEST_DB.TEST_SCHEMA.A\"] ;; C|[\"TEST_DB.TEST_SCHEMA.B\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.A', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("A|[\"TEST_DB.TEST_SCHEMA.C\"] ;; C|[\"TEST_DB.TEST_SCHEMA.B\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.C', RECURSIVE=>FALSE)) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Execute task cannot be called on non-root task TEST_DB.TEST_SCHEMA.A. Call EXECUTE TASK "
                + "on the root task of its graph instead.",
            "EXECUTE TASK a");
        assertAnswer("REFUSED Execute task cannot be called on non-root task TEST_DB.TEST_SCHEMA.B. Call EXECUTE TASK "
                + "on the root task of its graph instead.",
            "EXECUTE TASK b");
        assertAnswer("REFUSED Task B cannot use itself as its predecessor.", "ALTER TASK b ADD AFTER b");
        assertAnswer("REFUSED Invalid predecessor D was specified.", "CREATE OR REPLACE TASK d AFTER d AS SELECT 4");
        assertAnswer("Task B successfully created.", "CREATE OR REPLACE TASK b AFTER a AS SELECT 22");
        assertAnswer("A|[\"TEST_DB.TEST_SCHEMA.C\"] ;; B|[\"TEST_DB.TEST_SCHEMA.A\"] ;; C|[\"TEST_DB.TEST_SCHEMA.B\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.A', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("Task R successfully created.", "CREATE TASK r SCHEDULE='60 MINUTE' AS SELECT 0");
        assertAnswer("Task X successfully created.", "CREATE TASK x AFTER r AS SELECT 2");
        assertAnswer("Task Y successfully created.", "CREATE TASK y AFTER x AS SELECT 3");
        assertAnswer("Statement executed successfully.", "ALTER TASK x ADD AFTER y");
        assertAnswer("R|[] ;; X|[\"TEST_DB.TEST_SCHEMA.R\",\"TEST_DB.TEST_SCHEMA.Y\"] ;; Y|[\"TEST_DB.TEST_SCHEMA.X\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.R', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Execute task cannot be called on non-root task TEST_DB.TEST_SCHEMA.X. Call EXECUTE TASK "
                + "on the root task of its graph instead.",
            "EXECUTE TASK x");
        assertAnswer("Y successfully dropped.", "DROP TASK y");
        assertAnswer("R|[] ;; X|[\"TEST_DB.TEST_SCHEMA.R\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.R', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("Task M successfully created.", "CREATE TASK m SCHEDULE='60 MINUTE' AS SELECT 0");
        assertAnswer("Task N successfully created.", "CREATE TASK n AFTER m AS SELECT 1");
        assertAnswer("Task O successfully created.", "CREATE TASK o AFTER n AS SELECT 2");
        assertAnswer("N successfully dropped.", "DROP TASK n");
        assertAnswer("M|[]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.M', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("O|[]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.O', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("Task Z successfully created.", "CREATE TASK z AFTER r, o AS SELECT 4");
        assertAnswer("O|[] ;; Z|[\"TEST_DB.TEST_SCHEMA.O\",\"TEST_DB.TEST_SCHEMA.R\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.O', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
        assertAnswer("R successfully dropped.", "DROP TASK r");
        assertAnswer("O|[] ;; Z|[\"TEST_DB.TEST_SCHEMA.O\"]",
            "SELECT name, predecessors FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME=>'TEST_DB.TEST_SCHEMA.O', RECURSIVE=>TRUE)) "
                + "ORDER BY 1");
    }
}
