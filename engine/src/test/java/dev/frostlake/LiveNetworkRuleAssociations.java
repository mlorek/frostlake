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

package dev.frostlake;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * A NETWORK RULE that a NETWORK POLICY still names cannot be dropped with its database: the account
 * answers "Cannot drop database TEST_DB as it includes network rule - policy associations." — so one
 * test that leaves such a pair behind stops every later test, which recreates {@code test_db} to set
 * itself up.
 *
 * <p>The association belongs to the POLICY, not to the database, which is why dropping the database
 * cannot clear it. This unsets the rule list of every policy that names a rule in the database, and
 * leaves the policies themselves alone — one may be the account's.
 */
final class LiveNetworkRuleAssociations {

    /** What the account answers when an association stands in the way. */
    static final String REFUSAL = "network rule - policy associations";

    private LiveNetworkRuleAssociations() {
    }

    /**
     * Unset the rule list of every network policy naming a rule in {@code database}.
     *
     * @param connection the live session
     * @param database   the database whose rules block the drop
     */
    static void clearFor(final Connection connection, final String database) {
        final List<String> policies = new ArrayList<String>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW NETWORK POLICIES")) {
            while (rs.next()) {
                policies.add(rs.getString("name"));
            }
        } catch (final SQLException noPolicies) {
            return;
        }
        for (final String policy : policies) {
            if (namesARuleIn(connection, policy, database)) {
                // BOTH lists hold associations, and either one alone keeps the database alive. Clearing
                // only the allowed list left a policy whose BLOCKED list named a rule of the database,
                // and every test after it failed to replace test_db.
                execute(connection, "ALTER NETWORK POLICY " + policy + " UNSET ALLOWED_NETWORK_RULE_LIST");
                execute(connection, "ALTER NETWORK POLICY " + policy + " UNSET BLOCKED_NETWORK_RULE_LIST");
            }
        }
    }

    /** Whether {@code policy}'s allowed-rule list names a rule of {@code database}. */
    private static boolean namesARuleIn(final Connection connection, final String policy,
                                        final String database) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("DESCRIBE NETWORK POLICY " + policy)) {
            while (rs.next()) {
                final String name = rs.getString(1);
                final String value = rs.getString(2);
                if (name != null && name.toUpperCase().contains("RULE_LIST")
                        && value != null && value.toUpperCase().contains(database.toUpperCase() + ".")) {
                    return true;
                }
            }
        } catch (final SQLException undescribable) {
            return false;
        }
        return false;
    }

    private static void execute(final Connection connection, final String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (final SQLException refused) {
            // Best effort: the next drop reports the association if this did not clear it.
        }
    }
}
