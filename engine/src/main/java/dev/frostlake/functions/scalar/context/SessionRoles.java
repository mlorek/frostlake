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
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The roles a session acts with: its current role, every role granted to that one, directly or through another,
 * and PUBLIC, which every role holds.
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
     * The session's current role.
     *
     * @param sessionContext the session, or null for the default role
     * @return the role's name
     */
    static String current(final SessionContext sessionContext) {
        return sessionContext == null ? "SYSADMIN" : sessionContext.getCurrentRole();
    }
}
