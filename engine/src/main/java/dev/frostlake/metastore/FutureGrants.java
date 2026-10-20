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
import dev.frostlake.metastore.model.Parameter;
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
     * The future grants that cover a schema-level object about to be created, and the owner they give it. Working
     * this out changes nothing: the handler sets the owner on the object it builds and calls {@link CreationGrants#apply}
     * once the object is in its schema. A statement that adds nothing, as CREATE ... IF NOT EXISTS over an object
     * that is there, CREATE OR ALTER over one, or a CREATE refused after this was asked, leaves every grant on the
     * name as it was: the privileges on the existing object stay, and future grants apply only to objects created
     * afterwards (documented, FL-435).
     *
     * @param catalog    the catalog holding the roles
     * @param objectKind the object's kind, singular and upper-case (TABLE, VIEW, SEQUENCE, ...)
     * @param schema     the schema the object is created in
     * @param objectName the object's own name, unqualified
     * @return the new object's owner and the grants to apply once it is added
     */
    public static CreationGrants ofNew(final Catalog catalog, final String objectKind, final Schema schema,
                                 final String objectName) {
        return ofNew(catalog, objectKind, objectKind, schema, objectName);
    }

    /**
     * {@link #ofNew} for an object the future grants of one kind cover whose privileges are recorded under
     * another: an event table takes the grants ON FUTURE EVENT TABLES and holds them as a table's.
     *
     * @param catalog      the catalog holding the roles
     * @param futureKind   the kind the future grants name, singular and upper-case (EVENT_TABLE, ...)
     * @param recordedKind the kind its privileges are recorded under (TABLE, ...)
     * @param schema       the schema the object is created in
     * @param objectName   the object's own name, unqualified
     * @return the new object's owner and the grants to apply once it is added
     */
    public static CreationGrants ofNew(final Catalog catalog, final String futureKind, final String recordedKind,
                                 final Schema schema, final String objectName) {
        final String databaseName = schema == null ? null : schema.getDatabaseName();
        return ofNewNamed(catalog, futureKind, recordedKind, schema, objectName,
            databaseName == null ? null : QualifiedName.join(databaseName, schema.getName(), objectName));
    }

    /**
     * {@link #ofNew} for one overload of a routine, whose privileges are recorded under its signature
     * ({@link GrantNames#routine}): the overload starts with none of a dropped same-signature overload's privileges,
     * and its future grants are its own.
     *
     * @param catalog     the catalog holding the roles
     * @param objectKind  FUNCTION or PROCEDURE
     * @param schema      the schema the routine is created in
     * @param routineName the routine's name
     * @param parameters  the overload's parameters
     * @return the overload's owner and the grants to apply once it is added
     */
    public static CreationGrants ofNewRoutine(final Catalog catalog, final String objectKind, final Schema schema,
                                        final String routineName, final List<Parameter> parameters) {
        final String databaseName = schema == null ? null : schema.getDatabaseName();
        return ofNewNamed(catalog, objectKind, objectKind, schema, routineName,
            databaseName == null ? null : GrantNames.routine(schema, routineName, parameters));
    }

    private static CreationGrants ofNewNamed(final Catalog catalog, final String futureKind, final String objectKind,
                                       final Schema schema, final String objectName, final String qualified) {
        final List<Role> holders = new ArrayList<>();
        final List<FutureGrant> covering = new ArrayList<>();
        if (qualified == null) {
            return new CreationGrants(catalog, objectKind, null, false, holders, covering);
        }
        final String databaseName = schema.getDatabaseName();
        collect(catalog, futureKind, "SCHEMA", QualifiedName.join(databaseName, schema.getName()),
            holders, covering);
        if (covering.isEmpty()) {
            collect(catalog, futureKind, "DATABASE", databaseName, holders, covering);
        }
        // The privileges a dropped object of this name held are not the new object's (GrantNames). A temporary
        // table that takes a permanent one's name only hides it, and the permanent table keeps its privileges.
        final boolean takesName = !("TABLE".equals(objectKind) && schema.shadowedTable(objectName) != null);
        return new CreationGrants(catalog, objectKind, qualified, takesName, holders, covering);
    }

    /** The owner a set of future grants gives a new object: a future OWNERSHIP's holder, else the current role. */
    static String ownerAmong(final Catalog catalog, final List<Role> holders, final List<FutureGrant> covering) {
        String owner = catalog.currentRoleForOwner();
        for (int i = 0; i < covering.size(); i++) {
            if (Privilege.OWNERSHIP.name().equals(covering.get(i).getPrivilege())) {
                owner = nameOf(holders.get(i));
            }
        }
        return owner;
    }

    /**
     * Records the grants a set of future grants makes on one new object. A future OWNERSHIP names the owner; every
     * other privilege is granted to its holder IN THAT OWNER'S NAME, which is what SHOW GRANTS on the object reads
     * back as granted_by.
     */
    static void apply(final Catalog catalog, final String objectKind, final String qualified,
                      final String owner, final List<Role> holders, final List<FutureGrant> covering) {
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
    }

    /**
     * The owner a newly created DATABASE-level object takes — a schema, whose narrowest future-grant
     * scope is the database itself — applying every other future grant that covers it. The handlers ask it once the
     * schema is added, so a schema that is there already keeps its grants.
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
        final String qualified = QualifiedName.join(databaseName, objectName);
        GrantNames.created(catalog, objectKind, qualified);
        final String owner = ownerAmong(catalog, holders, covering);
        apply(catalog, objectKind, qualified, owner, holders, covering);
        return owner;
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
