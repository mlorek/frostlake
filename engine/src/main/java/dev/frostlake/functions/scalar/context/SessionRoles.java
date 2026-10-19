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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.security.SessionContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The roles a session acts with: its current role, its secondary roles, every role granted to one of those,
 * directly or through another, and PUBLIC, which every role holds. A role held as a secondary role alone is in
 * the session, and with USE SECONDARY ROLES ALL so is every role granted to the session's user (live-verified).
 * Inside a stored procedure that runs with owner's rights the roles acting are the owner's and the ones it
 * inherits, none of the caller's (documented).
 */
final class SessionRoles {

    private SessionRoles() {
    }

    /**
     * The session's roles.
     *
     * @param catalog        the catalog holding the roles, or null
     * @param sessionContext the session, or null for the default role
     * @return the roles, by name
     */
    static Set<Role> held(final Catalog catalog, final SessionContext sessionContext) {
        final Set<Role> held = new LinkedHashSet<>();
        if (catalog == null) {
            return held;
        }
        final Deque<String> pending = new ArrayDeque<>();
        pending.add(current(sessionContext));
        if (sessionContext == null || sessionContext.getExecutableOwner() == null) {
            pending.addAll(secondary(catalog, sessionContext));
        }
        pending.add("PUBLIC");
        final Set<String> seen = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            final String name = pending.poll();
            if (name == null || !seen.add(name)) {
                continue;
            }
            for (final Role candidate : catalog.getAllRoles()) {
                if (candidate.getName().equals(name)) {
                    held.add(candidate);
                    pending.addAll(candidate.getGrantedRoles());
                }
            }
        }
        return held;
    }

    /**
     * The session's secondary roles: the ones USE SECONDARY ROLES named, or for ALL every role granted to the
     * session's user.
     */
    private static List<String> secondary(final Catalog catalog, final SessionContext sessionContext) {
        final List<String> roles = new ArrayList<>();
        final String asked = sessionContext == null ? "" : sessionContext.getSecondaryRolesValue();
        if (asked == null || asked.isEmpty()) {
            return roles;
        }
        if ("ALL".equals(asked)) {
            // The engine's own session runs as SYSTEM on behalf of the user CURRENT_USER() names.
            final String securityUser = sessionContext.getCurrentUser();
            final String user = "SYSTEM".equals(securityUser) ? sessionContext.getDisplayUser() : securityUser;
            if (catalog.hasUser(user)) {
                for (final String granted : catalog.getUser(user).getGrantedRoles()) {
                    roles.add(granted.toUpperCase(Locale.ROOT));
                }
            }
            return roles;
        }
        for (final String name : asked.split(",")) {
            roles.add(name.trim());
        }
        return roles;
    }

    /**
     * The session's current role, which inside a stored procedure that runs with owner's rights is the owner.
     *
     * @param sessionContext the session, or null for the default role
     * @return the role's name
     */
    static String current(final SessionContext sessionContext) {
        return sessionContext == null ? "SYSADMIN" : sessionContext.getCreatingRole();
    }
}
