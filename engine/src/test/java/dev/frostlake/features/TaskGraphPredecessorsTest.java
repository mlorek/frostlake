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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * "A task runs after all of its predecessor tasks have finished their own runs successfully", and "all tasks in a task
 * graph must exist in the same schema" (documented, CREATE TASK … AFTER): a child of several predecessors waits for
 * all of them and does not run when one fails, and a graph run runs no task of another schema, nor one that follows a
 * task of the same name in another letter case. Engine only: EXECUTE TASK's run is asynchronous on the live account.
 */
public class TaskGraphPredecessorsTest extends BaseDatabaseTest {

    @BeforeEach
    public void engineOnly() {
        assumeFalse(isLiveSnowflake(), "EXECUTE TASK runs asynchronously on the live account");
        engine.execute("CREATE TABLE tgp_log (n STRING, ts NUMBER AUTOINCREMENT)");
    }

    private String logged() {
        return String.valueOf(engine.executeQuery("SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY ts) FROM tgp_log")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void aChildOfTwoWaitsForBothAndNotForAFailure() {
        engine.execute("CREATE TASK tgp_r SCHEDULE = '60 MINUTE' AS INSERT INTO tgp_log (n) VALUES ('r')");
        engine.execute("CREATE TASK tgp_a AFTER tgp_r AS INSERT INTO tgp_log (n) VALUES ('a')");
        engine.execute("CREATE TASK tgp_b AFTER tgp_r AS INSERT INTO tgp_log (n) SELECT 'b' || (1/0)::STRING");
        engine.execute("CREATE TASK tgp_c AFTER tgp_a, tgp_b AS INSERT INTO tgp_log (n) VALUES ('c')");
        for (final String task : new String[] {"tgp_a", "tgp_b", "tgp_c"}) {
            engine.execute("ALTER TASK " + task + " RESUME");
        }
        engine.execute("EXECUTE TASK tgp_r");
        assertEquals("r,a", logged());
        // Once the failing predecessor succeeds, the child runs after both.
        for (final String task : new String[] {"tgp_a", "tgp_b", "tgp_c"}) {
            engine.execute("ALTER TASK " + task + " SUSPEND");
        }
        engine.execute("CREATE OR REPLACE TASK tgp_b AFTER tgp_r AS INSERT INTO tgp_log (n) VALUES ('b')");
        for (final String task : new String[] {"tgp_a", "tgp_b", "tgp_c"}) {
            engine.execute("ALTER TASK " + task + " RESUME");
        }
        engine.execute("TRUNCATE TABLE tgp_log");
        engine.execute("EXECUTE TASK tgp_r");
        // A and B run in either order; C runs once, after both.
        assertEquals("a,b,c,r", String.valueOf(engine.executeQuery("SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) "
            + "FROM tgp_log").getRows().get(0).getValue(0)));
        assertEquals("c", String.valueOf(engine.executeQuery("SELECT n FROM tgp_log ORDER BY ts DESC LIMIT 1")
            .getRows().get(0).getValue(0)));
    }

    @Test
    public void aGraphRunRunsNoTaskOfAnotherSchema() {
        engine.execute("CREATE SCHEMA tgp_s2");
        // CREATE SCHEMA makes the new schema the session's; the root graph is the test schema's.
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TASK tgp_r SCHEDULE = '60 MINUTE' AS INSERT INTO test_db.test_schema.tgp_log (n) "
            + "VALUES ('r')");
        engine.execute("CREATE TASK tgp_b AFTER tgp_r AS INSERT INTO test_db.test_schema.tgp_log (n) VALUES ('b')");
        engine.execute("CREATE TASK tgp_s2.r2 SCHEDULE = '60 MINUTE' AS INSERT INTO test_db.test_schema.tgp_log (n) "
            + "VALUES ('s2.r2')");
        engine.execute("CREATE TASK tgp_s2.tgp_b AFTER tgp_s2.r2 AS INSERT INTO test_db.test_schema.tgp_log (n) "
            + "VALUES ('s2.b')");
        engine.execute("CREATE TASK tgp_s2.x AFTER tgp_s2.tgp_b AS INSERT INTO test_db.test_schema.tgp_log (n) "
            + "VALUES ('s2.x')");
        engine.execute("ALTER TASK tgp_b RESUME");
        engine.execute("ALTER TASK tgp_s2.x RESUME");
        engine.execute("EXECUTE TASK tgp_r");
        assertEquals("r,b", logged());
    }

    @Test
    public void aTaskFollowsOnlyThePredecessorItNamesExactly() {
        engine.execute("CREATE TASK tgp_r SCHEDULE = '60 MINUTE' AS INSERT INTO tgp_log (n) VALUES ('r')");
        engine.execute("CREATE TASK \"tgp_q\" AFTER tgp_r AS INSERT INTO tgp_log (n) VALUES ('quoted')");
        engine.execute("CREATE TASK tgp_r2 SCHEDULE = '60 MINUTE' AS INSERT INTO tgp_log (n) VALUES ('r2')");
        engine.execute("CREATE TASK tgp_q AFTER tgp_r2 AS INSERT INTO tgp_log (n) VALUES ('upper')");
        engine.execute("CREATE TASK tgp_x AFTER tgp_q AS INSERT INTO tgp_log (n) VALUES ('x')");
        engine.execute("ALTER TASK \"tgp_q\" RESUME");
        engine.execute("ALTER TASK tgp_q RESUME");
        engine.execute("ALTER TASK tgp_x RESUME");
        engine.execute("EXECUTE TASK tgp_r");
        assertEquals("r,quoted", logged());
    }
}
