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
 * A role that reads a table only through a secure view, or through the row access and masking policies on it, cannot
 * hand the view's or the policies' mapping tables rows of its own: a CTE of its query named like a mapping table is
 * not what the view's body or a policy's body reads. The live account allows no CREATE ROLE, CREATE USER or GRANT,
 * so the role-bound half is held by the embedded engine; the same bodies read as their owner are checked on both
 * sides in {@code StoredBodyCteScopeTest}.
 */
public class PolicyMappingCteScopeTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE TABLE secret (id INT, val VARCHAR)");
        engine.execute("INSERT INTO secret VALUES (1, 'mine'), (2, 'not mine')");
        engine.execute("CREATE TABLE acl (role_name VARCHAR, id INT)");
        engine.execute("INSERT INTO acl VALUES ('ANALYST', 1)");
        engine.execute("""
            CREATE SECURE VIEW sv AS SELECT s.id, s.val FROM secret s
            WHERE s.id IN (SELECT id FROM acl WHERE role_name = CURRENT_ROLE())""");
        engine.execute("CREATE TABLE sales (id INT, region VARCHAR, ssn VARCHAR)");
        engine.execute("INSERT INTO sales VALUES (1, 'EU', '111-11'), (2, 'US', '222-22')");
        engine.execute("CREATE TABLE entitlements (role_name VARCHAR, region VARCHAR)");
        engine.execute("INSERT INTO entitlements VALUES ('ANALYST', 'EU')");
        engine.execute("CREATE TABLE unmask (role_name VARCHAR)");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (r VARCHAR) RETURNS BOOLEAN -> "
            + "r IN (SELECT region FROM entitlements WHERE role_name = CURRENT_ROLE())");
        engine.execute("ALTER TABLE sales ADD ROW ACCESS POLICY rap ON (region)");
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> "
            + "CASE WHEN CURRENT_ROLE() IN (SELECT role_name FROM unmask) THEN v ELSE '***' END");
        engine.execute("ALTER TABLE sales MODIFY COLUMN ssn SET MASKING POLICY mp");
        engine.execute("CREATE ROLE analyst");
        engine.execute("CREATE USER bob");
        engine.execute("GRANT ROLE analyst TO USER bob");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE analyst");
        engine.execute("GRANT USAGE ON SCHEMA p.PUBLIC TO ROLE analyst");
        engine.execute("GRANT SELECT ON VIEW sv TO ROLE analyst");
        engine.execute("GRANT SELECT ON TABLE sales TO ROLE analyst");
        engine.execute("GRANT SELECT ON TABLE entitlements TO ROLE analyst");
        engine.execute("GRANT SELECT ON TABLE unmask TO ROLE analyst");
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("bob");
        engine.setCurrentRole("analyst");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser("SYSTEM");
            engine.setCurrentRole(systemRole);
            engine.shutdown();
        }
    }

    /** A query's rows as "a, b | c, d". */
    private String rows(final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            rows.append(rows.length() > 0 ? " | " : "").append(cells);
        }
        return rows.toString();
    }

    @Test
    public void aSecureViewsMappingTableIsNotTheQuerysCte() {
        assertEquals("1, mine", rows("SELECT * FROM sv ORDER BY 1"));
        assertEquals("1, mine", rows("""
            WITH acl AS (SELECT 'ANALYST' AS role_name, column1 AS id FROM VALUES (1), (2))
            SELECT * FROM sv ORDER BY 1"""));
    }

    @Test
    public void aPolicysMappingTableIsNotTheQuerysCte() {
        assertEquals("1, EU, ***", rows("SELECT * FROM sales ORDER BY 1"));
        assertEquals("1, EU, ***", rows("""
            WITH entitlements AS (SELECT 'ANALYST' AS role_name, column1 AS region FROM VALUES ('EU'), ('US')),
                 unmask AS (SELECT 'ANALYST' AS role_name)
            SELECT * FROM sales ORDER BY 1"""));
    }
}
