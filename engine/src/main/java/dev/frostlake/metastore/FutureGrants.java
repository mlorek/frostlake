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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.FutureGrant;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * The FUTURE grants that cover an object being created, applied to it as it is created.
 *
 * <p>A future grant is a standing instruction rather than a record: {@code GRANT OWNERSHIP ON FUTURE
 * TABLES IN SCHEMA s TO ROLE r} makes {@code r} the owner of every table created in {@code s}
 * afterwards, and a future SELECT grants that privilege on each one. The grants that fire are read
 * off the roles at creation time, so revoking one stops it applying to anything created later
 * without touching what it has already produced.
 *
 * <p>Two scopes can cover the same object and the NARROWER one wins WHOLE: with a future OWNERSHIP on
 * the schema to one role and another on the database to a second, a table created in that schema takes
 * the schema's role, and a table created in a sibling schema — which the schema grant does not reach —
 * takes the database's. The database scope is consulted only when the schema scope names no grant at
 * all for that kind.
 *
 * <p>The grants a future grant produces are recorded in the NEW OWNER's name, not the creator's:
 * {@code SHOW GRANTS ON TABLE} over a table created this way reads OWNERSHIP to the owning role
 * granted by itself, and the future SELECT beside it granted by that same owner.
 */
public final class FutureGrants {

    private FutureGrants() {
    }

    /**
     * The owner a newly created schema-level object takes, applying every other future grant that
     * covers it.
     *
     * @param catalog    the catalog holding the roles
     * @param objectKind the object's kind, singular and upper-case (TABLE, VIEW, SEQUENCE, ...)
     * @param schema     the schema the object is created in
     * @param objectName the object's own name, unqualified
     * @return the role that owns the object: a future OWNERSHIP grant's holder, else the current role
     */
    public static String ownerOfNew(final Catalog catalog, final String objectKind,
                                    final Schema schema, final String objectName) {
        final String databaseName = schema == null ? null : schema.getDatabaseName();
        if (databaseName == null) {
            return catalog.currentRoleForOwner();
        }
        final List<Role> holders = new ArrayList<>();
        final List<FutureGrant> covering = new ArrayList<>();
        collect(catalog, objectKind, "SCHEMA", QualifiedName.join(databaseName, schema.getName()),
            holders, covering);
        if (covering.isEmpty()) {
            collect(catalog, objectKind, "DATABASE", databaseName, holders, covering);
        }
        return apply(catalog, objectKind,
            QualifiedName.join(databaseName, schema.getName(), objectName), holders, covering);
    }

    /**
     * Records the grants a set of future grants makes on one new object, and answers its owner. A
     * future OWNERSHIP names the owner; every other privilege is granted to its holder IN THAT
     * OWNER'S NAME, which is what SHOW GRANTS on the object reads back as granted_by.
     */
    private static String apply(final Catalog catalog, final String objectKind, final String qualified,
                                final List<Role> holders, final List<FutureGrant> covering) {
        String owner = catalog.currentRoleForOwner();
        for (int i = 0; i < covering.size(); i++) {
            if (Privilege.OWNERSHIP.name().equals(covering.get(i).getPrivilege())) {
                owner = nameOf(holders.get(i));
            }
        }
        for (int i = 0; i < covering.size(); i++) {
            final FutureGrant grant = covering.get(i);
            final Privilege privilege = privilegeOf(grant.getPrivilege());
            if (privilege == null) {
                continue;
            }
            holders.get(i).grantPrivilege(objectKind, qualified, privilege, owner);
            if (grant.hasGrantOption() && privilege != Privilege.OWNERSHIP) {
                holders.get(i).setGrantOption(objectKind, qualified, privilege, true);
            }
        }
        return owner;
    }

    /**
     * The owner a newly created DATABASE-level object takes — a schema, whose narrowest future-grant
     * scope is the database itself — applying every other future grant that covers it.
     *
     * @param catalog      the catalog holding the roles
     * @param objectKind   the object's kind, singular and upper-case
     * @param databaseName the database the object is created in
     * @param objectName   the object's own name, unqualified
     * @return the role that owns the object: a future OWNERSHIP grant's holder, else the current role
     */
    public static String ownerOfNewInDatabase(final Catalog catalog, final String objectKind,
                                              final String databaseName, final String objectName) {
        if (databaseName == null) {
            return catalog.currentRoleForOwner();
        }
        final List<Role> holders = new ArrayList<>();
        final List<FutureGrant> covering = new ArrayList<>();
        collect(catalog, objectKind, "DATABASE", databaseName, holders, covering);
        return apply(catalog, objectKind, QualifiedName.join(databaseName, objectName), holders, covering);
    }

    /** Every future grant on that kind in that one scope, with the role holding each. */
    private static void collect(final Catalog catalog, final String objectKind, final String scopeKind,
                                final String scopeName, final List<Role> holders,
                                final List<FutureGrant> covering) {
        final List<Role> candidates = new ArrayList<>(catalog.getAllRoles());
        for (final Database database : catalog.getAllDatabases()) {
            candidates.addAll(database.getDatabaseRoles());
        }
        for (final Role role : candidates) {
            for (final FutureGrant grant : role.getFutureGrants()) {
                if (objectKind.equals(grant.getObjectKind()) && scopeKind.equals(grant.getScopeKind())
                        && scopeName.equalsIgnoreCase(grant.getScopeName())) {
                    holders.add(role);
                    covering.add(grant);
                }
            }
        }
    }

    /** A database role answers to its qualified name, an account role to its own. */
    private static String nameOf(final Role role) {
        return role instanceof DatabaseRole ? ((DatabaseRole) role).getQualifiedName() : role.getName();
    }

    /** The privilege a future grant names, or null when it is not one this engine models. */
    private static Privilege privilegeOf(final String name) {
        for (final Privilege privilege : Privilege.values()) {
            if (privilege.name().equalsIgnoreCase(name)) {
                return privilege;
            }
        }
        return null;
    }
}
