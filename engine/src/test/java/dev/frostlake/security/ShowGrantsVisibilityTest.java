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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW GRANTS shows a role only grants on what it sees (documented: "The command only returns objects for which the
 * current user's current role has been granted at least one access privilege. The MANAGE GRANTS access privilege
 * implicitly allows its holder to see every object in the account"): ON an object it does not see is refused as one
 * that is not there, as DESCRIBE is; TO ROLE, OF ROLE and another user's TO USER list only the rows on objects and
 * roles it sees; a user's own listing is whole. Every form answered in full to any role. Whether a form refuses or
 * answers empty is unmeasured on live. Engine only: the live account allows no CREATE ROLE or GRANT.
 */
public class ShowGrantsVisibilityTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE hrdb");
        engine.execute("CREATE TABLE hrdb.public.pay (salary INT)");
        engine.execute("CREATE ROLE hr");
        engine.execute("CREATE ROLE low");
        engine.execute("CREATE USER boss");
        engine.execute("CREATE USER ulow");
        engine.execute("GRANT ROLE hr TO USER boss");
        engine.execute("GRANT ROLE low TO USER ulow");
        engine.execute("GRANT USAGE ON DATABASE hrdb TO ROLE hr");
        engine.execute("GRANT USAGE ON SCHEMA hrdb.public TO ROLE hr");
        engine.execute("GRANT SELECT ON TABLE hrdb.public.pay TO ROLE hr");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    /** The privilege, the kind and the name of each row, or the role and the grantee of SHOW GRANTS OF. */
    private List<String> shown(final String sql, final int... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder text = new StringBuilder();
            for (final int column : columns) {
                text.append(text.length() == 0 ? "" : "|").append(row.getValue(column));
            }
            rows.add(text.toString());
        }
        return rows;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aRoleWithNoPrivilegeSeesNoneOfTheGrants() {
        as("ULOW", "LOW");
        assertTrue(refusal("SHOW GRANTS ON TABLE hrdb.public.pay").startsWith(
            "SQL compilation error:\nTable 'HRDB.PUBLIC.PAY' does not exist or not authorized."));
        assertEquals(List.of(), shown("SHOW GRANTS TO ROLE hr", 1, 2, 3));
        assertEquals(List.of(), shown("SHOW GRANTS OF ROLE hr", 1, 3));
        assertEquals(List.of(), shown("SHOW GRANTS TO USER boss", 1, 2, 3));
        assertEquals(List.of("USAGE|ROLE|LOW"), shown("SHOW GRANTS TO USER ulow", 1, 2, 3));
    }

    @Test
    public void aRoleThatHoldsTheOtherSeesItsGrants() {
        engine.execute("GRANT ROLE hr TO ROLE low");
        as("ULOW", "LOW");
        assertEquals(List.of("USAGE|DATABASE|HRDB", "USAGE|SCHEMA|HRDB.PUBLIC", "SELECT|TABLE|HRDB.PUBLIC.PAY"),
            shown("SHOW GRANTS TO ROLE hr", 1, 2, 3));
        assertEquals(List.of("HR|BOSS", "HR|LOW"), shown("SHOW GRANTS OF ROLE hr", 1, 3));
    }

    @Test
    public void manageGrantsSeesEveryGrant() {
        as("SYSTEM", systemRole);
        assertEquals(3, shown("SHOW GRANTS TO ROLE hr", 1, 2, 3).size());
        assertEquals(List.of("HR|BOSS"), shown("SHOW GRANTS OF ROLE hr", 1, 3));
    }
}
