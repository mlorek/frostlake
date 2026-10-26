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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Databases, schemas, warehouses, compute pools, alerts and Cortex Search services are listed by SHOW only to a role
 * that owns them or holds a privilege on them, or holds MANAGE GRANTS (documented on every SHOW page), and DESCRIBE
 * and GET_DDL answer one the role does not see as one that does not exist. The SNOWFLAKE database is listed to every
 * role. The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the embedded engine.
 */
public class AccountListingVisibilityTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE alv_p");
        engine.execute("CREATE SCHEMA alv_p.seen_s");
        engine.execute("CREATE SCHEMA alv_p.hidden_s");
        engine.execute("CREATE DATABASE alv_q");
        engine.execute("CREATE WAREHOUSE alv_w1");
        engine.execute("CREATE WAREHOUSE alv_w2");
        engine.execute("CREATE ROLE reader_role");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER reader DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE reader_role TO USER reader");
        engine.execute("GRANT USAGE ON DATABASE alv_p TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA alv_p.seen_s TO ROLE reader_role");
        engine.execute("GRANT USAGE ON WAREHOUSE alv_w1 TO ROLE reader_role");
        engine.execute("USE SCHEMA alv_p.seen_s");
        engine.execute("CREATE TABLE docs (id INT, body VARCHAR)");
        engine.execute("CREATE ALERT al WAREHOUSE = alv_w1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) "
            + "THEN SELECT 1");
        engine.execute("CREATE CORTEX SEARCH SERVICE css ON body WAREHOUSE = alv_w1 TARGET_LAG = '1 hour' "
            + "AS SELECT id, body FROM docs");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The reader's answer - rows as "a, b | c, d", or the refusal - then back to the administrator. */
    private String asReader(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole("reader_role");
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    /** The names a SHOW lists that the test made, or SNOWFLAKE, in its order. */
    private String names(final String show) {
        return asReader(show + " ->> SELECT \"name\" FROM $1 WHERE \"name\" LIKE 'ALV%' OR \"name\" IN ('SNOWFLAKE', "
            + "'SEEN_S', 'HIDDEN_S', 'PUBLIC', 'INFORMATION_SCHEMA', 'AL', 'CSS') ORDER BY 1");
    }

    /** The schemas of the test's databases a SHOW lists, each as database.schema, in order. */
    private String schemas(final String show) {
        return asReader(show + " ->> SELECT \"database_name\" || '.' || \"name\" FROM $1 WHERE \"database_name\" LIKE "
            + "'ALV%' ORDER BY 1");
    }

    private static String missing(final String kind, final String name, final String hint) {
        return "SQL compilation error:|" + kind + " '" + name + "' does not exist or not authorized. Your primary role "
            + "READER_ROLE must have " + hint + " granted on " + kind.toUpperCase(Locale.ROOT) + " " + name
            + ".";
    }

    @Test
    public void containersAndWarehousesAreListedOnlyToARoleThatSeesThem() {
        assertEquals("ALV_P | SNOWFLAKE", names("SHOW DATABASES"));
        assertEquals("ALV_P | SNOWFLAKE", names("SHOW DATABASES HISTORY"));
        assertEquals("ALV_P.INFORMATION_SCHEMA | ALV_P.SEEN_S", schemas("SHOW SCHEMAS IN DATABASE alv_p"));
        assertEquals("ALV_P.INFORMATION_SCHEMA | ALV_P.SEEN_S", schemas("SHOW SCHEMAS IN ACCOUNT"));
        assertEquals("", schemas("SHOW SCHEMAS IN DATABASE alv_q"));
        assertEquals("ALV_W1", names("SHOW WAREHOUSES"));
        assertEquals("", names("SHOW ALERTS IN ACCOUNT"));
        assertEquals("", names("SHOW CORTEX SEARCH SERVICES IN ACCOUNT"));
        // The administrator still lists every one of them.
        assertEquals("ALV_P | ALV_Q | SNOWFLAKE", adminNames("SHOW DATABASES"));
        assertEquals("ALV_W1 | ALV_W2", adminNames("SHOW WAREHOUSES"));
    }

    private String adminNames(final String show) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(show + " ->> SELECT \"name\" FROM $1 WHERE \"name\" LIKE 'ALV%' "
                + "OR \"name\" = 'SNOWFLAKE' ORDER BY 1").getRows()) {
            rows.append(rows.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return rows.toString();
    }

    @Test
    public void anUnseenContainerOrWarehouseIsDescribedAsOneThatDoesNotExist() {
        final String database = missing("Database", "ALV_Q", "USAGE or any other privilege");
        final String schema = missing("Schema", "ALV_P.HIDDEN_S", "USAGE or any other privilege");
        // DESCRIBE SCHEMA asks MONITOR of a schema the role sees or not, so it refuses either the same way.
        final String monitor = "SQL access control error:|Insufficient privileges to operate on schema 'HIDDEN_S'. "
            + "Your primary role READER_ROLE must have MONITOR granted on SCHEMA ALV_P.HIDDEN_S.";
        final String warehouse = missing("Warehouse", "ALV_W2", "at least one privilege");
        assertEquals(database, asReader("DESCRIBE DATABASE alv_q"));
        assertEquals(database, asReader("SELECT GET_DDL('DATABASE', 'alv_q')"));
        assertEquals(monitor, asReader("DESCRIBE SCHEMA alv_p.hidden_s"));
        assertEquals(schema, asReader("SELECT GET_DDL('SCHEMA', 'alv_p.hidden_s')"));
        assertEquals(warehouse, asReader("DESCRIBE WAREHOUSE alv_w2"));
        assertEquals(warehouse, asReader("SELECT GET_DDL('WAREHOUSE', 'alv_w2')"));
        // What the role sees is answered, a database's schemas filtered alike.
        assertEquals("INFORMATION_SCHEMA, SCHEMA | SEEN_S, SCHEMA",
            asReader("DESCRIBE DATABASE alv_p ->> SELECT \"name\", \"kind\" FROM $1"));
        assertEquals("ALV_W1, WAREHOUSE", asReader("DESCRIBE WAREHOUSE alv_w1 ->> SELECT \"name\", \"kind\" FROM $1"));
    }

    @Test
    public void anAlertIsSeenThroughAPrivilegeOnItAndASearchServiceIsNotSeenWithoutOne() {
        assertEquals(missing("Alert", "ALV_P.SEEN_S.AL", "at least one privilege"),
            asReader("DESCRIBE ALERT alv_p.seen_s.al"));
        assertEquals(missing("Cortex Search Service", "ALV_P.SEEN_S.CSS", "at least one privilege"),
            asReader("DESCRIBE CORTEX SEARCH SERVICE alv_p.seen_s.css"));
        engine.execute("GRANT OPERATE ON ALERT alv_p.seen_s.al TO ROLE reader_role");
        assertEquals("AL", names("SHOW ALERTS IN ACCOUNT"));
        assertEquals("AL", asReader("DESCRIBE ALERT alv_p.seen_s.al ->> SELECT \"name\" FROM $1"));
        assertEquals("", names("SHOW CORTEX SEARCH SERVICES IN ACCOUNT"));
    }

    @Test
    public void manageGrantsSeesEveryObjectOfTheAccount() {
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE reader_role");
        assertEquals("ALV_P | ALV_Q | SNOWFLAKE", names("SHOW DATABASES"));
        assertEquals("ALV_P.HIDDEN_S | ALV_P.INFORMATION_SCHEMA | ALV_P.PUBLIC | ALV_P.SEEN_S",
            schemas("SHOW SCHEMAS IN DATABASE alv_p"));
        assertEquals("ALV_Q.INFORMATION_SCHEMA | ALV_Q.PUBLIC", schemas("SHOW SCHEMAS IN DATABASE alv_q"));
        assertEquals("ALV_W1 | ALV_W2", names("SHOW WAREHOUSES"));
        assertEquals("AL", names("SHOW ALERTS IN ACCOUNT"));
        assertEquals("CSS", names("SHOW CORTEX SEARCH SERVICES IN ACCOUNT"));
        assertEquals("ALV_W2, WAREHOUSE", asReader("DESCRIBE WAREHOUSE alv_w2 ->> SELECT \"name\", \"kind\" FROM $1"));
    }
}
