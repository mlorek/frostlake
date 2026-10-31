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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * USAGE on a routine whose name is quoted lower case authorizes calling it: the privilege is asked under the name it
 * is recorded under, {@code DB.S.pq()}, not the name SHOW GRANTS and the refusals spell, {@code DB.S."pq"()}. Only
 * USAGE on the routine and on its database and schema is needed (documented), for a procedure of owner's or
 * caller's rights, a function, and a defaulted parameter alike, as for an upper-case name. Engine only:
 * GRANT and USE ROLE are not run on the live account.
 */
public class QuotedRoutineUsageTest extends BaseDatabaseTest {

    private String opener;

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE ROLE qr_r");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE qr_r");
        engine.execute("GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE qr_r");
        // A role is activated by a user it reaches: here through the role the session opens with.
        engine.execute("GRANT ROLE qr_r TO ROLE " + opener);
    }

    private String value(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private List<String> names(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(String.valueOf(row.getValue(1)));
        }
        return out;
    }

    private void asRole() {
        engine.execute("USE ROLE qr_r");
        engine.execute("USE SECONDARY ROLES NONE");
    }

    @Test
    public void usageOnAQuotedLowerCaseRoutineAuthorizesIt() {
        engine.execute("CREATE PROCEDURE \"pq\"() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE PROCEDURE \"pc\"(x INT) RETURNS INT LANGUAGE SQL EXECUTE AS CALLER"
            + " AS 'BEGIN RETURN x; END'");
        engine.execute("CREATE FUNCTION \"fq\"() RETURNS INT AS '1'");
        engine.execute("CREATE FUNCTION \"fd\"(a INT, b VARCHAR DEFAULT 'z') RETURNS INT AS 'a'");
        engine.execute("CREATE FUNCTION \"fn\"() RETURNS INT AS '2'");
        engine.execute("GRANT USAGE ON PROCEDURE \"pq\"() TO ROLE qr_r");
        engine.execute("GRANT USAGE ON PROCEDURE \"pc\"(INT) TO ROLE qr_r");
        engine.execute("GRANT USAGE ON FUNCTION \"fq\"() TO ROLE qr_r");
        engine.execute("GRANT USAGE ON FUNCTION \"fd\"(INT, VARCHAR) TO ROLE qr_r");
        asRole();
        try {
            assertEquals("1", value("CALL \"pq\"()"));
            assertEquals("1", value("CALL test_db.test_schema.\"pq\"()"));
            assertEquals("3", value("CALL \"pc\"(3)"));
            assertEquals("1", value("SELECT \"fq\"()"));
            assertEquals("4", value("SELECT \"fd\"(4)"));
            // A routine the role was granted nothing on stays refused, in the words naming it as SQL spells it.
            final String refused = assertRefused("SELECT \"fn\"()");
            assertTrue(refused.contains("must have USAGE granted on FUNCTION TEST_DB.TEST_SCHEMA.\"fn\"()"), refused);
            // The routines it may use are listed to it, and the other is not.
            final List<String> functions = names("SHOW USER FUNCTIONS");
            assertTrue(functions.contains("fq") && !functions.contains("fn"), functions.toString());
            assertTrue(names("SHOW USER PROCEDURES").contains("pq"));
        } finally {
            engine.execute("USE ROLE " + opener);
        }
    }

    private String assertRefused(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException refused) {
            return refused.getMessage();
        }
        throw new AssertionError("not refused: " + sql);
    }
}
