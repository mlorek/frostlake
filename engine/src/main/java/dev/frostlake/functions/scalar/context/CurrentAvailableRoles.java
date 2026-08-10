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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.User;
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.StringType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;

/**
 * CURRENT_AVAILABLE_ROLES() — a JSON array (as text) of every role the session can use: the active
 * roles, the roles granted to the current user, and the closure of roles granted to those roles.
 * Snowflake returns the names alphabetically in a compact JSON array string.
 */
public class CurrentAvailableRoles extends BuiltInFunction {

    private final Catalog catalog;
    private final SessionContext sessionContext;

    public CurrentAvailableRoles(final Catalog catalog, final SessionContext sessionContext) {
        super("CURRENT_AVAILABLE_ROLES", StringType.VARCHAR);
        this.catalog = catalog;
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final TreeSet<String> roles = new TreeSet<>();
        if (sessionContext.getCurrentRole() != null) {
            roles.add(sessionContext.getCurrentRole());
        }
        roles.addAll(sessionContext.getActiveRoles());
        try {
            final User user = catalog.getUser(sessionContext.getCurrentUser());
            roles.addAll(user.getGrantedRoles());
        } catch (final RuntimeException noSuchUser) {
            // A session user without a catalog entry still has its active roles.
        }
        // Close over the role hierarchy: roles granted to already-available roles are usable too.
        final Deque<String> toCheck = new ArrayDeque<>(roles);
        while (!toCheck.isEmpty()) {
            final String roleName = toCheck.poll();
            try {
                final Role role = catalog.getRole(roleName);
                for (final String granted : role.getGrantedRoles()) {
                    if (roles.add(granted)) {
                        toCheck.add(granted);
                    }
                }
            } catch (final RuntimeException noSuchRole) {
                // An active role that is not in the catalog contributes no further grants.
            }
        }
        final StringBuilder sb = new StringBuilder("[");
        for (final String role : roles) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append('"').append(role).append('"');
        }
        return sb.append("]").toString();
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
