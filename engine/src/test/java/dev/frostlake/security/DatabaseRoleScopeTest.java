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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * "Privileges on objects in one database (such as tables or views) cannot be granted to database roles in another
 * database" (documented, "Access control considerations"): a grant on another database, its schemas or their objects to
 * a database role is refused, and nothing is recorded, so a role holding the database role reads only its own
 * database. The refusal's wording is not measured. Engine only: the live account allows no CREATE ROLE, CREATE USER
 * or GRANT.
 */
public class DatabaseRoleScopeTest {

    private static final String REFUSED = "SQL compilation error:\nPrivileges on objects of database HR cannot be "
        + "granted to database role SALES.ANALYST.";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE sales");
        engine.execute("CREATE TABLE sales.public.orders (id INT)");
        engine.execute("CREATE DATABASE hr");
        engine.execute("CREATE TABLE hr.public.salaries (name VARCHAR, salary INT)");
        engine.execute("INSERT INTO hr.public.salaries VALUES ('ceo', 999)");
        engine.execute("CREATE DATABASE ROLE sales.analyst");
        engine.execute("CREATE ROLE r1");
        engine.execute("GRANT DATABASE ROLE sales.analyst TO ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
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
    public void anotherDatabasesObjectsAreRefused() {
        assertEquals(REFUSED, refusal("GRANT USAGE ON DATABASE hr TO DATABASE ROLE sales.analyst"));
        assertEquals(REFUSED, refusal("GRANT USAGE ON SCHEMA hr.public TO DATABASE ROLE sales.analyst"));
        assertEquals(REFUSED, refusal("GRANT SELECT ON TABLE hr.public.salaries TO DATABASE ROLE sales.analyst"));
        assertEquals(REFUSED, refusal("GRANT SELECT ON ALL TABLES IN SCHEMA hr.public TO DATABASE ROLE sales.analyst"));
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("U1");
        engine.setCurrentRole("R1");
        try {
            // Holding nothing on HR, the role is refused the read.
            refusal("SELECT * FROM hr.public.salaries");
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser("SYSTEM");
            engine.setCurrentRole(systemRole);
        }
    }

    @Test
    public void itsOwnDatabasesObjectsAreTaken() {
        engine.execute("GRANT USAGE ON DATABASE sales TO DATABASE ROLE sales.analyst");
        engine.execute("GRANT USAGE ON SCHEMA sales.public TO DATABASE ROLE sales.analyst");
        engine.execute("GRANT SELECT ON TABLE sales.public.orders TO DATABASE ROLE sales.analyst");
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("U1");
        engine.setCurrentRole("R1");
        try {
            assertEquals(0, engine.executeQuery("SELECT * FROM sales.public.orders").getRows().size());
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser("SYSTEM");
            engine.setCurrentRole(systemRole);
        }
    }
}
