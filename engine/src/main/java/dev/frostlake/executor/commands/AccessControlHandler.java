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

import dev.frostlake.executor.AccessControlListings;
import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.PrivilegeRefusal;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.UserLookup;
import dev.frostlake.metastore.AccountDirectory;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SystemDatabase;
import dev.frostlake.metastore.TagAssignments;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.Account;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.ManagedAccount;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Database roles (CREATE, ALTER, DROP, SHOW DATABASE ROLES, GRANT and REVOKE DATABASE ROLE), the grants listings
 * that name database roles, roles as securables and future grants, and the account records: CREATE, DROP and
 * UNDROP ACCOUNT, CREATE and DROP MANAGED ACCOUNT, SHOW MANAGED ACCOUNTS.
 *
 * <p>A database role lives in its database and is named {@code <database>.<role>}, or by its bare name relative to
 * the current database. Accounts are records: the engine serves one account, and CREATE ACCOUNT keeps the new
 * account's name, edition, region, comment and administrator — never its password — for SHOW ACCOUNTS to list.
 */
public final class AccessControlHandler {

    private static final List<String> EDITIONS = Arrays.asList("STANDARD", "ENTERPRISE", "BUSINESS_CRITICAL");
    private static final int MIN_GRACE_DAYS = 3;
    private static final int MAX_GRACE_DAYS = 90;

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;

    /**
     * @param catalog the catalog
     * @param queryExecutor the executor, for the SHOW listings it builds
     * @param visitor the visitor, for names and literals
     */
    public AccessControlHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                                final SQLCommandVisitor visitor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
    }

    /** Runs one access-control statement: a result set for a listing, null for the rest. */
    public Object handle(final FrostlakeParser.AccessControlStatementContext ctx) {
        if (ctx.CALLER() != null) {
            // A caller grant listing reads a LIMIT and its count as the other grant listings do (live-verified).
            final FrostlakeParser.ShowTailContext tail = ctx.showTail();
            ShowTailSyntax.requireCountOnly(ctx, tail);
            final ResultSet listing = new CallerGrantHandler(catalog, queryExecutor).show(ctx);
            return tail == null || tail.LIMIT() == null ? listing
                : firstRows(listing, ShowPageSize.require(tail.INTEGER_LITERAL().getText()));
        }
        final int first = ctx.getStart().getType();
        if (ctx.MANAGED() != null) {
            return managedAccount(ctx, first);
        }
        if (ctx.ACCOUNT() != null) {
            return account(ctx, first);
        }
        if (ctx.FUTURE() != null || ctx.GRANTS() != null) {
            // A grant listing reads a LIMIT and its count and no other listing modifier (live-verified).
            final FrostlakeParser.ShowTailContext tail = ctx.showTail();
            ShowTailSyntax.requireCountOnly(ctx, tail);
            final ResultSet listing = ctx.FUTURE() != null ? futureGrants(ctx) : grantsListing(ctx);
            return tail == null || tail.LIMIT() == null ? listing
                : firstRows(listing, ShowPageSize.require(tail.INTEGER_LITERAL().getText()));
        }
        if (ctx.ROLES() != null) {
            return showDatabaseRoles(ctx);
        }
        if (first == FrostlakeParser.CREATE) {
            return createDatabaseRole(ctx);
        } else if (first == FrostlakeParser.ALTER) {
            alterDatabaseRole(ctx);
        } else if (first == FrostlakeParser.DROP) {
            return dropDatabaseRole(ctx);
        }
        return null;
    }

    // ---- database roles --------------------------------------------------------------------------------

    /**
     * SHOW DATABASE ROLES [LIKE 'p'] IN [DATABASE] db [STARTS WITH 's'] [LIMIT n [FROM 'name']]. The pattern
     * matches whatever the case, the prefix and the FROM name read the names as they are. A listing without a
     * database, and a TERSE one, are features the account does not have (live-verified).
     */
    private ResultSet showDatabaseRoles(final FrostlakeParser.AccessControlStatementContext ctx) {
        if (!ctx.rolesDatabasePath.isEmpty()) {
            // A database is one part: a path names nothing, which is told after the page size and before anything
            // else.
            if (ctx.LIMIT() != null) {
                ShowPageSize.require(ctx.INTEGER_LITERAL().getText());
            }
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        if (ctx.TERSE() != null) {
            throw new RuntimeException("Unsupported feature 'SHOW TERSE DATABASE ROLES'.");
        }
        final String pattern = ctx.likePattern == null ? null : ParseTreeText.stringValue(ctx.likePattern);
        if (ctx.identifier() == null) {
            throw new RuntimeException("Unsupported feature 'SHOW DATABASE ROLES" + (pattern == null ? ""
                : " LIKE '" + pattern + "'") + "'; use 'SHOW DATABASE ROLES IN DATABASE <database_name>' instead.");
        }
        final Database database = catalog.databaseExact(visitor.getText(ctx.identifier()));
        if (SystemDatabase.is(database.getName()) && (ctx.startsPrefix != null || ctx.LIMIT() != null)) {
            // A shared database's roles are listed whole (live-verified).
            throw new RuntimeException("Unsupported feature 'SHOW DATABASE ROLES IN DATABASE <shared database>"
                + " [STARTS WITH <s>] [LIMIT <l>]'; use 'SHOW DATABASE ROLES IN DATABASE <shared database>' instead.");
        }
        ResultSet listing = new AccessControlListings(catalog).databaseRoles(database);
        final String prefix = ctx.startsPrefix == null ? null : ParseTreeText.stringValue(ctx.startsPrefix);
        if (pattern != null || prefix != null) {
            final int nameIndex = listing.getColumnIndex("name");
            final List<Row> kept = new ArrayList<>();
            for (final Row row : listing.getRows()) {
                final String name = String.valueOf(row.getValue(nameIndex));
                if ((pattern == null || ShowResultHelpers.matchesLike(name, pattern))
                        && (prefix == null || name.startsWith(prefix))) {
                    kept.add(row);
                }
            }
            listing = new ResultSet(listing.getColumns(), kept);
        }
        if (ctx.LIMIT() != null) {
            listing = limitFrom(listing, ShowPageSize.require(ctx.INTEGER_LITERAL().getText()),
                ctx.fromName == null ? null : ParseTreeText.stringValue(ctx.fromName));
        }
        return listing;
    }

    /**
     * The database and role a database role's name holds, canonical: {@code db.role}, or {@code role} in the
     * current database.
     */
    static String[] databaseRoleName(final Catalog catalog, final FrostlakeParser.QualifiedNameContext name) {
        return databaseRoleName(catalog, ParseTreeText.qualifiedNameParts(name));
    }

    /** {@link #databaseRoleName(Catalog, FrostlakeParser.QualifiedNameContext)} for a name resolved to its parts. */
    static String[] databaseRoleName(final Catalog catalog, final String[] parts) {
        if (parts.length > 2) {
            throw new RuntimeException(SqlCompilationError.of("Invalid database role name '"
                + QualifiedName.join(parts) + "'."));
        }
        final String database = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
        if (database == null) {
            throw NoCurrentDatabaseRefusal.forStatement();
        }
        return new String[] {database, parts[parts.length - 1]};
    }

    /**
     * A database role's name as a sentence and a listing spell it: with its database, each part quoted where it
     * could not have been written bare — {@code DB."dr_low"} for a role created under a quoted name, which is a
     * role of its own beside {@code DB.DR_LOW} (live-verified).
     */
    static String shown(final String database, final String role) {
        return SqlIdentifiers.spellAlreadyCanonicalPath(QualifiedName.join(database, role));
    }

    /**
     * The existing database role a name names.
     *
     * @throws RuntimeException the does-not-exist refusal when the database or the role is missing
     */
    static DatabaseRole databaseRole(final Catalog catalog, final FrostlakeParser.QualifiedNameContext name) {
        return databaseRole(catalog, ParseTreeText.qualifiedNameParts(name));
    }

    /**
     * The existing database role a name's parts name.
     *
     * @throws RuntimeException the does-not-exist refusal when the database or the role is missing
     */
    static DatabaseRole databaseRole(final Catalog catalog, final String[] name) {
        final String[] parts = databaseRoleName(catalog, name);
        final Database database = catalog.databaseExact(parts[0]);
        final DatabaseRole role = database.getDatabaseRole(parts[1]);
        if (role == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database role",
                QualifiedName.join(database.getName(), parts[1])));
        }
        return role;
    }

    /**
     * CREATE DATABASE ROLE. Its sentences name the role with its database, as the account words them: {@code Role
     * DB.R successfully created.}, {@code DB.R already exists, statement succeeded.} and {@code Object 'DB.R' already
     * exists.}
     */
    private ResultSet createDatabaseRole(final FrostlakeParser.AccessControlStatementContext ctx) {
        if (ctx.or_replace() != null && ctx.if_not_exists() != null) {
            throw new RuntimeException(SqlCompilationError.of("options IF NOT EXISTS and OR REPLACE are incompatible."));
        }
        final String[] parts = databaseRoleName(catalog, ctx.qualifiedName());
        final Database database = catalog.databaseExact(parts[0]);
        SystemDatabase.refuseChange(database.getName());
        final String qualified = shown(database.getName(), parts[1]);
        final DatabaseRole existing = database.getDatabaseRole(parts[1]);
        if (existing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(qualified);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + qualified + "' already exists."));
        }
        if (existing != null) {
            forgetDatabaseRole(existing);
            database.removeDatabaseRole(existing.getName());
        }
        final DatabaseRole role = new DatabaseRole(database.getName(), parts[1]);
        role.setOwner(catalog.currentRoleForOwner());
        if (ctx.commentClause() != null) {
            role.setComment(visitor.extractStringLiteral(ctx.commentClause().STRING_LITERAL()));
        }
        database.putDatabaseRole(role);
        return StatusResults.created("Role", qualified);
    }

    private void alterDatabaseRole(final FrostlakeParser.AccessControlStatementContext ctx) {
        final String[] parts = databaseRoleName(catalog, ctx.qualifiedName());
        final Database database = catalog.databaseExact(parts[0]);
        SystemDatabase.refuseChange(database.getName());
        final DatabaseRole role = database.getDatabaseRole(parts[1]);
        if (role == null) {
            if (ctx.if_exists() != null) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database role",
                QualifiedName.join(database.getName(), parts[1])));
        }
        final FrostlakeParser.DatabaseRoleActionContext action = ctx.databaseRoleAction();
        if (action.RENAME() != null) {
            final String[] target = ParseTreeText.qualifiedNameParts(action.qualifiedName());
            if (target.length > 2 || (target.length == 2 && !target[0].equals(database.getName()))) {
                throw new RuntimeException(SqlCompilationError.of(
                    "A database role cannot be renamed into another database."));
            }
            final String newName = target[target.length - 1];
            if (database.getDatabaseRole(newName) != null) {
                throw new RuntimeException(SqlCompilationError.of("Object '"
                    + shown(database.getName(), newName) + "' already exists."));
            }
            final String oldQualified = role.getQualifiedName();
            database.removeDatabaseRole(role.getName());
            role.rename(newName);
            database.putDatabaseRole(role);
            renameDatabaseRoleGrants(oldQualified, role.getQualifiedName());
        } else if (action.tagSet() != null) {
            applyTagSet(role, action.tagSet());
        } else if (action.tagUnset() != null) {
            applyTagUnset(role, action.tagUnset());
        } else if (action.UNSET() != null) {
            role.setComment(null);
        } else {
            role.setComment(visitor.extractStringLiteral(action.STRING_LITERAL()));
        }
    }

    /**
     * DROP DATABASE ROLE: {@code DB.R successfully dropped.}, the role named with its database; a missing role under
     * IF EXISTS keeps the kind-blind sentence, which names it as written. CASCADE and RESTRICT after the name change
     * nothing, and a PURGE is refused once the database has resolved and the role is there or IF EXISTS forgives its
     * absence (see {@link DropPurge}; live-verified).
     */
    private ResultSet dropDatabaseRole(final FrostlakeParser.AccessControlStatementContext ctx) {
        final String[] parts = databaseRoleName(catalog, ctx.qualifiedName());
        final Database database = catalog.databaseExact(parts[0]);
        SystemDatabase.refuseChange(database.getName());
        final DatabaseRole role = database.getDatabaseRole(parts[1]);
        if (DropPurge.written(ctx.dropBehavior()) && (role != null || ctx.if_exists() != null)) {
            throw DropPurge.refusal(ParseTreeText.qualifiedNameParts(ctx.qualifiedName()));
        }
        if (role == null) {
            if (ctx.if_exists() != null) {
                ConditionalDdlOutcome.dropSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database role",
                QualifiedName.join(database.getName(), parts[1])));
        }
        database.removeDatabaseRole(role.getName());
        forgetDatabaseRole(role);
        return StatusResults.dropped(shown(database.getName(), role.getName()));
    }

    /** Takes a dropped database role back from every role, database role and user that held it. */
    private void forgetDatabaseRole(final DatabaseRole role) {
        final String qualified = role.getQualifiedName();
        for (final Role holder : catalog.getAllRoles()) {
            holder.revokeDatabaseRole(qualified);
        }
        for (final User user : catalog.getAllUsers()) {
            user.revokeDatabaseRole(qualified);
        }
        final Database database = catalog.getDatabase(role.getDatabase());
        if (database != null) {
            for (final DatabaseRole holder : database.getDatabaseRoles()) {
                holder.revokeDatabaseRole(qualified);
            }
        }
    }

    /** Moves every grant of a renamed database role to its new name. */
    private void renameDatabaseRoleGrants(final String oldQualified, final String newQualified) {
        final List<Role> holders = new ArrayList<>(catalog.getAllRoles());
        final Database database = catalog.getDatabase(newQualified.substring(0, newQualified.lastIndexOf('.')));
        if (database != null) {
            holders.addAll(database.getDatabaseRoles());
        }
        for (final Role holder : holders) {
            final String grantor = holder.getDatabaseRoleGrants().get(oldQualified);
            if (grantor != null) {
                holder.revokeDatabaseRole(oldQualified);
                holder.grantDatabaseRole(newQualified, grantor);
            }
        }
        for (final User user : catalog.getAllUsers()) {
            final String grantor = user.getDatabaseRoleGrants().get(oldQualified);
            if (grantor != null) {
                user.revokeDatabaseRole(oldQualified);
                user.grantDatabaseRole(newQualified, grantor);
            }
        }
    }

    /**
     * GRANT DATABASE ROLE d.r TO USER u | ROLE r | DATABASE ROLE d.r2. The user or the role that is to hold it is
     * found before the database role (live-verified).
     */
    public Object grantDatabaseRole(final FrostlakeParser.GrantDatabaseRoleStatementContext ctx) {
        final String grantor = catalog.currentRoleForOwner();
        if (ctx.databaseRoleGrantee() != null) {
            final DatabaseRole holder = databaseRole(catalog,
                PrincipalNames.parts(ctx.databaseRoleGrantee().referencedName(), queryExecutor));
            final DatabaseRole role = grantedDatabaseRole(ctx.referencedName());
            if (holder == role) {
                throw new RuntimeException(SqlCompilationError.of("Cannot grant database role '"
                    + shown(role.getDatabase(), role.getName()) + "' to itself."));
            }
            if (holdsDatabaseRole(role, holder.getQualifiedName(), new HashSet<String>())) {
                // A grant that closes a loop is refused as an account role's is: the hierarchy stays one.
                throw new RuntimeException("Cannot grant database role: would create circular role hierarchy");
            }
            requireDatabaseRoleAuthority(role);
            holder.grantDatabaseRole(role.getQualifiedName(), grantor);
        } else if (ctx.USER() != null) {
            final User holder = userHolder(PrincipalNames.of(ctx.principalName(), queryExecutor));
            holder.grantDatabaseRole(authorizedDatabaseRole(ctx.referencedName()).getQualifiedName(), grantor);
        } else {
            final Role holder = catalog.getRoleExact(PrincipalNames.of(ctx.principalName(), queryExecutor));
            holder.grantDatabaseRole(authorizedDatabaseRole(ctx.referencedName()).getQualifiedName(), grantor);
        }
        return null;
    }

    /** Whether a database role holds another, by its qualified name, directly or through the roles it holds. */
    private boolean holdsDatabaseRole(final DatabaseRole from, final String target, final Set<String> seen) {
        for (final String held : from.getDatabaseRoleGrants().keySet()) {
            if (held.equals(target)) {
                return true;
            }
            if (!seen.add(held)) {
                continue;
            }
            final String[] parts = QualifiedName.parse(held).parts();
            final Database database = parts.length == 2 && catalog.hasDatabaseExact(parts[0])
                ? catalog.databaseExact(parts[0]) : null;
            final DatabaseRole next = database == null ? null : database.getDatabaseRole(parts[1]);
            if (next != null && holdsDatabaseRole(next, target, seen)) {
                return true;
            }
        }
        return false;
    }

    /** The user a database role is granted to or revoked from (see {@link UserLookup}). */
    private User userHolder(final String name) {
        return UserLookup.asking(catalog, queryExecutor.getSecurityManager(), name, Privilege.MANAGE_GRANTS);
    }

    /** REVOKE DATABASE ROLE d.r FROM USER u | ROLE r | DATABASE ROLE d.r2, its holder found first as a GRANT's is. */
    public Object revokeDatabaseRole(final FrostlakeParser.RevokeDatabaseRoleStatementContext ctx) {
        if (ctx.databaseRoleGrantee() != null) {
            final DatabaseRole holder = databaseRole(catalog,
                PrincipalNames.parts(ctx.databaseRoleGrantee().referencedName(), queryExecutor));
            holder.revokeDatabaseRole(authorizedDatabaseRole(ctx.referencedName()).getQualifiedName());
        } else if (ctx.USER() != null) {
            final User holder = userHolder(PrincipalNames.of(ctx.principalName(), queryExecutor));
            holder.revokeDatabaseRole(authorizedDatabaseRole(ctx.referencedName()).getQualifiedName());
        } else {
            final Role holder = catalog.getRoleExact(PrincipalNames.of(ctx.principalName(), queryExecutor));
            holder.revokeDatabaseRole(authorizedDatabaseRole(ctx.referencedName()).getQualifiedName());
        }
        return null;
    }

    /** {@link #grantedDatabaseRole}, once the session is found to decide its grants. */
    private DatabaseRole authorizedDatabaseRole(final FrostlakeParser.ReferencedNameContext name) {
        final DatabaseRole role = grantedDatabaseRole(name);
        requireDatabaseRoleAuthority(role);
        return role;
    }

    /**
     * GRANT and REVOKE DATABASE ROLE are decided by the database role's owner beside the roles that decide every
     * grant: the SYSTEM user's, an administrative role and a role holding MANAGE GRANTS.
     */
    private void requireDatabaseRoleAuthority(final DatabaseRole role) {
        if (queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().checkManageGrants("database role '" + role.getQualifiedName() + "'",
                "DATABASE ROLE", role.getQualifiedName(), role.getOwner());
        }
    }

    /**
     * The database role a GRANT or a REVOKE DATABASE ROLE names. An IDENTIFIER() reference spelling more than a
     * database and a role names nothing (live-verified).
     */
    private DatabaseRole grantedDatabaseRole(final FrostlakeParser.ReferencedNameContext name) {
        final String[] parts = PrincipalNames.parts(name, queryExecutor);
        if (parts.length > 2 && PrincipalNames.isReference(name)) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        return databaseRole(catalog, parts);
    }

    private void applyTagSet(final Taggable target, final FrostlakeParser.TagSetContext set) {
        for (final FrostlakeParser.TagAssignContext assign : set.tagAssign()) {
            final Tag tag = catalog.getTagExact(visitor.getText(assign.qualifiedName()));
            final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
            TagValues.requireAllowed(tag, value);
            TagAssignments.set(target, tag, value);
        }
    }

    private void applyTagUnset(final Taggable target, final FrostlakeParser.TagUnsetContext unset) {
        for (final FrostlakeParser.QualifiedNameContext name : unset.qualifiedName()) {
            TagAssignments.unset(target, catalog.getTagExact(visitor.getText(name)));
        }
    }

    // ---- grants listings -------------------------------------------------------------------------------

    private ResultSet grantsListing(final FrostlakeParser.AccessControlStatementContext ctx) {
        final AccessControlListings listings = new AccessControlListings(catalog);
        if (ctx.ON() != null && ctx.referencedName() == null) {
            return listings.grantsOnRole(catalog.getRoleExact(PrincipalNames.of(ctx.principalName(), queryExecutor)));
        }
        final DatabaseRole role = databaseRole(catalog, PrincipalNames.parts(ctx.referencedName(), queryExecutor));
        if (ctx.ON() != null) {
            return listings.grantsOnRole(role);
        }
        if (ctx.OF() != null) {
            return listings.grantsOfDatabaseRole(role);
        }
        // SHOW GRANTS TO DATABASE ROLE names the grantee BARE, where the same role is spelled with its
        // database in the object column of a grant ON it (live-verified).
        return listings.grantsTo(role, AccessControlListings.DATABASE_ROLE, role.getName());
    }

    private ResultSet futureGrants(final FrostlakeParser.AccessControlStatementContext ctx) {
        final AccessControlListings listings = new AccessControlListings(catalog);
        if (ctx.SCHEMA() != null) {
            return listings.futureGrants("SCHEMA",
                schemaScopeName(catalog, PrincipalNames.parts(ctx.referencedName(), queryExecutor)), null);
        }
        if (ctx.IN() != null) {
            return listings.futureGrants("DATABASE",
                catalog.databaseExact(PrincipalNames.of(ctx.principalName(), queryExecutor)).getName(), null);
        }
        if (ctx.referencedName() != null) {
            return listings.futureGrants(null, null,
                databaseRole(catalog, PrincipalNames.parts(ctx.referencedName(), queryExecutor)));
        }
        return listings.futureGrants(null, null,
            catalog.getRoleExact(PrincipalNames.of(ctx.principalName(), queryExecutor)));
    }

    /** The canonical name of the schema a scope names, {@code DB.SCHEMA}, which must exist. */
    static String schemaScopeName(final Catalog catalog, final FrostlakeParser.QualifiedNameContext name) {
        return schemaScopeName(catalog, ParseTreeText.qualifiedNameParts(name));
    }

    /** {@link #schemaScopeName(Catalog, FrostlakeParser.QualifiedNameContext)} for a name already resolved to its parts. */
    static String schemaScopeName(final Catalog catalog, final String[] parts) {
        final Schema schema = catalog.resolveSchema(QualifiedName.of(parts));
        final String database = parts.length >= 2 ? parts[parts.length - 2] : catalog.getCurrentDatabase();
        return QualifiedName.join(catalog.databaseExact(database).getName(), schema.getName());
    }

    /** The canonical name of a bulk grant's scope: {@code DB.SCHEMA} or {@code DB}, the only two it has. */
    static String scopeName(final Catalog catalog, final boolean schema, final String[] parts) {
        if (schema) {
            return schemaScopeName(catalog, parts);
        }
        return catalog.databaseExact(parts[0]).getName();
    }

    /**
     * The qualified names of the objects of one kind that exist in a bulk grant's scope — what GRANT … ON ALL
     * grants on, object by object.
     */
    static List<String> objectsInScope(final Catalog catalog, final String kind, final String scopeType,
                                       final String scopeName) {
        final List<String> names = new ArrayList<>();
        final List<Database> databases = new ArrayList<>();
        if ("DATABASE".equals(scopeType)) {
            databases.add(catalog.databaseExact(scopeName));
        } else {
            final Schema schema = catalog.resolveSchema(QualifiedName.parse(scopeName));
            addObjects(names, scopeName, schema, kind);
            return names;
        }
        for (final Database database : databases) {
            for (final Schema schema : database.getAllSchemas()) {
                if ("INFORMATION_SCHEMA".equals(schema.getName())) {
                    continue;
                }
                final String schemaName = QualifiedName.join(database.getName(), schema.getName());
                if ("SCHEMA".equals(kind)) {
                    names.add(schemaName);
                } else {
                    addObjects(names, schemaName, schema, kind);
                }
            }
        }
        return names;
    }

    private static void addObjects(final List<String> names, final String schemaName, final Schema schema,
                                   final String kind) {
        // A routine's privileges are each overload's own, so ALL FUNCTIONS and ALL PROCEDURES reach every
        // overload under its signature and count each one (live-verified).
        if ("FUNCTION".equals(kind)) {
            for (final Function function : schema.getFunctions()) {
                names.add(GrantNames.routine(schema, function.getName(), function.getParameters()));
            }
            return;
        }
        if ("PROCEDURE".equals(kind)) {
            for (final Procedure procedure : schema.getProcedures()) {
                names.add(GrantNames.routine(schema, procedure.getName(), procedure.getParameters()));
            }
            return;
        }
        for (final String object : objectNames(schema, kind)) {
            names.add(schemaName + "." + QualifiedName.join(object));
        }
    }

    /**
     * The names of the objects of one kind a schema holds, unqualified — what a bulk statement over
     * that kind reaches. A kind this engine does not hold in a schema answers nothing, which is what
     * leaves a bulk statement over it a no-op rather than an error. A temporary table or view is never
     * reached: it is the session's own, while a transient table is reached like any other.
     */
    static List<String> objectNames(final Schema schema, final String kind) {
        final List<String> objects = new ArrayList<>();
        switch (kind) {
            case "TABLE":
                // An event table is its own kind here: ALL TABLES leaves it out, ALL EVENT TABLES reaches it.
                for (final Table table : schema.getNonTemporaryTables()) {
                    if (!table.isEventTable()) {
                        objects.add(table.getName());
                    }
                }
                break;
            case "EVENT_TABLE":
                for (final Table table : schema.getNonTemporaryTables()) {
                    if (table.isEventTable()) {
                        objects.add(table.getName());
                    }
                }
                break;
            case "DYNAMIC_TABLE":
                for (final DynamicTable table : schema.getDynamicTables()) {
                    objects.add(table.getName());
                }
                break;
            case "MATERIALIZED_VIEW":
                for (final MaterializedView view : schema.getMaterializedViews()) {
                    objects.add(view.getName());
                }
                break;
            case "FILE_FORMAT":
                for (final FileFormat format : schema.getFileFormats()) {
                    objects.add(format.getName());
                }
                break;
            case "ALERT":
                for (final Alert alert : schema.getAlerts()) {
                    objects.add(alert.getName());
                }
                break;
            case "VIEW":
                for (final View view : schema.getViews()) {
                    if (!view.isTemporary()) {
                        objects.add(view.getName());
                    }
                }
                break;
            case "SEQUENCE":
                for (final Sequence sequence : schema.getSequences()) {
                    objects.add(sequence.getName());
                }
                break;
            case "STAGE":
                for (final Stage stage : schema.getStages()) {
                    objects.add(stage.getName());
                }
                break;
            case "STREAM":
                for (final Stream stream : schema.getStreams()) {
                    objects.add(stream.getName());
                }
                break;
            case "TASK":
                for (final Task task : schema.getTasks()) {
                    objects.add(task.getName());
                }
                break;
            case "PIPE":
                for (final Pipe pipe : schema.getPipes()) {
                    objects.add(pipe.getName());
                }
                break;
            case "FUNCTION":
                for (final Function function : schema.getFunctions()) {
                    objects.add(function.getName());
                }
                break;
            case "PROCEDURE":
                for (final Procedure procedure : schema.getProcedures()) {
                    objects.add(procedure.getName());
                }
                break;
            default:
                break;
        }
        return objects;
    }

    // ---- accounts --------------------------------------------------------------------------------------

    private Object account(final FrostlakeParser.AccessControlStatementContext ctx, final int first) {
        final AccountDirectory directory = catalog.getAccountDirectory();
        final String name = ctx.identifier() != null ? visitor.getText(ctx.identifier()) : undroppedAccount(ctx);
        final Account existing = directory.account(name);
        if (first == FrostlakeParser.CREATE) {
            if (existing != null || isCurrentAccount(name)) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
            }
            createAccount(ctx, directory, name);
        } else if (first == FrostlakeParser.DROP) {
            final int grace = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
            if (grace < MIN_GRACE_DAYS || grace > MAX_GRACE_DAYS) {
                throw new RuntimeException(SqlCompilationError.of("GRACE_PERIOD_IN_DAYS must be between "
                    + MIN_GRACE_DAYS + " and " + MAX_GRACE_DAYS + "."));
            }
            if (isCurrentAccount(name)) {
                throw new RuntimeException(SqlCompilationError.of("Cannot drop the current account."));
            }
            if (existing == null || existing.isDropped()) {
                if (ctx.if_exists() != null) {
                    ConditionalDdlOutcome.dropSkipped();
                    return null;
                }
                throw new RuntimeException(SqlCompilationError.doesNotExist("Account", name));
            }
            existing.drop(grace);
        } else {
            if (existing == null) {
                // An account that is not there is refused as one the role may not operate on, by the
                // organization privilege it would need on the current account (live-verified).
                final SecurityManager security = queryExecutor.getSecurityManager();
                throw new PrivilegeRefusal(SqlAccessControlError.privilegeRequired("ACCOUNT", name,
                    security == null ? null : security.getSessionContext().getCurrentRole(),
                    security != null && security.getSessionContext().isSecondaryRolesInUse(),
                    "MANAGE ORGANIZATION ACCOUNTS", catalog.getAccountLocator()));
            }
            if (!existing.isDropped()) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
            }
            existing.restore();
        }
        return null;
    }

    /** The account an UNDROP names: one part, a qualifier being refused in the account's words (live-verified). */
    private static String undroppedAccount(final FrostlakeParser.AccessControlStatementContext ctx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
        if (parts.length > 1) {
            throw new RuntimeException("Cannot specify qualifier for account.");
        }
        return parts[0];
    }

    private boolean isCurrentAccount(final String name) {
        final ResultSet own = queryExecutor.getShowExecutor().showAccounts();
        return !own.getRows().isEmpty() && name.equalsIgnoreCase(String.valueOf(own.getRows().get(0).getValue(1)));
    }

    private void createAccount(final FrostlakeParser.AccessControlStatementContext ctx,
                               final AccountDirectory directory, final String name) {
        String adminName = null;
        boolean credential = false;
        String email = null;
        String edition = null;
        String region = null;
        String regionGroup = null;
        String comment = null;
        for (final FrostlakeParser.AccountPropertyContext property : ctx.accountProperty()) {
            if (property.ADMIN_NAME() != null) {
                adminName = property.STRING_LITERAL() != null ? visitor.extractStringLiteral(property.STRING_LITERAL())
                    : visitor.getText(property.identifier());
            } else if (property.ADMIN_PASSWORD() != null || property.ADMIN_RSA_PUBLIC_KEY() != null) {
                credential = true;
            } else if (property.EMAIL() != null) {
                email = visitor.extractStringLiteral(property.STRING_LITERAL());
            } else if (property.EDITION() != null) {
                edition = visitor.getText(property.identifier());
                if (!EDITIONS.contains(edition)) {
                    throw new RuntimeException(SqlCompilationError.of("invalid value '" + edition
                        + "' for property 'EDITION'"));
                }
            } else if (property.REGION() != null) {
                region = visitor.getText(property.identifier());
            } else if (property.REGION_GROUP() != null) {
                regionGroup = visitor.getText(property.identifier());
            } else if (property.COMMENT() != null) {
                comment = visitor.extractStringLiteral(property.STRING_LITERAL());
            }
        }
        final List<String> missing = new ArrayList<>();
        if (adminName == null) {
            missing.add("ADMIN_NAME");
        }
        if (!credential) {
            missing.add("ADMIN_PASSWORD");
        }
        if (email == null) {
            missing.add("EMAIL");
        }
        if (edition == null) {
            missing.add("EDITION");
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + missing + "."));
        }
        directory.putAccount(new Account(name, edition, region, regionGroup, comment, adminName, email,
            directory.nextLocator()));
    }

    private Object managedAccount(final FrostlakeParser.AccessControlStatementContext ctx, final int first) {
        final AccountDirectory directory = catalog.getAccountDirectory();
        if (first == FrostlakeParser.SHOW) {
            final List<ManagedAccount> listed = new ArrayList<>();
            final String pattern = ctx.stringText().isEmpty() ? null
                : ParseTreeText.stringValue(ctx.stringText(0));
            for (final ManagedAccount account : directory.managedAccounts()) {
                if (pattern == null || likeMatches(account.getName(), pattern)) {
                    listed.add(account);
                }
            }
            return queryExecutor.getShowExecutor().showManagedAccounts(listed);
        }
        final String name = visitor.getText(ctx.identifier());
        if (first == FrostlakeParser.DROP) {
            if (!directory.removeManagedAccount(name)) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Managed account", name));
            }
            return null;
        }
        if (directory.managedAccount(name) != null) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        String adminName = null;
        boolean password = false;
        String type = null;
        String comment = null;
        for (final FrostlakeParser.ManagedAccountPropertyContext property : ctx.managedAccountProperty()) {
            if (property.ADMIN_NAME() != null) {
                adminName = property.STRING_LITERAL() != null ? visitor.extractStringLiteral(property.STRING_LITERAL())
                    : visitor.getText(property.identifier());
            } else if (property.ADMIN_PASSWORD() != null) {
                password = true;
            } else if (property.TYPE() != null) {
                type = visitor.getText(property.identifier());
            } else {
                comment = visitor.extractStringLiteral(property.STRING_LITERAL());
            }
        }
        final List<String> missing = new ArrayList<>();
        if (adminName == null) {
            missing.add("ADMIN_NAME");
        }
        if (!password) {
            missing.add("ADMIN_PASSWORD");
        }
        if (type == null) {
            missing.add("TYPE");
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + missing + "."));
        }
        if (!"READER".equals(type)) {
            throw new RuntimeException(SqlCompilationError.of("invalid value '" + type + "' for property 'TYPE'"));
        }
        final ManagedAccount account = new ManagedAccount(name, adminName, comment, directory.nextLocator());
        directory.putManagedAccount(account);
        final ResultSet own = queryExecutor.getShowExecutor().showAccounts();
        final String organization = own.getRows().isEmpty() ? ""
            : String.valueOf(own.getRows().get(0).getValue(0)).toLowerCase(Locale.ROOT);
        final String locator = account.getLocator().toLowerCase(Locale.ROOT);
        final String status = "{\"accountName\":\"" + name + "\",\"accountLocator\":\"" + account.getLocator()
            + "\",\"url\":\"https://" + organization + "-" + name.toLowerCase(Locale.ROOT)
            + ".snowflakecomputing.com\",\"accountLocatorUrl\":\"https://" + locator + ".snowflakecomputing.com\"}";
        return new ResultSet(Arrays.asList(new ResultSetColumn("status", StringType.VARCHAR)),
            new ArrayList<Row>(Arrays.asList(new Row(Arrays.<Object>asList(status)))));
    }

    /** SQL LIKE, case-insensitive, as the SHOW listings match their LIKE. */
    private static boolean likeMatches(final String value, final String pattern) {
        return ShowResultHelpers.matchesLike(value, pattern);
    }

    /** LIMIT n FROM 'name': at most n rows, from the first whose name sorts after the given one. */
    /** The first rows of a listing, in its own order. */
    private static ResultSet firstRows(final ResultSet listing, final int limit) {
        return limit >= listing.getRows().size() ? listing
            : new ResultSet(listing.getColumns(), new ArrayList<>(listing.getRows().subList(0, limit)));
    }

    private static ResultSet limitFrom(final ResultSet listing, final int limit, final String from) {
        final int nameIndex = listing.getColumnIndex("name");
        final List<Row> rows = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            if (rows.size() >= limit) {
                break;
            }
            // FROM names a cursor: the page starts after it, with the first name greater than the string.
            if (from == null || String.valueOf(row.getValue(nameIndex)).compareTo(from) > 0) {
                rows.add(row);
            }
        }
        return new ResultSet(listing.getColumns(), rows);
    }
}
