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

package dev.frostlake.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code IN ACCOUNT} lists every listing's objects account-wide, the account-level listings included, and any other
 * scope on an account-level listing — databases, warehouses, users, roles, compute pools, locks, transactions — is
 * refused as live refuses it (live-verified).
 */
public class ShowAccountScopeTest extends BaseDatabaseTest {

    /** The listing's row count and its first row's name, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final ResultSet listing = engine.executeQuery(sql);
            int name = -1;
            for (int i = 0; i < listing.getColumns().size(); i++) {
                if ("name".equalsIgnoreCase(listing.getColumns().get(i).getName())) {
                    name = i;
                }
            }
            final List<String> names = new ArrayList<>();
            for (final Row row : listing.getRows()) {
                names.add(name < 0 ? "?" : String.valueOf(row.getValue(name)));
            }
            return "rows " + names;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String noObject() {
        return "SQL compilation error:|Object does not exist, or operation cannot be performed.";
    }

    private static String cannotShow(final String kind, final String scope) {
        return "SQL compilation error:|Unsupported statement type 'Cannot show objects of type " + kind + " in "
            + scope + "'.";
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theSchemaListingsTakeABareAccountScope() {
        engine.execute("CREATE OR REPLACE FUNCTION fl_acc_scope_fn(x INT) RETURNS INT AS 'x + 1'");
        engine.execute("CREATE OR REPLACE PROCEDURE fl_acc_scope_proc() RETURNS INT LANGUAGE SQL"
            + " AS 'BEGIN RETURN 1; END'");
        assertEquals("rows [FL_ACC_SCOPE_FN]", answer("SHOW USER FUNCTIONS LIKE 'FL_ACC_SCOPE_FN' IN ACCOUNT"));
        assertEquals("rows [FL_ACC_SCOPE_FN]", answer("SHOW FUNCTIONS LIKE 'FL_ACC_SCOPE_FN' IN ACCOUNT"));
        assertEquals("rows [FL_ACC_SCOPE_FN]", answer("SHOW TERSE USER FUNCTIONS LIKE 'FL_ACC_SCOPE_FN' IN ACCOUNT"));
        assertEquals("rows [FL_ACC_SCOPE_PROC]", answer("SHOW PROCEDURES LIKE 'FL_ACC_SCOPE_PROC' IN ACCOUNT"));
        assertEquals("rows [FL_ACC_SCOPE_PROC]", answer("SHOW USER PROCEDURES LIKE 'FL_ACC_SCOPE_PROC' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW MATERIALIZED VIEWS LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW HYBRID TABLES LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW ICEBERG TABLES LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW HYBRID TABLES LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT LIMIT 1"));
    }

    @Test
    public void theAccountLevelListingsTakeTheAccountScope() {
        assertEquals("rows [TEST_DB]", answer("SHOW DATABASES LIKE 'TEST_DB' IN ACCOUNT"));
        assertEquals("rows [TEST_DB]", answer("SHOW TERSE DATABASES LIKE 'TEST_DB' IN ACCOUNT LIMIT 1"));
        assertEquals("rows [PUBLIC]", answer("SHOW ROLES LIKE 'PUBLIC' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW WAREHOUSES LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW USERS LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT"));
        assertEquals("rows []", answer("SHOW COMPUTE POOLS LIKE 'FL_ACC_SCOPE_NONE%' IN ACCOUNT"));
        assertTrue(answer("SHOW TRANSACTIONS IN ACCOUNT").startsWith("rows"));
        assertTrue(answer("SHOW LOCKS IN ACCOUNT").startsWith("rows"));
    }

    @Test
    public void anAccountNamedAfterTheScopeIsNoObject() {
        assertEquals(noObject(), answer("SHOW DATABASES IN ACCOUNT abc"));
        assertEquals(noObject(), answer("SHOW DATABASES IN ACCOUNT test_db"));
        assertEquals(noObject(), answer("SHOW DATABASES IN ACCOUNT abc.def"));
        assertEquals(noObject(), answer("SHOW DATABASES IN abc"));
        assertEquals(noObject(), answer("SHOW WAREHOUSES IN ACCOUNT IDENTIFIER('abc')"));
        assertEquals(noObject(), answer("SHOW USERS IN ACCOUNT \"abc\""));
        assertEquals(noObject(), answer("SHOW LOCKS IN ACCOUNT abc"));
        assertEquals(noObject(), answer("SHOW LOCKS IN ACCOUNT abc.def"));
        assertEquals(noObject(), answer("SHOW TRANSACTIONS IN ACCOUNT abc"));
        assertEquals(hinted("SQL compilation error:|Account 'ABC' does not exist or not authorized."),
            answer("SHOW COMPUTE POOLS IN ACCOUNT abc"));
        assertEquals(hinted("SQL compilation error:|Account 'DEF' does not exist or not authorized."),
            answer("SHOW COMPUTE POOLS IN ACCOUNT abc.def"));
        assertEquals(hinted("SQL compilation error:|Account '\"abc\"' does not exist or not authorized."),
            answer("SHOW COMPUTE POOLS IN \"abc\""));
    }

    @Test
    public void aContainerScopeHoldsNoAccountLevelObject() {
        assertEquals(cannotShow("DATABASE", "DATABASE"), answer("SHOW DATABASES IN DATABASE"));
        assertEquals(cannotShow("DATABASE", "DATABASE"), answer("SHOW DATABASES IN DATABASE test_db"));
        assertEquals(cannotShow("DATABASE", "SCHEMA"), answer("SHOW DATABASES HISTORY IN SCHEMA abc"));
        assertEquals(cannotShow("DATABASE", "TABLE"), answer("SHOW DATABASES IN TABLE"));
        assertEquals(cannotShow("WAREHOUSE", "DATABASE"), answer("SHOW WAREHOUSES IN DATABASE abc"));
        assertEquals(cannotShow("USER", "TABLE"), answer("SHOW USERS IN TABLE t1"));
        assertEquals(cannotShow("ROLE", "SCHEMA"), answer("SHOW ROLES IN SCHEMA"));
        assertEquals(cannotShow("COMPUTE POOL", "DATABASE"), answer("SHOW COMPUTE POOLS IN DATABASE x"));
        assertEquals(cannotShow("DATABASE", "INSTANCE"), answer("SHOW DATABASES IN abc y"));
        assertEquals("SQL compilation error: Object type or Class 'ABC' does not exist or not authorized.",
            answer("SHOW ROLES IN abc y"));
    }

    @Test
    public void rolesInADatabaseAreTheDatabasesOwn() {
        final ResultSet roles = engine.executeQuery("SHOW ROLES IN DATABASE test_db");
        assertEquals(0, roles.getRowCount());
        assertTrue(roles.getColumnIndex("granted_to_database_roles") >= 0);
        assertEquals("rows []", answer("SHOW ROLES LIKE 'x' IN DATABASE"));
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCH' does not exist or not authorized."),
            answer("SHOW ROLES IN DATABASE nosuch"));
        assertEquals("Unsupported feature 'SHOW TERSE DATABASE ROLES'.", answer("SHOW TERSE ROLES IN DATABASE"));
    }

    @Test
    public void theShapeIsJudgedBeforeTheLimitAndTheNameAfterIt() {
        final String zero = "page size \"0\" must be greater than 0 in limit clause";
        assertEquals(cannotShow("DATABASE", "DATABASE"), answer("SHOW DATABASES IN DATABASE LIMIT 0"));
        assertEquals(cannotShow("USER", "SCHEMA"), answer("SHOW USERS IN SCHEMA LIMIT 0"));
        assertEquals(zero, answer("SHOW DATABASES IN abc LIMIT 0"));
        assertEquals(zero, answer("SHOW DATABASES IN ACCOUNT abc LIMIT 0"));
        assertEquals(zero, answer("SHOW COMPUTE POOLS IN abc LIMIT 0"));
        assertEquals(zero, answer("SHOW ROLES IN DATABASE nosuch LIMIT 0"));
        assertEquals(zero, answer("SHOW MATERIALIZED VIEWS IN ACCOUNT LIMIT 0"));
        assertEquals(zero, answer("SHOW FUNCTIONS IN ACCOUNT LIMIT 0"));
    }

    @Test
    public void locksAndTransactionsTakeNoModifier() {
        assertEquals(syntax(18, "LIKE"), answer("SHOW TRANSACTIONS LIKE 'x'"));
        assertEquals(syntax(28, "1"), answer("SHOW LOCKS IN ACCOUNT LIMIT 1"));
        assertEquals(syntax(35, "1"), answer("SHOW TRANSACTIONS IN ACCOUNT LIMIT 1 FROM 'x'"));
        assertEquals(syntax(34, "'A'"), answer("SHOW LOCKS IN ACCOUNT STARTS WITH 'A'"));
        assertEquals(syntax(33, "LIMIT"), answer("SHOW TRANSACTIONS IN ACCOUNT abc LIMIT 1"));
        assertEquals(syntax(27, "PRIVILEGES"), answer("SHOW LOCKS IN ACCOUNT WITH PRIVILEGES USAGE"));
        assertEquals(noObject(), answer("SHOW LOCKS IN ACCOUNT LIMIT"));
    }
}
