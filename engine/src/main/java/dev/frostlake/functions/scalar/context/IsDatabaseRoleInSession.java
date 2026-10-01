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
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.BooleanType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * IS_DATABASE_ROLE_IN_SESSION(name) — TRUE when the session's current role holds the database role: granted to
 * it or to a role it inherits, directly or through other database roles. The name is {@code db.role}, or a bare
 * role name in the current database; a name no database role answers to is FALSE, as on the account. The bare
 * word NULL and a number are refused while the statement compiles (see the visitor's argument rules).
 */
public class IsDatabaseRoleInSession extends BuiltInFunction {

    private final Catalog catalog;
    private final SessionContext sessionContext;

    /**
     * @param catalog the catalog holding the roles
     * @param sessionContext the session whose current role is asked about
     */
    public IsDatabaseRoleInSession(final Catalog catalog, final SessionContext sessionContext) {
        super("IS_DATABASE_ROLE_IN_SESSION", BooleanType.BOOLEAN);
        this.catalog = catalog;
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            return null;
        }
        final String text = args.get(0).toString();
        final QualifiedName name;
        try {
            name = QualifiedName.parse(text);
        } catch (final RuntimeException notAName) {
            return Boolean.FALSE;
        }
        if (name.size() == 0 || name.size() > 2) {
            return Boolean.FALSE;
        }
        final String databaseName = name.size() >= 2 ? name.part(name.size() - 2) : catalog.getCurrentDatabase();
        Database database = null;
        try {
            database = databaseName == null ? null : catalog.getDatabase(databaseName);
        } catch (final RuntimeException noSuchDatabase) {
            return Boolean.FALSE;
        }
        final DatabaseRole wanted = database == null ? null
            : database.getDatabaseRole(name.last().toUpperCase(Locale.ROOT));
        if (wanted == null) {
            return Boolean.FALSE;
        }
        final String current = sessionContext == null ? "SYSADMIN" : sessionContext.getCurrentRole();
        final Set<String> seenRoles = new HashSet<>();
        final Set<String> heldDatabaseRoles = new HashSet<>();
        final Deque<String> roles = new ArrayDeque<>();
        final Deque<String> databaseRoles = new ArrayDeque<>();
        if (current != null) {
            roles.add(current.toUpperCase(Locale.ROOT));
        }
        while (!roles.isEmpty()) {
            final String roleName = roles.poll();
            if (!seenRoles.add(roleName)) {
                continue;
            }
            Role role = null;
            for (final Role candidate : catalog.getAllRoles()) {
                if (candidate.getName().equals(roleName)) {
                    role = candidate;
                }
            }
            if (role == null) {
                continue;
            }
            roles.addAll(role.getGrantedRoles());
            databaseRoles.addAll(role.getDatabaseRoleGrants().keySet());
        }
        while (!databaseRoles.isEmpty()) {
            final String qualified = databaseRoles.poll();
            if (!heldDatabaseRoles.add(qualified)) {
                continue;
            }
            final int dot = qualified.lastIndexOf('.');
            Database holderDatabase = null;
            try {
                holderDatabase = catalog.getDatabase(qualified.substring(0, dot));
            } catch (final RuntimeException dropped) {
                continue;
            }
            final DatabaseRole held = holderDatabase == null ? null
                : holderDatabase.getDatabaseRole(qualified.substring(dot + 1));
            if (held != null) {
                databaseRoles.addAll(held.getDatabaseRoleGrants().keySet());
            }
        }
        return heldDatabaseRoles.contains(wanted.getQualifiedName());
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
