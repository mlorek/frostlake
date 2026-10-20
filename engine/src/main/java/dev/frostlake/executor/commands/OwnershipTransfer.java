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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.security.SecurityManager;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * GRANT OWNERSHIP moving an object to another role, as live moves it: the role becomes the object's owner, the one
 * SHOW TABLES, SHOW GRANTS and INFORMATION_SCHEMA name. A grant the object carries blocks the move unless the
 * statement says what becomes of it: COPY CURRENT GRANTS keeps every grant, recorded from then on as granted by the
 * new owner, and REVOKE CURRENT GRANTS drops them. A move to the role that already owns the object is never blocked
 * (live-verified).
 */
final class OwnershipTransfer {

    private static final String DEPENDENT_GRANT = "SQL execution error: Dependent grant of privilege '%s' on "
        + "securable '%s' to %s '%s' exists.  It must be revoked first.  More than one dependent grant may exist: "
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
        return move(objectType, parts, arity, writtenName, catalog.getRoleExact(roleName).getName(), tail);
    }

    /**
     * Move one object to a database role, which then owns it under its qualified name: the roles that held it act
     * as its owner no more, and a session acts as owner only while it holds the database role (live-verified).
     *
     * @param objectType  the kind the statement names, upper-case
     * @param parts       the object's name, part by part
     * @param arity       the number of argument types written after a routine's name, or null for none
     * @param writtenName the name as the statement writes it, one of those a grant may be recorded under
     * @param role        the database role that is to own the object
     * @param tail        COPY or REVOKE CURRENT GRANTS, or null
     * @return false for a kind whose owner is not recorded here, whose ownership stays a granted privilege
     */
    boolean transfer(final String objectType, final String[] parts, final Integer arity, final String writtenName,
                     final DatabaseRole role, final FrostlakeParser.CurrentGrantsContext tail) {
        return move(objectType, parts, arity, writtenName, role.getQualifiedName(), tail);
    }

    /**
     * What moving a stream or a paused pipe asks of the rights that move it (live-verified): SELECT on the table a
     * stream reads, refused {@code To grant use role with SELECT privilege on the target table DB.SCHEMA.T}, and
     * INSERT on the table a paused pipe loads, which may still hold files to load: {@code Pipe may have pending files
     * that could be loaded into table when resumed. To do grant, use role with INSERT privilege on the target table
     * T}.
     *
     * @param objectType the kind the statement names, upper-case
     * @param parts      the object's name, part by part
     */
    private void requirePrerequisites(final String objectType, final String[] parts) {
        final SecurityManager security = executor.getSecurityManager();
        if (security == null || !"STREAM".equals(objectType) && !"PIPE".equals(objectType)) {
            return;
        }
        final String[] path = catalog.withoutAccount(parts, 3);
        final Schema schema = schemaOrNull(path.length == 3 ? path[0] : catalog.getCurrentDatabase(),
            path.length >= 2 ? path[path.length - 2] : catalog.getCurrentSchema());
        final String name = path[path.length - 1];
        if (schema == null) {
            return;
        }
        if ("STREAM".equals(objectType)) {
            if (!schema.hasStreamExact(name) || schema.getStream(name).getSourceType() != StreamSourceType.TABLE) {
                return;
            }
            final String[] source = tablePath(schema, QualifiedName.parse(schema.getStream(name).getSourceTableName())
                .parts());
            final Table table = tableOrNull(source);
            if (table != null && !security.holdsPrivilege(Privilege.SELECT, "TABLE", QualifiedName.join(source),
                    table.getOwner())) {
                throw new RuntimeException("To grant use role with SELECT privilege on the target table "
                    + QualifiedName.join(source));
            }
            return;
        }
        final Pipe pipe = pipeOrNull(schema, name);
        final FrostlakeParser.CopyIntoStatementContext copy = pipe == null || !pipe.isPaused() ? null
            : copyOf(pipe.getCopyStatement());
        if (copy == null || copy.qualifiedName() == null) {
            return;
        }
        final String[] target = tablePath(schema, ParseTreeText.qualifiedNameParts(copy.qualifiedName()));
        final Table table = tableOrNull(target);
        if (table != null && !security.holdsPrivilege(Privilege.INSERT, "TABLE", QualifiedName.join(target),
                table.getOwner())) {
            throw new RuntimeException("Pipe may have pending files that could be loaded into table when resumed. To "
                + "do grant, use role with INSERT privilege on the target table " + table.getName());
        }
    }

    private static Pipe pipeOrNull(final Schema schema, final String name) {
        try {
            return schema.getPipe(name);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    private Schema schemaOrNull(final String database, final String schema) {
        if (database == null || schema == null) {
            return null;
        }
        try {
            return catalog.getDatabase(database).getSchema(schema);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** A table's name completed to its full path from the schema the naming object lives in. */
    private String[] tablePath(final Schema home, final String[] written) {
        final String[] parts = catalog.withoutAccount(written, 3);
        if (parts.length == 3) {
            return parts;
        }
        if (parts.length == 2) {
            return new String[] {home.getDatabaseName(), parts[0], parts[1]};
        }
        return new String[] {home.getDatabaseName(), home.getName(), parts[0]};
    }

    private Table tableOrNull(final String[] path) {
        final Schema schema = schemaOrNull(path[0], path[1]);
        if (schema == null) {
            return null;
        }
        try {
            return schema.getTable(path[2]);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** A pipe's COPY statement as parsed, or null when its text is no COPY. */
    private static FrostlakeParser.CopyIntoStatementContext copyOf(final String text) {
        if (text == null) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            return parser.copyIntoStatement();
        } catch (final RuntimeException unparsable) {
            return null;
        }
    }

    private boolean move(final String objectType, final String[] parts, final Integer arity, final String writtenName,
                         final String owner, final FrostlakeParser.CurrentGrantsContext tail) {
        final GrantedObject granted = executor.grantedObject(objectType, parts, arity);
        if (granted == null) {
            return false;
        }
        requirePrerequisites(objectType, parts);
        final List<String> spellings = new ArrayList<>();
        spellings.add(writtenName);
        spellings.addAll(granted.getSpellings());
        final List<Role> holders = holders();
        final List<User> users = users();
        if (tail == null && !owner.equals(granted.getOwner())) {
            rejectDependentGrant(objectType, granted, spellings, holders, users);
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
        for (final User user : users) {
            for (final String spelling : spellings) {
                for (final Privilege privilege : new ArrayList<>(user.getPrivileges(objectType, spelling))) {
                    if (privilege == Privilege.OWNERSHIP || revoke) {
                        user.revokePrivilege(objectType, spelling, privilege);
                    } else {
                        user.grantPrivilege(objectType, spelling, privilege, owner);
                    }
                }
            }
        }
        executor.assignOwner(objectType, parts, arity, owner);
        return true;
    }

    /**
     * Every role an object's grant may be held by, in the order a dependent grant is reported in: the account roles
     * by name, then each database's roles by their qualified names. A database role's grant is an outbound grant of
     * the object like an account role's, which a move with no COPY or REVOKE CURRENT GRANTS finds in its way.
     */
    private List<Role> holders() {
        final List<Role> holders = new ArrayList<>(catalog.getAllRoles());
        Collections.sort(holders, new Comparator<Role>() {
            @Override
            public int compare(final Role left, final Role right) {
                return left.getName().compareTo(right.getName());
            }
        });
        final List<DatabaseRole> databaseRoles = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            databaseRoles.addAll(database.getDatabaseRoles());
        }
        Collections.sort(databaseRoles, new Comparator<DatabaseRole>() {
            @Override
            public int compare(final DatabaseRole left, final DatabaseRole right) {
                return left.getQualifiedName().compareTo(right.getQualifiedName());
            }
        });
        holders.addAll(databaseRoles);
        return holders;
    }

    /** Every user, by name: a privilege granted to a user is an outbound grant of the object too. */
    private List<User> users() {
        final List<User> users = new ArrayList<>(catalog.getAllUsers());
        Collections.sort(users, new Comparator<User>() {
            @Override
            public int compare(final User left, final User right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return users;
    }

    /**
     * Move every object of one kind in a schema, or in each schema of a database, in name order. The first object
     * a grant blocks stops the statement, with the objects before it already moved (live-verified).
     *
     * <p>SCHEMAS is the one kind whose objects are the schemas themselves, so it is scoped to a database and moves
     * each of them — INFORMATION_SCHEMA excepted, which carries no owner on any account.
     *
     * @param bulkType    the kinds the statement names, plural
     * @param schemaScope whether they live in a schema, where otherwise in a database
     * @param scopeParts  that schema's or database's canonical name parts
     * @param roleName    the role that is to own them
     * @param tail     COPY or REVOKE CURRENT GRANTS, or null
     * @return false for a kind or scope whose owners are not moved here, whose grant stays recorded as a privilege
     */
    boolean transferAll(final String bulkType, final boolean schemaScope, final String[] scopeParts,
                        final String roleName, final FrostlakeParser.CurrentGrantsContext tail) {
        final String kind = bulkType.endsWith("S") ? bulkType.substring(0, bulkType.length() - 1) : bulkType;
        if ("SCHEMA".equals(kind)) {
            return transferSchemas(schemaScope, scopeParts, roleName, tail);
        }
        final List<Schema> schemas = new ArrayList<>();
        if (schemaScope) {
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
    private boolean transferSchemas(final boolean schemaScope, final String[] scopeParts,
                                    final String roleName, final FrostlakeParser.CurrentGrantsContext tail) {
        if (schemaScope) {
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
            || "PROCEDURE".equals(kind) || "TASK".equals(kind) || "PIPE".equals(kind)
            || "MATERIALIZED_VIEW".equals(kind);
    }

    /**
     * The first grant, in SHOW GRANTS order, that blocks a move with no COPY or REVOKE CURRENT GRANTS: an account
     * role's, then a database role's, named by its qualified name, then a user's.
     */
    private static void rejectDependentGrant(final String objectType, final GrantedObject granted,
                                             final List<String> spellings, final List<Role> holders,
                                             final List<User> users) {
        for (final Role holder : holders) {
            final List<String> held = new ArrayList<>();
            for (final String spelling : spellings) {
                held.addAll(outbound(holder.getPrivileges(objectType, spelling)));
            }
            if (!held.isEmpty()) {
                final boolean databaseRole = holder instanceof DatabaseRole;
                throw dependentGrant(held, granted, databaseRole ? "database role" : "role",
                    databaseRole ? ((DatabaseRole) holder).getQualifiedName() : holder.getName());
            }
        }
        for (final User user : users) {
            final List<String> held = new ArrayList<>();
            for (final String spelling : spellings) {
                held.addAll(outbound(user.getPrivileges(objectType, spelling)));
            }
            if (!held.isEmpty()) {
                throw dependentGrant(held, granted, "user", user.getName());
            }
        }
    }

    /** The privileges of a holder's grant that block a move: every one but OWNERSHIP. */
    private static List<String> outbound(final Set<Privilege> privileges) {
        final List<String> held = new ArrayList<>();
        for (final Privilege privilege : privileges) {
            if (privilege != Privilege.OWNERSHIP) {
                held.add(privilege.name());
            }
        }
        return held;
    }

    /** The refusal naming a holder's first blocking privilege, in name order. */
    private static RuntimeException dependentGrant(final List<String> held, final GrantedObject granted,
                                                   final String holderKind, final String holderName) {
        Collections.sort(held);
        return new RuntimeException(String.format(DEPENDENT_GRANT, Privilege.valueOf(held.get(0)).displayName(),
            granted.getName(), holderKind, holderName));
    }
}
