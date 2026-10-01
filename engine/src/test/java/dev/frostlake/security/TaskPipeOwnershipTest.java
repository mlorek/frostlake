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

package dev.frostlake.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GRANT OWNERSHIP moves a task, one at a time or every task of a schema at once, and SHOW TASKS names the new owner.
 * A pipe that is running keeps its owner: the move is refused until the pipe is paused.
 */
public class TaskPipeOwnershipTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS tp_own");
        engine.execute("CREATE ROLE IF NOT EXISTS tp_mid");
    }

    @Override
    protected void teardownTest() {
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE tp_own TO ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE tp_mid TO ROLE ACCOUNTADMIN");
        quietly("DROP ROLE IF EXISTS tp_mid");
        quietly("DROP ROLE IF EXISTS tp_own");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    /** Each task SHOW TASKS lists in the schema, as name and owner, by name. */
    private List<String> taskOwners() {
        final ResultSet shown = engine.executeQuery("SHOW TASKS IN SCHEMA test_schema");
        final List<String> owners = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            owners.add(row.getValue(shown.getColumnIndex("name")) + " " + row.getValue(shown.getColumnIndex("owner")));
        }
        Collections.sort(owners);
        return owners;
    }

    @Test
    public void aTaskMovesAloneOrWithEveryTaskOfItsSchema() {
        engine.execute("CREATE TASK tk SCHEDULE = '5 MINUTE' AS SELECT 1");
        engine.execute("GRANT OWNERSHIP ON TASK tk TO ROLE tp_own COPY CURRENT GRANTS");
        assertEquals(List.of("TK TP_OWN"), taskOwners());
        engine.execute("GRANT ROLE tp_own TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE TASK tk2 SCHEDULE = '5 MINUTE' AS SELECT 1");
        engine.execute("GRANT OWNERSHIP ON ALL TASKS IN SCHEMA test_schema TO ROLE tp_mid COPY CURRENT GRANTS");
        // A listing shows what the session holds a privilege on, so the new owner is held before looking.
        engine.execute("GRANT ROLE tp_mid TO ROLE ACCOUNTADMIN");
        assertEquals(List.of("TK TP_MID", "TK2 TP_MID"), taskOwners());
    }

    @Test
    public void aRunningPipeKeepsItsOwner() {
        engine.execute("CREATE STAGE pst");
        engine.execute("CREATE TABLE pt (a INT)");
        engine.execute("CREATE PIPE pp AS COPY INTO pt FROM @pst");
        final String refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT OWNERSHIP ON PIPE pp TO ROLE tp_own COPY CURRENT GRANTS");
            }
        }).getMessage();
        assertEquals("SQL execution error:\nPipe PP not in paused state. To pause pipe run ALTER PIPE PP SET "
            + "PIPE_EXECUTION_PAUSED=true", refused);
        final ResultSet shown = engine.executeQuery("SHOW PIPES LIKE 'PP'");
        assertEquals("ACCOUNTADMIN", String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("owner"))));
    }
}
