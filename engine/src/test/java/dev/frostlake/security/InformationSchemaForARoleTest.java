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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A user who is no administrator reads the INFORMATION_SCHEMA views of a database its role may use: the views
 * need no grant of their own, and each lists the objects the role reaches and no others. The user is switched
 * through the engine's own API, which no SQL statement does.
 */
public class InformationSchemaForARoleTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE TABLE t (v NUMBER)");
        engine.execute("CREATE TABLE kept_back (w NUMBER)");
        engine.execute("CREATE VIEW seen_v AS SELECT v FROM t");
        engine.execute("CREATE FUNCTION f() RETURNS NUMBER AS '1'");
        engine.execute("CREATE ROLE analyst");
        engine.execute("CREATE USER ann");
        engine.execute("GRANT ROLE analyst TO USER ann");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE analyst");
        engine.execute("GRANT USAGE ON SCHEMA p.PUBLIC TO ROLE analyst");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE analyst");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The first column of a query's rows, as the analyst's user sees them. */
    private List<String> asAnn(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("ann");
        engine.setCurrentRole("analyst");
        try {
            final ResultSet answered = engine.executeQuery(sql);
            final List<String> first = new ArrayList<>();
            for (final Row row : answered.getRows()) {
                first.add(String.valueOf(row.getValue(0)));
            }
            return first;
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    @Test
    public void theViewsAreReadWithoutAGrantAndListWhatTheRoleReaches() {
        assertEquals(Collections.singletonList("0"), asAnn("SELECT COUNT(*) FROM t"));
        assertEquals(Collections.singletonList("T"), asAnn(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC' ORDER BY 1"));
        assertEquals(Collections.singletonList("1"), asAnn(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'PUBLIC'"));
        assertEquals(Collections.singletonList("0"), asAnn(
            "SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'PUBLIC'"));
        assertEquals(Collections.singletonList("0"), asAnn(
            "SELECT COUNT(*) FROM information_schema.functions WHERE function_schema = 'PUBLIC'"));
        assertEquals(Collections.singletonList("PUBLIC"), asAnn(
            "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'PUBLIC'"));
    }

    @Test
    public void aGrantOnAnObjectListsIt() {
        engine.execute("GRANT SELECT ON VIEW seen_v TO ROLE analyst");
        engine.execute("GRANT USAGE ON FUNCTION f() TO ROLE analyst");
        engine.execute("GRANT INSERT ON TABLE kept_back TO ROLE analyst");
        assertEquals(Arrays.asList("KEPT_BACK", "SEEN_V", "T"), asAnn(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC' ORDER BY 1"));
        assertEquals(Collections.singletonList("SEEN_V"), asAnn(
            "SELECT table_name FROM information_schema.views WHERE table_schema = 'PUBLIC'"));
        assertEquals(Collections.singletonList("F"), asAnn(
            "SELECT function_name FROM information_schema.functions WHERE function_schema = 'PUBLIC'"));
    }

    @Test
    public void aSchemaTheRoleCannotEnterListsNothing() {
        engine.execute("REVOKE USAGE ON SCHEMA p.PUBLIC FROM ROLE analyst");
        assertEquals(Collections.emptyList(), asAnn(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'"));
        assertEquals(Collections.emptyList(), asAnn(
            "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'PUBLIC'"));
    }

    @Test
    public void theAdministratorStillSeesEverything() {
        final ResultSet answered = engine.executeQuery(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC' ORDER BY 1");
        final List<String> names = new ArrayList<>();
        for (final Row row : answered.getRows()) {
            names.add(String.valueOf(row.getValue(0)));
        }
        assertEquals(Arrays.asList("KEPT_BACK", "SEEN_V", "T"), names);
    }
}
