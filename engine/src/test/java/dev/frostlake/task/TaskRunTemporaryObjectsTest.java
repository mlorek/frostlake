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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task's run is a session of its own, so what its body creates TEMPORARY ends with the run: the table the body
 * filled and read is not there for the session that started the task, while that session's own temporary table,
 * and what the body wrote to a permanent one, stay.
 */
public class TaskRunTemporaryObjectsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE task_tmp_db");
        engine.execute("CREATE SCHEMA task_tmp_db.s");
        engine.execute("USE SCHEMA task_tmp_db.s");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String cell(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.next(), sql);
        return String.valueOf(rs.getValue(0));
    }

    @Test
    public void aRunsTemporaryTableEndsWithTheRun() {
        engine.executeQuery("CREATE TABLE made_log (n NUMBER)");
        engine.executeQuery("CREATE TEMPORARY TABLE callers_own (x NUMBER)");
        engine.executeQuery("INSERT INTO callers_own VALUES (5)");
        engine.executeQuery("""
            CREATE TASK tmp_task AS
            BEGIN
                CREATE TEMPORARY TABLE tmp_made (x NUMBER);
                INSERT INTO tmp_made VALUES (1), (2);
                INSERT INTO made_log SELECT COUNT(*) FROM tmp_made;
            END
            """);
        engine.executeQuery("EXECUTE TASK tmp_task");
        assertEquals("2", cell("SELECT n FROM made_log"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM tmp_made");
            }
        });
        assertTrue(String.valueOf(refused.getMessage())
            .contains("Object 'TMP_MADE' does not exist or not authorized."), refused.getMessage());
        assertEquals("5", cell("SELECT x FROM callers_own"));
        // The next run creates the table again: the name was free.
        engine.executeQuery("EXECUTE TASK tmp_task");
        assertEquals("2", cell("SELECT COUNT(*) FROM made_log"));
    }
}
