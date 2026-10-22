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
 * SHOW SECRETS, SHOW NETWORK RULES, SHOW PASSWORD POLICIES and SHOW SESSION POLICIES list, and DESCRIBE answers, only
 * the security objects the role holds a privilege on, with USAGE on their database and schema, as for every other
 * schema object (the SHOW usage note: "only returns objects for which the current user's current role has been
 * granted at least one access privilege"). DESCRIBE of one the role does not see refuses it as a missing one.
 * Engine only: GRANT and USE ROLE are not run on the live account.
 */
public class SecurityObjectVisibilityTest extends BaseDatabaseTest {

    private String opener;

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE so_db");
        engine.execute("CREATE SCHEMA so_db.hid");
        engine.execute("CREATE SECRET so_db.hid.sec TYPE = GENERIC_STRING SECRET_STRING = 'topsecret'");
        engine.execute("CREATE NETWORK RULE so_db.hid.nr MODE = EGRESS TYPE = HOST_PORT VALUE_LIST = ('example.com')");
        engine.execute("CREATE PASSWORD POLICY so_db.hid.pp");
        engine.execute("CREATE SESSION POLICY so_db.hid.sp");
        engine.execute("CREATE SECRET so_db.public.shared TYPE = GENERIC_STRING SECRET_STRING = 'x'");
        engine.execute("CREATE SECRET so_db.public.unshared TYPE = GENERIC_STRING SECRET_STRING = 'y'");
        engine.execute("CREATE ROLE so_r");
        engine.execute("GRANT USAGE ON DATABASE so_db TO ROLE so_r");
        engine.execute("GRANT USAGE ON SCHEMA so_db.public TO ROLE so_r");
        engine.execute("GRANT READ ON SECRET so_db.public.shared TO ROLE so_r");
        // A role is activated by a user it reaches: here through the role the session opens with.
        engine.execute("GRANT ROLE so_r TO ROLE " + opener);
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
        engine.execute("USE ROLE so_r");
        engine.execute("USE SECONDARY ROLES NONE");
    }

    @Test
    public void securityObjectsTheRoleHoldsNothingOnAreNeitherListedNorDescribed() {
        asRole();
        try {
            assertEquals(List.of("SHARED"), names("SHOW SECRETS IN ACCOUNT"));
            assertTrue(names("SHOW SECRETS IN SCHEMA so_db.hid").isEmpty());
            assertTrue(names("SHOW NETWORK RULES IN ACCOUNT").isEmpty());
            assertTrue(names("SHOW PASSWORD POLICIES IN ACCOUNT").isEmpty());
            assertTrue(names("SHOW SESSION POLICIES IN ACCOUNT").isEmpty());
            assertTrue(refusal("DESCRIBE SECRET so_db.hid.sec")
                .contains("Secret 'SO_DB.HID.SEC' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE SECRET so_db.public.unshared")
                .contains("Secret 'SO_DB.PUBLIC.UNSHARED' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE NETWORK RULE so_db.hid.nr")
                .contains("Network rule 'SO_DB.HID.NR' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE PASSWORD POLICY so_db.hid.pp")
                .contains("Password policy 'SO_DB.HID.PP' does not exist or not authorized."));
            assertTrue(refusal("DESCRIBE SESSION POLICY so_db.hid.sp")
                .contains("Session policy 'SO_DB.HID.SP' does not exist or not authorized."));
            // The secret the role reads is described.
            assertEquals("SHARED", String.valueOf(engine.executeQuery("DESCRIBE SECRET so_db.public.shared")
                .getRows().get(0).getValue(1)));
        } finally {
            engine.execute("USE ROLE " + opener);
        }
    }

    @Test
    public void theOwnerStillListsAndDescribesThem() {
        final List<String> secrets = names("SHOW SECRETS IN ACCOUNT");
        assertTrue(secrets.contains("SEC") && secrets.contains("SHARED") && secrets.contains("UNSHARED"), secrets
            .toString());
        assertEquals(List.of("NR"), names("SHOW NETWORK RULES IN SCHEMA so_db.hid"));
        assertEquals("NR", String.valueOf(engine.executeQuery("DESCRIBE NETWORK RULE so_db.hid.nr").getRows().get(0)
            .getValue(1)));
        assertFalse(names("SHOW SESSION POLICIES IN SCHEMA so_db.hid").isEmpty());
    }
}
