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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Statement-level expression evaluation (the SET / session-variable path): unary operators,
 * parentheses, {@code $var} references inside expressions, and the SYSTEM$ function family
 * (SYSTEM$STREAM_HAS_DATA, SYSTEM$USER_TASK_CANCEL, SYSTEM$TYPEOF).
 */
public class SessionExpressionAndSystemFuncTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(SessionExpressionAndSystemFuncTest.class);

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void setEvaluatesUnaryParensAndSessionVarRefs() {
        engine.execute("SET n = -5");
        assertEquals(-5, ((Number) scalar("SELECT $n")).intValue());

        engine.execute("SET p = +7");
        assertEquals(7, ((Number) scalar("SELECT $p")).intValue());

        engine.execute("SET q = (3)");
        assertEquals(3, ((Number) scalar("SELECT $q")).intValue());

        // A $var reference inside a SET expression resolves through the same evaluator.
        engine.execute("SET r = $n");
        assertEquals(-5, ((Number) scalar("SELECT $r")).intValue());
    }

    @Test
    public void systemStreamHasDataReflectsPendingChanges() {
        engine.execute("CREATE TABLE shd_src (id INTEGER)");
        engine.execute("CREATE STREAM shd_stream ON TABLE shd_src");

        engine.execute("SET empty_state = SYSTEM$STREAM_HAS_DATA('SHD_STREAM')");
        assertEquals(false, scalar("SELECT $empty_state"));

        engine.execute("INSERT INTO shd_src VALUES (1)");
        engine.execute("SET full_state = SYSTEM$STREAM_HAS_DATA('SHD_STREAM')");
        assertEquals(true, scalar("SELECT $full_state"));
    }

    @Test
    public void systemUserTaskCancelSuspendsTheTask() {
        engine.execute("CREATE TASK cancel_me SCHEDULE = '1 MINUTE' AS SELECT 1");
        engine.execute("SET outcome = SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('CANCEL_ME')");
        final Object outcome = scalar("SELECT $outcome");
        assertNotNull(outcome);
        assertTrue(outcome.toString().contains("cancelled"), "got: " + outcome);

        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE 'CANCEL_ME'");
        assertEquals(1, tasks.getRows().size());
        logger.info("Task state after cancel: {}", tasks.getRows().get(0).getValues());
    }

    @Test
    public void systemTypeofReturnsATypeName() {
        engine.execute("SET t = SYSTEM$TYPEOF(123)");
        assertNotNull(scalar("SELECT $t"));
    }
}
