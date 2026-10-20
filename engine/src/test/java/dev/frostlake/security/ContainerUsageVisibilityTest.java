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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * What SHOW lists and DESCRIBE and GET_DDL answer of a schema's object: a role reaches the object only with USAGE on
 * its database and its schema, whatever it holds on the object itself (documented: "To access a specific object
 * within a schema, a role must have USAGE on the database and schema"), as SELECT and INFORMATION_SCHEMA already
 * judged; and DESCRIBE SEQUENCE, TASK and STREAM refuse an object the role does not see as a missing one, as GET_DDL
 * does. Engine only: GRANT and USE ROLE are not run on the live account.
 */
public class ContainerUsageVisibilityTest extends BaseDatabaseTest {

    private String opener;

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE cu_db");
        engine.execute("CREATE TABLE cu_db.public.t (x INT, s VARCHAR)");
        engine.execute("CREATE VIEW cu_db.public.v AS SELECT * FROM cu_db.public.t");
        engine.execute("CREATE ROLE cu_r");
        engine.execute("GRANT SELECT ON TABLE cu_db.public.t TO ROLE cu_r");
        engine.execute("GRANT SELECT ON VIEW cu_db.public.v TO ROLE cu_r");
        // A role is activated by a user it reaches: here through the role the session opens with.
        engine.execute("GRANT ROLE cu_r TO ROLE " + opener);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** The names a listing's rows carry in its second column. */
    private List<String> names(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(String.valueOf(row.getValue(1)));
        }
        return out;
    }

    private void asRole() {
        engine.execute("USE ROLE cu_r");
        engine.execute("USE SECONDARY ROLES NONE");
    }

    @Test
    public void anObjectOfContainersTheRoleDoesNotUseIsNeitherListedNorAnswered() {
        asRole();
        try {
            assertFalse(names("SHOW TABLES IN ACCOUNT").contains("T"));
            assertFalse(names("SHOW VIEWS IN ACCOUNT").contains("V"));
            assertTrue(refusal("SELECT GET_DDL('VIEW', 'cu_db.public.v')")
                .contains("'CU_DB.PUBLIC.V' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE TABLE cu_db.public.t").contains("does not exist or not authorized."));
            assertTrue(refusal("SELECT * FROM cu_db.public.t").contains("does not exist or not authorized."));
        } finally {
            engine.execute("USE ROLE " + opener);
        }
    }

    @Test
    public void usageOnTheContainersListsAndAnswersIt() {
        engine.execute("GRANT USAGE ON DATABASE cu_db TO ROLE cu_r");
        engine.execute("GRANT USAGE ON SCHEMA cu_db.public TO ROLE cu_r");
        asRole();
        try {
            assertTrue(names("SHOW TABLES IN ACCOUNT").contains("T"));
            assertTrue(names("SHOW TERSE TABLES IN SCHEMA cu_db.public").contains("T"));
            assertTrue(String.valueOf(engine.executeQuery("SELECT GET_DDL('VIEW', 'cu_db.public.v')").getRows().get(0)
                .getValue(0)).contains("create or replace view V"));
            assertEquals(2, engine.executeQuery("DESCRIBE TABLE cu_db.public.t").getRows().size());
        } finally {
            engine.execute("USE ROLE " + opener);
        }
    }

    @Test
    public void describeRefusesASequenceATaskAndAStreamTheRoleDoesNotSee() {
        engine.execute("GRANT USAGE ON DATABASE cu_db TO ROLE cu_r");
        engine.execute("GRANT USAGE ON SCHEMA cu_db.public TO ROLE cu_r");
        engine.execute("CREATE TABLE cu_db.public.secret (x INT, s VARCHAR)");
        engine.execute("CREATE SEQUENCE cu_db.public.seq START 100");
        engine.execute("CREATE STREAM cu_db.public.sstr ON TABLE cu_db.public.secret");
        engine.execute("CREATE TASK cu_db.public.stask SCHEDULE = '60 MINUTE' AS SELECT 1");
        asRole();
        try {
            assertTrue(refusal("DESCRIBE SEQUENCE cu_db.public.seq")
                .contains("Sequence 'CU_DB.PUBLIC.SEQ' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE TASK cu_db.public.stask")
                .contains("Task 'CU_DB.PUBLIC.STASK' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE STREAM cu_db.public.sstr")
                .contains("Stream 'CU_DB.PUBLIC.SSTR' does not exist or not authorized."));
            assertTrue(refusal("SELECT GET_DDL('SEQUENCE', 'cu_db.public.seq')").contains("does not exist"));
        } finally {
            engine.execute("USE ROLE " + opener);
        }
        engine.execute("GRANT USAGE ON SEQUENCE cu_db.public.seq TO ROLE cu_r");
        asRole();
        try {
            assertEquals(1, engine.executeQuery("DESCRIBE SEQUENCE cu_db.public.seq").getRows().size());
        } finally {
            engine.execute("USE ROLE " + opener);
        }
    }
}
