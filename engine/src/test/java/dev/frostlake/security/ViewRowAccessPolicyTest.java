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
import dev.frostlake.metastore.model.View;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER VIEW ... ADD/DROP ROW ACCESS POLICY: a row access policy attached to a VIEW filters the
 * view's output rows exactly like one attached to a table filters scans (ACCOUNTADMIN/SYSADMIN
 * bypass included), and the attachment survives CLONE.
 */
public class ViewRowAccessPolicyTest extends BaseDatabaseTest {

    private void createOrdersAndView() {
        engine.execute("CREATE TABLE orders (id INTEGER, region VARCHAR)");
        engine.execute("INSERT INTO orders VALUES (1, 'EU'), (2, 'US'), (3, 'EU')");
        engine.execute("CREATE VIEW orders_view AS SELECT id, region FROM orders");
    }

    private void useRole(final String role) {
        engine.getSecurityManager().getSessionContext().setCurrentRole(role);
    }

    @Test
    public void alterViewAddRowAccessPolicyAttaches() {
        createOrdersAndView();
        engine.execute("CREATE ROW ACCESS POLICY eu_only AS (r VARCHAR) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("ALTER VIEW orders_view ADD ROW ACCESS POLICY eu_only ON (region)");

        final View view = engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getView("ORDERS_VIEW");
        assertTrue(view.hasRowAccessPolicy());
        assertEquals("EU_ONLY", view.getRowAccessPolicyName());
        assertEquals(1, view.getRowAccessPolicyColumns().size());
    }

    @Test
    public void policyFiltersViewRowsForNonAdminAndBypassesForAdmin() {
        createOrdersAndView();
        engine.execute("""
            CREATE ROW ACCESS POLICY eu_only AS (r VARCHAR) RETURNS BOOLEAN
            -> CURRENT_ROLE() = 'SYSADMIN' OR r = 'EU'""");
        engine.execute("ALTER VIEW orders_view ADD ROW ACCESS POLICY eu_only ON (region)");

        useRole("SYSADMIN");
        assertEquals(3, engine.executeQuery("SELECT * FROM orders_view").getRowCount());

        useRole("PUBLIC");
        assertEquals(2, engine.executeQuery("SELECT * FROM orders_view").getRowCount());
    }

    @Test
    public void dropRowAccessPolicyRestoresFullVisibility() {
        createOrdersAndView();
        engine.execute("CREATE ROW ACCESS POLICY none_visible AS (r VARCHAR) RETURNS BOOLEAN -> FALSE");
        engine.execute("ALTER VIEW orders_view ADD ROW ACCESS POLICY none_visible ON (region)");

        useRole("PUBLIC");
        assertEquals(0, engine.executeQuery("SELECT * FROM orders_view").getRowCount());

        useRole("SYSADMIN");
        engine.execute("ALTER VIEW orders_view DROP ROW ACCESS POLICY none_visible");
        assertFalse(engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA")
            .getView("ORDERS_VIEW").hasRowAccessPolicy());

        useRole("PUBLIC");
        assertEquals(3, engine.executeQuery("SELECT * FROM orders_view").getRowCount());
    }

    @Test
    public void schemaQualifiedPolicyNameResolvesFromTheViewAttachment() {
        createOrdersAndView();
        engine.execute("CREATE SCHEMA util_schema");
        engine.execute("""
            CREATE ROW ACCESS POLICY util_schema.region_rap AS (r VARCHAR) RETURNS BOOLEAN
            -> CURRENT_ROLE() = 'SYSADMIN' OR r = 'US'""");
        engine.execute("ALTER VIEW orders_view ADD ROW ACCESS POLICY util_schema.region_rap ON (region)");

        useRole("PUBLIC");
        assertEquals(1, engine.executeQuery("SELECT * FROM orders_view").getRowCount());
        useRole("SYSADMIN");
    }

    @Test
    public void cloneDatabasePreservesViewPolicyAttachment() {
        createOrdersAndView();
        engine.execute("""
            CREATE ROW ACCESS POLICY eu_only AS (r VARCHAR) RETURNS BOOLEAN
            -> CURRENT_ROLE() = 'SYSADMIN' OR r = 'EU'""");
        engine.execute("ALTER VIEW orders_view ADD ROW ACCESS POLICY eu_only ON (region)");
        engine.execute("CREATE DATABASE rap_clone CLONE test_db");
        engine.execute("USE DATABASE rap_clone");
        engine.execute("USE SCHEMA test_schema");

        useRole("PUBLIC");
        assertEquals(2, engine.executeQuery("SELECT * FROM orders_view").getRowCount());

        useRole("SYSADMIN");
        assertEquals(3, engine.executeQuery("SELECT * FROM orders_view").getRowCount());
    }
}
