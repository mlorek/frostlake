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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA's role and privilege views answer for the session that reads them (documented): ENABLED_ROLES
 * lists the roles in use and every role granted to one of them, with PUBLIC always; APPLICABLE_ROLES lists the role
 * grants applied to the session's user, the roles granted to those included; TABLE_PRIVILEGES and OBJECT_PRIVILEGES
 * list only objects the session's roles reach. Engine only: the live account allows no CREATE USER or CREATE ROLE.
 */
public class InformationSchemaRoleViewsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE isr");
        engine.execute("USE SCHEMA isr.public");
        engine.execute("CREATE TABLE hidden (id INT, pw STRING)");
        engine.execute("CREATE SECURE VIEW sv AS SELECT * FROM hidden WHERE pw = 'topsecret'");
        engine.execute("CREATE ROLE isr_z");
        engine.execute("CREATE ROLE isr_zc");
        engine.execute("CREATE ROLE isr_other");
        engine.execute("GRANT ROLE isr_zc TO ROLE isr_z");
        engine.execute("GRANT USAGE ON DATABASE isr TO ROLE isr_z");
        engine.execute("GRANT USAGE ON SCHEMA isr.public TO ROLE isr_z");
        engine.execute("CREATE USER isr_u");
        engine.execute("CREATE USER isr_someone");
        engine.execute("GRANT ROLE isr_z TO USER isr_u");
        engine.execute("GRANT ROLE isr_other TO USER isr_someone");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A query's rows, each one's values joined by '|', and the rows joined by " ; ". */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void asIsrZ() {
        engine.setCurrentUser("ISR_U");
        engine.execute("USE ROLE isr_z");
    }

    @Test
    public void enabledRolesAreTheRolesInUseAndTheRolesGrantedToThem() {
        asIsrZ();
        assertEquals("ISR_Z ; ISR_ZC ; PUBLIC",
            rows("SELECT role_name FROM isr.INFORMATION_SCHEMA.ENABLED_ROLES ORDER BY 1"));
        engine.execute("USE ROLE isr_zc");
        // ISR_Z stays in use as a secondary role, the user's default being ALL.
        assertEquals("ISR_Z ; ISR_ZC ; PUBLIC",
            rows("SELECT role_name FROM isr.INFORMATION_SCHEMA.ENABLED_ROLES ORDER BY 1"));
        engine.execute("USE SECONDARY ROLES NONE");
        assertEquals("ISR_ZC ; PUBLIC", rows("SELECT role_name FROM isr.INFORMATION_SCHEMA.ENABLED_ROLES ORDER BY 1"));
    }

    @Test
    public void applicableRolesAreTheGrantsAppliedToTheSessionsUser() {
        asIsrZ();
        assertEquals("ISR_U|ISR_Z ; ISR_Z|ISR_ZC",
            rows("SELECT grantee, role_name FROM isr.INFORMATION_SCHEMA.APPLICABLE_ROLES"
                + " WHERE role_name LIKE 'ISR%' ORDER BY 1, 2"));
        assertEquals("0", rows("SELECT COUNT(*) FROM isr.INFORMATION_SCHEMA.APPLICABLE_ROLES"
            + " WHERE grantee = 'ISR_SOMEONE' OR role_name = 'ISR_OTHER'"));
    }

    @Test
    public void thePrivilegeViewsListOnlyWhatTheSessionReaches() {
        asIsrZ();
        assertEquals("", rows("SELECT table_name FROM isr.INFORMATION_SCHEMA.TABLES WHERE table_schema = 'PUBLIC'"));
        assertEquals("0", rows("SELECT COUNT(*) FROM isr.INFORMATION_SCHEMA.TABLE_PRIVILEGES"));
        assertEquals("0", rows("SELECT COUNT(*) FROM isr.INFORMATION_SCHEMA.OBJECT_PRIVILEGES"
            + " WHERE object_name IN ('HIDDEN', 'SV')"));
        assertEquals("ISR|ISR_Z ; PUBLIC|ISR_Z", rows("SELECT object_name, grantee FROM isr.INFORMATION_SCHEMA."
            + "OBJECT_PRIVILEGES WHERE grantee = 'ISR_Z' ORDER BY 1"));
    }
}
