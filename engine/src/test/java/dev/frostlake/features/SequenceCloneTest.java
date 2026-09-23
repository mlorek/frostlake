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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** CREATE SEQUENCE ... CLONE: the clone continues from where its source stands, with its step and ordering. */
public class SequenceCloneTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_db.clone_target");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SEQUENCE seq_src START = 10 INCREMENT = 5 ORDER COMMENT = 'numbers'");
    }

    private long next(final String sequence) {
        return ((Number) engine.executeQuery("SELECT " + sequence + ".NEXTVAL").getRows().get(0).getValue(0))
            .longValue();
    }

    @Test
    public void aCloneContinuesFromItsSource() {
        assertEquals(10L, next("seq_src"));
        engine.execute("CREATE SEQUENCE test_db.clone_target.seq_copy CLONE seq_src");
        assertEquals(List.of(15L, 5L, "Y", "numbers"), engine.executeQuery("SHOW SEQUENCES LIKE 'SEQ_COPY' IN SCHEMA "
            + "test_db.clone_target ->> SELECT \"next_value\", \"interval\", \"ordered\", \"comment\" FROM $1")
            .getRows().get(0).getValues());
        assertEquals(15L, next("test_db.clone_target.seq_copy"));
        assertEquals(15L, next("seq_src"), "the source keeps its own position");
    }

    @Test
    public void theCreateModesApply() {
        engine.execute("CREATE SEQUENCE seq_other");
        final String refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE SEQUENCE seq_other CLONE seq_src");
            }
        }).getMessage();
        assertTrue(refusal.contains("already exists"), refusal);
        engine.execute("CREATE SEQUENCE IF NOT EXISTS seq_other CLONE seq_src");
        engine.execute("CREATE OR REPLACE SEQUENCE seq_other CLONE seq_src");
        assertEquals(10L, next("seq_other"));
        final String missing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE SEQUENCE seq_new CLONE nosuch");
            }
        }).getMessage();
        assertTrue(missing.contains("does not exist or not authorized"), missing);
    }

    @Test
    public void renameMovesTheSequence() {
        engine.execute("ALTER SEQUENCE seq_src RENAME TO test_db.clone_target.seq_moved");
        assertEquals(1, engine.executeQuery("SHOW SEQUENCES LIKE 'SEQ_MOVED' IN SCHEMA test_db.clone_target")
            .getRows().size());
        assertEquals(0, engine.executeQuery("SHOW SEQUENCES LIKE 'SEQ_SRC' IN SCHEMA test_db.test_schema")
            .getRows().size());
    }
}
