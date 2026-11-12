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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * SYSTEM$TASK_DEPENDENTS_ENABLE "Recursively resumes a specified task and all its dependent tasks" (documented): the
 * task its name resolves to, in the schema the name gives, and that task's dependents there - not the same-named tasks
 * of the session's schema, and the named task too, whether or not it is a root. Engine only: resuming tasks on the live
 * account starts them on its warehouse.
 */
public class TaskDependentsEnableScopeTest extends BaseDatabaseTest {

    private String states(final String schema) {
        final ResultSet rs = engine.executeQuery("SHOW TASKS IN SCHEMA " + schema);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getRows().size(); i++) {
            out.append(i > 0 ? "," : "").append(cell(rs, rs.getRows().get(i), "name")).append('=')
                .append(cell(rs, rs.getRows().get(i), "state"));
        }
        return out.toString();
    }

    @Test
    public void theNamedSchemasGraphIsResumedWithItsNamedTask() {
        assumeFalse(isLiveSnowflake(), "resuming tasks starts them on the live account's warehouse");
        for (final String schema : new String[] {"tde_a", "tde_b"}) {
            engine.execute("CREATE SCHEMA " + schema);
            engine.execute("CREATE TASK " + schema + ".r SCHEDULE = '60 MINUTE' AS SELECT 1");
            engine.execute("CREATE TASK " + schema + ".c AFTER " + schema + ".r AS SELECT 2");
        }
        engine.execute("USE SCHEMA tde_b");
        try {
            engine.executeQuery("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('test_db.tde_a.r')");
            assertEquals("C=started,R=started", states("test_db.tde_a"));
            assertEquals("C=suspended,R=suspended", states("test_db.tde_b"));
        } finally {
            engine.execute("ALTER TASK test_db.tde_a.r SUSPEND");
        }
    }

    @Test
    public void aChildNamedIsResumedWithWhatFollowsIt() {
        assumeFalse(isLiveSnowflake(), "resuming tasks starts them on the live account's warehouse");
        engine.execute("CREATE TASK tde_root SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK tde_mid AFTER tde_root AS SELECT 2");
        engine.execute("CREATE TASK tde_leaf AFTER tde_mid AS SELECT 3");
        engine.executeQuery("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('tde_mid')");
        assertEquals("TDE_LEAF=started,TDE_MID=started,TDE_ROOT=suspended", states("test_db.test_schema"));
    }
}
