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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.GrantedObject;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * GRANT OWNERSHIP moving an object to another role, as live moves it: the role becomes the object's owner, the one
 * SHOW TABLES, SHOW GRANTS and INFORMATION_SCHEMA name. A grant the object carries blocks the move unless the
 * statement says what becomes of it: COPY CURRENT GRANTS keeps every grant, recorded from then on as granted by the
 * new owner, and REVOKE CURRENT GRANTS drops them. A move to the role that already owns the object is never blocked
 * (live-verified).
 */
final class OwnershipTransfer {

    private static final String DEPENDENT_GRANT = "SQL execution error: Dependent grant of privilege '%s' on "
        + "securable '%s' to role '%s' exists.  It must be revoked first.  More than one dependent grant may exist: "
        + "use 'SHOW GRANTS' command to view them.  To revoke all dependent grants while transferring object "
        + "ownership, use convenience command 'GRANT OWNERSHIP ON <target_objects> TO <target_role> REVOKE CURRENT "
        + "GRANTS'.";

    private final Catalog catalog;
    private final QueryExecutor executor;

    OwnershipTransfer(final Catalog catalog, final QueryExecutor executor) {
        this.catalog = catalog;
        this.executor = executor;
    }

    /**
     * Move one object to a role.
     *
     * @param objectType  the kind the statement names, upper-case
     * @param parts       the object's name, part by part
     * @param arity       the number of argument types written after a routine's name, or null for none
     * @param writtenName the name as the statement writes it, one of those a grant may be recorded under
     * @param roleName    the role that is to own the object
     * @param tail        COPY or REVOKE CURRENT GRANTS, or null
     * @return false for a kind whose owner is not recorded here, whose ownership stays a granted privilege
     */
    boolean transfer(final String objectType, final String[] parts, final Integer arity, final String writtenName,
                     final String roleName, final FrostlakeParser.CurrentGrantsContext tail) {
        final GrantedObject granted = executor.grantedObject(objectType, parts, arity);
        if (granted == null) {
            return false;
        }
        final String owner = catalog.getRole(roleName).getName();
        final List<String> spellings = new ArrayList<>();
        spellings.add(writtenName);
        spellings.addAll(granted.getSpellings());
        final List<Role> holders = new ArrayList<>(catalog.getAllRoles());
        Collections.sort(holders, new Comparator<Role>() {
            @Override
            public int compare(final Role left, final Role right) {
                return left.getName().compareTo(right.getName());
            }
        });
        if (tail == null && !owner.equals(granted.getOwner())) {
            rejectDependentGrant(objectType, granted, spellings, holders);
        }
        final boolean revoke = tail != null && tail.REVOKE_CURRENT_GRANTS() != null;
        for (final Role holder : holders) {
            for (final String spelling : spellings) {
                for (final Privilege privilege : new ArrayList<>(holder.getPrivileges(objectType, spelling))) {
                    // An OWNERSHIP recorded as a privilege is superseded by the owner itself.
                    if (privilege == Privilege.OWNERSHIP || revoke) {
                        holder.revokePrivilege(objectType, spelling, privilege);
                    } else {
                        holder.grantPrivilege(objectType, spelling, privilege, owner);
                    }
                }
            }
        }
        executor.assignOwner(objectType, parts, arity, owner);
        return true;
    }

    /**
     * Move every object of one kind in a schema, or in each schema of a database, in name order. The first object
     * a grant blocks stops the statement, with the objects before it already moved (live-verified).
     *
     * <p>SCHEMAS is the one kind whose objects are the schemas themselves, so it is scoped to a database and moves
     * each of them — INFORMATION_SCHEMA excepted, which carries no owner on any account.
     *
     * @param bulkType the kinds the statement names, plural
     * @param scope    the schema or database they live in
     * @param roleName the role that is to own them
     * @param tail     COPY or REVOKE CURRENT GRANTS, or null
     * @return false for a kind or scope whose owners are not moved here, whose grant stays recorded as a privilege
     */
    boolean transferAll(final String bulkType, final FrostlakeParser.BulkScopeContext scope, final String roleName,
                        final FrostlakeParser.CurrentGrantsContext tail) {
        if (scope.qualifiedName() == null) {
            return false;
        }
        final String kind = bulkType.endsWith("S") ? bulkType.substring(0, bulkType.length() - 1) : bulkType;
        final String[] scopeParts = ParseTreeText.qualifiedNameParts(scope.qualifiedName());
        if ("SCHEMA".equals(kind)) {
            return transferSchemas(scope, scopeParts, roleName, tail);
        }
        final List<Schema> schemas = new ArrayList<>();
        if (scope.SCHEMA() != null) {
            schemas.add(catalog.resolveSchema(QualifiedName.of(scopeParts)));
        } else {
            schemas.addAll(catalog.databaseExact(scopeParts[0]).getAllSchemas());
        }
        boolean moved = false;
        for (final Schema schema : schemas) {
            final List<String> names = AccessControlHandler.objectNames(schema, kind);
            Collections.sort(names);
            for (final String name : names) {
                final String[] parts = {schema.getDatabaseName(), schema.getName(), name};
                moved |= transfer(kind, parts, null, QualifiedName.join(parts), roleName, tail);
            }
        }
        // An empty scope moves nothing and is still the statement's whole effect, so it must not fall back
        // to recording a privilege — but a kind this engine cannot move at all still has to.
        return moved || movableKind(kind);
    }

    /** Move every schema of a database, in name order; INFORMATION_SCHEMA has no owner to move. */
    private boolean transferSchemas(final FrostlakeParser.BulkScopeContext scope, final String[] scopeParts,
                                    final String roleName, final FrostlakeParser.CurrentGrantsContext tail) {
        if (scope.DATABASE() == null) {
            return false;
        }
        final String database = catalog.databaseExact(scopeParts[0]).getName();
        final List<String> names = new ArrayList<>();
        for (final Schema schema : catalog.databaseExact(scopeParts[0]).getAllSchemas()) {
            if (!"INFORMATION_SCHEMA".equalsIgnoreCase(schema.getName())) {
                names.add(schema.getName());
            }
        }
        Collections.sort(names);
        for (final String name : names) {
            final String[] parts = {database, name};
            transfer("SCHEMA", parts, null, QualifiedName.join(parts), roleName, tail);
        }
        return true;
    }

    /** Whether an owner of that kind is recorded here at all, so an empty scope is still a move. */
    private static boolean movableKind(final String kind) {
        return "TABLE".equals(kind) || "VIEW".equals(kind) || "SEQUENCE".equals(kind)
            || "STAGE".equals(kind) || "STREAM".equals(kind) || "FUNCTION".equals(kind)
            || "PROCEDURE".equals(kind) || "TASK".equals(kind) || "PIPE".equals(kind);
    }

    /** The first grant, in SHOW GRANTS order, that blocks a move with no COPY or REVOKE CURRENT GRANTS. */
    private static void rejectDependentGrant(final String objectType, final GrantedObject granted,
                                             final List<String> spellings, final List<Role> holders) {
        for (final Role holder : holders) {
            final List<String> held = new ArrayList<>();
            for (final String spelling : spellings) {
                for (final Privilege privilege : holder.getPrivileges(objectType, spelling)) {
                    if (privilege != Privilege.OWNERSHIP) {
                        held.add(privilege.name());
                    }
                }
            }
            if (!held.isEmpty()) {
                Collections.sort(held);
                throw new RuntimeException(String.format(DEPENDENT_GRANT, Privilege.valueOf(held.get(0)).displayName(),
                    granted.getName(), holder.getName()));
            }
        }
    }
}
