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
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code GRANT …} and {@code REVOKE …} statements, extracted verbatim from
 * {@link SQLCommandVisitor#visitGrantStatement} and {@link SQLCommandVisitor#visitRevokeStatement}.
 * The {@code visitSecurityStatement} router stays on the visitor and dispatches into
 * {@link #handleGrant} / {@link #handleRevoke}. Catalog mutations run against the injected
 * {@link Catalog}; grant-authority checks go through {@code queryExecutor.getSecurityManager()};
 * parse-text extraction ({@code getText}) is reached through the {@code visitor} back-reference so
 * its exact original behavior is preserved. The private {@link #privilegeName} helper moved along
 * with the two statement bodies (it was used only by them).
 */
public class GrantRevokeHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(GrantRevokeHandler.class);

    private static final String OWNERSHIP_TO_USER = "SQL execution error: Cannot grant OWNERSHIP to users.";

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;

    public GrantRevokeHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                              final SQLCommandVisitor visitor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handleGrant(final FrostlakeParser.GrantStatementContext ctx) {
        try {
            final DatabaseRole databaseGrantee = ctx.databaseRoleGrantee() == null ? null
                : AccessControlHandler.databaseRole(catalog, ctx.databaseRoleGrantee().qualifiedName());
            final boolean grantOption = ctx.withGrantOption() != null;
            if (ctx.bulkObjectType() != null) {
                // GRANT privs ON ALL/FUTURE <types> IN DATABASE/SCHEMA/ACCOUNT <name> TO USER/ROLE/DATABASE ROLE target
                final boolean isFuture = ctx.FUTURE() != null;
                final String objectType = bulkWords(ctx.bulkObjectType());
                final String objectKind = singularKind(objectType);
                final FrostlakeParser.BulkScopeContext scope = ctx.bulkScope();
                final String scopeType = scope.DATABASE() != null ? "DATABASE"
                    : "SCHEMA";
                // The scope must exist before anything is granted in it (live-verified).
                final String scopeName = AccessControlHandler.scopeName(catalog, scope);
                if (!isFuture && "PIPE".equals(objectKind)) {
                    throw new RuntimeException(SqlCompilationError.of("Bulk grant on objects of type PIPE to "
                        + bulkGranteeKind(ctx.USER() != null, ctx.databaseRoleGrantee() != null) + " is restricted."));
                }
                final List<String> privileges = bulkPrivileges(ctx.privilegeList(), objectKind);
                final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : ids != null && !ids.isEmpty() ? visitor.getText(ids.get(ids.size() - 1)) : "";
                final boolean isUser = ctx.USER() != null;
                if (databaseGrantee == null) {
                    requireGrantee(isUser, targetName);
                }
                final boolean ownership = rejectMisplacedOwnership(ctx.privilegeList(), ctx.currentGrants());
                if (ownership && isUser) {
                    throw new RuntimeException(OWNERSHIP_TO_USER);
                }
                if (ownership && !isFuture && databaseGrantee == null) {
                    // The objects are counted before they change hands, as the privileges of a bulk grant are.
                    final int inScope = AccessControlHandler.objectsInScope(catalog, objectKind, scopeType, scopeName)
                        .size();
                    if (new OwnershipTransfer(catalog, queryExecutor)
                            .transferAll(objectType, scope, targetName, ctx.currentGrants())) {
                        logger.trace("Transferred ownership of all {} in {} {} to {}", objectType, scopeType,
                            scopeName, targetName);
                        return objectsAffected(inScope);
                    }
                }
                if (isFuture) {
                    if (isUser) {
                        throw new RuntimeException(SqlCompilationError.of("Future grants to users are not supported."));
                    }
                    final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRole(targetName);
                    for (final String privilege : privileges) {
                        holder.addFutureGrant(objectKind, scopeType, scopeName, privilege, grantOption);
                    }
                } else {
                    final List<String> inScope = AccessControlHandler.objectsInScope(catalog, objectKind, scopeType,
                        scopeName);
                    for (final String objectName : inScope) {
                        for (final String privilege : privileges) {
                            grantTo(databaseGrantee, isUser, targetName, privilege, objectKind, objectName, null,
                                grantOption);
                        }
                    }
                    logger.trace("Granted {} on ALL {} in {} {} to {}", privileges, objectType, scopeType, scopeName,
                        targetName);
                    return objectsAffected(inScope.size());
                }
                logger.trace("Granted {} on {}{} in {} {} to {}", privileges,
                    isFuture ? "FUTURE " : "ALL ", objectType, scopeType, scopeName, targetName);

            } else if (ctx.privilegeList() == null && ctx.objectType() == null && ctx.globalPrivilegeList() == null
                    && databaseGrantee == null) {
                // GRANT ROLE role_name TO USER/ROLE target_name
                // identifiers: 0=role_name, 1=target_name
                final String roleName = visitor.getText(ctx.identifier(0));
                final String targetName2 = visitor.getText(ctx.identifier(1));

                if ("PUBLIC".equals(roleName)) {
                    // Every user and role holds PUBLIC already: the grant changes nothing, and says so once the
                    // grantee is found.
                    if (ctx.USER() != null) {
                        catalog.getUser(targetName2);
                    } else {
                        catalog.getRole(targetName2);
                    }
                    return StatusResults.of("Granting role PUBLIC has no effect.  Every user and role has role PUBLIC"
                        + " implicitly granted.");
                } else if (ctx.USER() != null) {
                    catalog.grantRoleToUser(roleName, targetName2);
                    logger.trace("Granted role {} to user {}", roleName, targetName2);
                } else {
                    // Target is ROLE
                    catalog.grantRoleToRole(roleName, targetName2);
                    logger.trace("Granted role {} to role {}", roleName, targetName2);
                }

            } else if (ctx.globalPrivilegeList() != null) {
                // GRANT global_privileges TO ROLE role_name
                // identifiers: 0=role_name
                final String roleName = visitor.getText(ctx.identifier(0));

                for (final FrostlakeParser.GlobalPrivilegeContext privCtx : ctx.globalPrivilegeList().globalPrivilege()) {
                    final String privilege = privilegeName(privCtx);
                    grantTo(null, false, roleName, privilege, "ACCOUNT", "ACCOUNT", null, grantOption);
                    logger.trace("Granted global privilege {} to role {}", privilege, roleName);
                }

            } else if (ctx.ACCOUNT() != null) {
                // GRANT privileges ON ACCOUNT TO USER/ROLE target_name
                // identifiers: 0=target_name
                final String targetName = visitor.getText(ctx.identifier(0));
                final boolean isUser = ctx.USER() != null;

                if (ctx.privilegeList().ALL() != null) {
                    grantTo(null, isUser, targetName, "ALL", "ACCOUNT", "ACCOUNT", null, grantOption);
                } else {
                    for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                        grantTo(null, isUser, targetName, privilegeName(privCtx), "ACCOUNT", "ACCOUNT", null,
                            grantOption);
                    }
                }
                logger.trace("Granted privileges on ACCOUNT to {}", targetName);

            } else if (ctx.privilegeList() != null || ctx.OWNERSHIP() != null) {
                // GRANT privileges/OWNERSHIP ON object TO USER/ROLE/DATABASE ROLE target_name
                // identifiers: 0=target_name
                final String writtenType = SecurableKinds.of(ctx.objectType());
                final String objectName = visitor.getText(ctx.qualifiedName());
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : visitor.getText(ctx.identifier(0));
                final boolean isUser = ctx.USER() != null;
                final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
                final Integer arity = ctx.LPAREN() == null ? null
                    : Integer.valueOf(ctx.identifierList() == null ? 0 : ctx.identifierList().identifier().size());
                // The object is found first, then the grantee, and only then is the grant itself judged
                // (live-verified).
                queryExecutor.requireSecurable(writtenType, parts, arity);
                final String objectType = grantedKind(writtenType, parts);
                if (databaseGrantee == null) {
                    requireGrantee(isUser, targetName);
                }
                final boolean ownership = ctx.OWNERSHIP() != null
                    || rejectMisplacedOwnership(ctx.privilegeList(), ctx.currentGrants());

                // Only an owner / administrative role may grant privileges on an object.
                if (queryExecutor.getSecurityManager() != null) {
                    queryExecutor.getSecurityManager().checkGrantAuthority(objectType, objectName);
                }

                if (ownership && databaseGrantee == null) {
                    // Ownership belongs to ROLES only. Live-verified on a real account:
                    // GRANT OWNERSHIP ON TABLE t TO USER u fails "SQL execution error: Cannot grant
                    // OWNERSHIP to users." while the same statement TO ROLE succeeds.
                    if (isUser) {
                        throw new RuntimeException(OWNERSHIP_TO_USER);
                    }
                    if (!new OwnershipTransfer(catalog, queryExecutor)
                            .transfer(objectType, parts, arity, objectName, targetName, ctx.currentGrants())) {
                        catalog.grantPrivilegeToRole("OWNERSHIP", objectType, objectName, targetName);
                    }
                    logger.trace("Granted OWNERSHIP on {} {} to role {}", objectType, objectName, targetName);
                } else {
                    // A grant on an object is recorded in its owner's name, whichever role runs it (live-verified).
                    final GrantedObject granted = queryExecutor.grantedObject(objectType, parts, arity);
                    final String grantor = granted != null && granted.getOwner() != null
                        && !granted.getOwner().isEmpty() ? granted.getOwner() : null;
                    if (ctx.privilegeList().ALL() != null) {
                        // GRANT ALL stores the EXPANSION, never an ALL marker — live-verified:
                        // SHOW GRANTS lists the individual privileges, revoking one leaves the
                        // rest, and OWNERSHIP is never part of the set. A type without a modeled
                        // vocabulary keeps the legacy ALL row.
                        for (final String privilege : allPrivilegesFor(objectType)) {
                            grantTo(databaseGrantee, isUser, targetName, privilege, objectType, objectName, grantor,
                                grantOption);
                        }
                    } else {
                        for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                            final String privilege = privilegeName(privCtx);
                            rejectInvalidPrivilegeForObjectType(privilege, objectType);
                            grantTo(databaseGrantee, isUser, targetName, privilege, objectType, objectName, grantor,
                                grantOption);
                        }
                    }
                    logger.trace("Granted privileges on {} {} to {}", objectType, objectName, targetName);
                }
            }

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    public Object handleRevoke(final FrostlakeParser.RevokeStatementContext ctx) {
        try {
            final DatabaseRole databaseGrantee = ctx.databaseRoleGrantee() == null ? null
                : AccessControlHandler.databaseRole(catalog, ctx.databaseRoleGrantee().qualifiedName());
            final boolean optionOnly = ctx.grantOptionFor() != null;
            if (ctx.bulkObjectType() != null) {
                // REVOKE [GRANT OPTION FOR] privs ON ALL/FUTURE <types> IN <scope> FROM ROLE/DATABASE ROLE target
                final boolean isFuture = ctx.FUTURE() != null;
                final String objectKind = singularKind(bulkWords(ctx.bulkObjectType()));
                final FrostlakeParser.BulkScopeContext scope = ctx.bulkScope();
                final String scopeType = scope.DATABASE() != null ? "DATABASE"
                    : "SCHEMA";
                final String scopeName = AccessControlHandler.scopeName(catalog, scope);
                if (!isFuture && "PIPE".equals(objectKind)) {
                    throw new RuntimeException(SqlCompilationError.of("Bulk revoke on objects of type PIPE from "
                        + bulkGranteeKind(ctx.USER() != null, ctx.databaseRoleGrantee() != null) + " is restricted."));
                }
                final List<String> privileges = bulkPrivileges(ctx.privilegeList(), objectKind);
                final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : ids != null && !ids.isEmpty() ? visitor.getText(ids.get(ids.size() - 1)) : "";
                final boolean isUser = ctx.USER() != null;
                if (databaseGrantee == null) {
                    requireGrantee(isUser, targetName);
                }
                if (isFuture) {
                    if (!isUser) {
                        final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRole(targetName);
                        for (final String privilege : privileges) {
                            holder.revokeFutureGrant(objectKind, scopeType, scopeName, privilege, optionOnly);
                        }
                    }
                } else {
                    int affected = 0;
                    for (final String objectName : AccessControlHandler.objectsInScope(catalog, objectKind, scopeType,
                            scopeName)) {
                        boolean revoked = false;
                        for (final String privilege : privileges) {
                            revoked |= revokeFrom(databaseGrantee, isUser, targetName, privilege, objectKind,
                                objectName, optionOnly);
                        }
                        affected += revoked ? 1 : 0;
                    }
                    return objectsAffected(affected);
                }

            } else if (ctx.privilegeList() == null && ctx.objectType() == null && ctx.globalPrivilegeList() == null
                    && databaseGrantee == null) {
                // REVOKE ROLE role_name FROM USER/ROLE target_name
                // identifiers: 0=role_name, 1=target_name
                final String roleName = visitor.getText(ctx.identifier(0));
                final String targetName = visitor.getText(ctx.identifier(1));

                if (ctx.USER() != null) {
                    catalog.revokeRoleFromUser(roleName, targetName);
                    logger.trace("Revoked role {} from user {}", roleName, targetName);
                } else {
                    // Target is ROLE
                    catalog.revokeRoleFromRole(roleName, targetName);
                    logger.trace("Revoked role {} from role {}", roleName, targetName);
                }

            } else if (ctx.globalPrivilegeList() != null) {
                // REVOKE global_privileges FROM ROLE role_name
                // identifiers: 0=role_name
                final String roleName = visitor.getText(ctx.identifier(0));

                boolean revoked = false;
                for (final FrostlakeParser.GlobalPrivilegeContext privCtx : ctx.globalPrivilegeList().globalPrivilege()) {
                    revoked |= revokeFrom(null, false, roleName, privilegeName(privCtx), "ACCOUNT", "ACCOUNT",
                        optionOnly);
                }
                return objectsAffected(revoked ? 1 : 0);

            } else if (ctx.ACCOUNT() != null) {
                // REVOKE privileges ON ACCOUNT FROM USER/ROLE target_name
                // identifiers: 0=target_name
                final String targetName = visitor.getText(ctx.identifier(0));
                final boolean isUser = ctx.USER() != null;

                boolean revoked = false;
                if (ctx.privilegeList().ALL() != null) {
                    revoked = revokeFrom(null, isUser, targetName, "ALL", "ACCOUNT", "ACCOUNT", optionOnly);
                } else {
                    for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                        revoked |= revokeFrom(null, isUser, targetName, privilegeName(privCtx), "ACCOUNT", "ACCOUNT",
                            optionOnly);
                    }
                }
                return objectsAffected(revoked ? 1 : 0);

            } else if (ctx.privilegeList() != null || ctx.OWNERSHIP() != null) {
                // REVOKE privileges/OWNERSHIP ON object FROM USER/ROLE/DATABASE ROLE target_name
                // identifiers: 0=target_name
                final String writtenType = SecurableKinds.of(ctx.objectType());
                final String objectName = visitor.getText(ctx.qualifiedName());
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : visitor.getText(ctx.identifier(0));
                final boolean isUser = ctx.USER() != null;
                // The object is found before the grantee or any authority is judged (live-verified).
                queryExecutor.requireSecurable(writtenType, ParseTreeText.qualifiedNameParts(ctx.qualifiedName()),
                    ctx.LPAREN() == null ? null
                        : Integer.valueOf(ctx.identifierList() == null ? 0 : ctx.identifierList().identifier().size()));
                final String objectType = grantedKind(writtenType, ParseTreeText.qualifiedNameParts(ctx.qualifiedName()));

                if (ctx.OWNERSHIP() != null || namesOwnership(ctx.privilegeList())) {
                    // An object always has an owner: live refuses to revoke it, once the grantee is found.
                    if (databaseGrantee == null) {
                        requireGrantee(isUser, targetName);
                    }
                    throw new RuntimeException("SQL execution error: OWNERSHIP can only be transferred.");
                }
                boolean revoked = false;
                if (ctx.privilegeList().ALL() != null) {
                    // REVOKE ALL removes the expanded set (and a legacy stored ALL marker),
                    // mirroring the grant-side expansion.
                    final List<String> allToRevoke = new ArrayList<>(allPrivilegesFor(objectType));
                    if (!allToRevoke.contains("ALL")) {
                        allToRevoke.add("ALL");
                    }
                    for (final String privilege : allToRevoke) {
                        revoked |= revokeFrom(databaseGrantee, isUser, targetName, privilege, objectType, objectName,
                            optionOnly);
                    }
                } else {
                    for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                        revoked |= revokeFrom(databaseGrantee, isUser, targetName, privilegeName(privCtx), objectType,
                            objectName, optionOnly);
                    }
                }
                logger.trace("Revoked privileges on {} {} from {}", objectType, objectName, targetName);
                return objectsAffected(revoked ? 1 : 0);
            }

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * Grants one privilege on one object to a user, an account role or a database role, recording the grant
     * option when the grant carries one (a user never holds one).
     *
     * @param databaseGrantee the database role granted to, or null for a user or an account role
     * @param grantor the role the grant is recorded in the name of, or null for the current role
     */
    private void grantTo(final DatabaseRole databaseGrantee, final boolean isUser, final String targetName,
                         final String privilege, final String objectType, final String objectName,
                         final String grantor, final boolean grantOption) {
        if (isUser) {
            catalog.grantPrivilegeToUser(privilege, objectType, objectName, targetName, grantor);
            return;
        }
        final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRole(targetName);
        final Privilege granted = Privilege.valueOf(privilege.toUpperCase());
        holder.grantPrivilege(objectType, objectName, granted, grantor != null ? grantor : catalog.currentRoleForOwner());
        if (grantOption) {
            holder.setGrantOption(objectType, objectName, granted, true);
        }
    }

    /**
     * Revokes one privilege on one object from a user, an account role or a database role — or, with
     * {@code optionOnly}, only the grant option it carries.
     */
    private boolean revokeFrom(final DatabaseRole databaseGrantee, final boolean isUser, final String targetName,
                               final String privilege, final String objectType, final String objectName,
                               final boolean optionOnly) {
        final Privilege revoked = Privilege.valueOf(privilege.toUpperCase());
        if (isUser) {
            if (optionOnly) {
                return false;
            }
            final boolean held = catalog.getUser(targetName).hasPrivilege(objectType, objectName, revoked);
            catalog.revokePrivilegeFromUser(privilege, objectType, objectName, targetName);
            return held;
        }
        final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRole(targetName);
        if (optionOnly) {
            final boolean had = holder.hasGrantOption(objectType, objectName, revoked);
            holder.setGrantOption(objectType, objectName, revoked, false);
            return had;
        }
        final boolean held = holder.hasPrivilege(objectType, objectName, revoked);
        holder.revokePrivilege(objectType, objectName, revoked);
        return held;
    }

    /**
     * The sentence of a GRANT or REVOKE that reaches objects by the count: {@code Statement executed successfully.
     * N objects affected.} A bulk GRANT counts the objects in its scope; a REVOKE counts the objects it took a
     * privilege, or a grant option, back from.
     */
    private static ResultSet objectsAffected(final int count) {
        return StatusResults.of(StatusResults.EXECUTED + " " + count + " objects affected.");
    }

    /** A bulk grant's plural kind as one word: {@code DYNAMIC TABLES} is {@code DYNAMIC_TABLES}. */
    private static String bulkWords(final FrostlakeParser.BulkObjectTypeContext kind) {
        final StringBuilder words = new StringBuilder();
        for (int i = 0; i < kind.getChildCount(); i++) {
            if (i > 0) {
                words.append('_');
            }
            words.append(kind.getChild(i).getText().toUpperCase(Locale.ROOT));
        }
        return words.toString();
    }

    /** How a bulk statement's refusal names the kind of its grantee, in words: {@code DATABASE ROLE}. */
    private static String bulkGranteeKind(final boolean user, final boolean databaseRole) {
        return user ? "USER" : databaseRole ? "DATABASE ROLE" : "ROLE";
    }

    /** The singular kind a bulk grant's plural names: TABLES is TABLE, SCHEMAS is SCHEMA. */
    private static String singularKind(final String plural) {
        return plural.endsWith("S") ? plural.substring(0, plural.length() - 1) : plural;
    }

    /** The privileges a bulk grant names, ALL expanded to the kind's own set, each in its enumeration spelling. */
    private List<String> bulkPrivileges(final FrostlakeParser.PrivilegeListContext list, final String objectKind) {
        final List<String> privileges = new ArrayList<>();
        if (list.ALL() != null) {
            privileges.addAll(allPrivilegesFor(objectKind));
            return privileges;
        }
        for (final FrostlakeParser.PrivilegeContext privilege : list.privilege()) {
            final String name = privilegeName(privilege);
            Privilege.valueOf(name);
            privileges.add(name);
        }
        return privileges;
    }

    /** A grantee that does not exist is refused by its kind and name before anything about the grant. */
    private void requireGrantee(final boolean isUser, final String name) {
        if (isUser) {
            catalog.getUser(name);
        } else {
            catalog.getRole(name);
        }
    }

    /** Whether a privilege list names OWNERSHIP. */
    private static boolean namesOwnership(final FrostlakeParser.PrivilegeListContext list) {
        if (list == null || list.ALL() != null) {
            return false;
        }
        for (final FrostlakeParser.PrivilegeContext privilege : list.privilege()) {
            if (privilege.privilegeLead().OWNERSHIP() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a grant transfers ownership, refusing the two misplacements live refuses: OWNERSHIP beside another
     * privilege, and a COPY or REVOKE CURRENT GRANTS tail on a grant of anything but OWNERSHIP, in the sentence live
     * uses for both tails (live-verified).
     *
     * @param list the privileges the grant names
     * @param tail its COPY or REVOKE CURRENT GRANTS tail, or null
     * @return true when the grant names OWNERSHIP alone
     */
    private static boolean rejectMisplacedOwnership(final FrostlakeParser.PrivilegeListContext list,
                                                    final FrostlakeParser.CurrentGrantsContext tail) {
        final boolean ownership = namesOwnership(list);
        if (ownership && list.privilege().size() > 1) {
            throw new RuntimeException("Cannot specify OWNERSHIP in privilege list.");
        }
        if (tail != null && !ownership) {
            throw new RuntimeException(SqlCompilationError.of(
                "Only OWNERSHIP grant command may specify REVOKE CURRENT GRANTS option."));
        }
        return ownership;
    }

    /**
     * The kind a grant is recorded under. A relation's grant belongs to the relation itself, whichever of
     * TABLE or VIEW the statement wrote: GRANT … ON VIEW t for a table t is the table's grant, listed by
     * SHOW GRANTS ON TABLE t and taken back by REVOKE … ON TABLE t, and a grant ON TABLE v for a view v
     * is the view's (live-verified). Every other kind is recorded as written.
     */
    private String grantedKind(final String writtenType, final String[] parts) {
        if (!"TABLE".equals(writtenType) && !"VIEW".equals(writtenType)) {
            return writtenType;
        }
        final GrantedObject granted = queryExecutor.grantedObject(writtenType, parts, null);
        return granted != null ? granted.getKind() : writtenType;
    }

    /**
     * Canonical enum name for a privilege parse node (a {@code privilege} or {@code globalPrivilege}
     * context). ANTLR's {@code getText()} concatenates child tokens with NO separator, so a
     * multi-word privilege like {@code CREATE VIEW} would collapse to "CREATEVIEW" and fail
     * {@code Privilege.valueOf(...)}; joining the child tokens with '_' yields the enum-matching
     * "CREATE_VIEW" (and "CREATE_MASKING_POLICY", "MONITOR_USAGE", …).
     */
    /**
     * Snowflake checks that a privilege is defined for the object type it is granted on. Live-verified
     * on a real account: {@code GRANT SELECT ON DATABASE d TO ROLE r} fails "Invalid object
     * type 'DATABASE' for privilege 'SELECT'", as do SELECT on SCHEMA and on WAREHOUSE, INSERT on
     * DATABASE and CREATE TABLE on DATABASE — while SELECT on TABLE / VIEW, USAGE on DATABASE / SCHEMA,
     * CREATE TABLE on SCHEMA, MODIFY on WAREHOUSE and GRANT ALL on DATABASE all succeed. Conversely
     * {@code GRANT USAGE ON TABLE t} fails "Invalid object type 'TABLE' for privilege 'USAGE'". A
     * database holds SCHEMAS, so CREATE SCHEMA ON DATABASE is legal while CREATE VIEW / CREATE TABLE ON
     * DATABASE are not. Only the pairs probed are enforced; anything else keeps passing.
     */
    private void rejectInvalidPrivilegeForObjectType(final String privilege, final String objectType) {
        final boolean rowPrivilege = "SELECT".equals(privilege) || "INSERT".equals(privilege)
            || "UPDATE".equals(privilege) || "DELETE".equals(privilege)
            || "TRUNCATE".equals(privilege) || "REFERENCES".equals(privilege);
        final boolean container = "DATABASE".equals(objectType) || "SCHEMA".equals(objectType)
            || "WAREHOUSE".equals(objectType);
        final boolean relation = "TABLE".equals(objectType) || "VIEW".equals(objectType);
        final boolean schemaLevelCreate = privilege.startsWith("CREATE_")
            && !"CREATE_SCHEMA".equals(privilege) && !"CREATE_DATABASE_ROLE".equals(privilege);
        if ((rowPrivilege && container)
                || ("USAGE".equals(privilege) && relation)
                || (schemaLevelCreate && "DATABASE".equals(objectType))
                || ("SELECT".equals(privilege) && "FILE_FORMAT".equals(objectType))) {
            // A relation is refused as a TABLE whichever keyword named it and whatever it is (live-verified).
            throw new RuntimeException("SQL compilation error:\nInvalid object type '" + (relation ? "TABLE" : objectType)
                + "' for privilege '" + privilege.replace('_', ' ') + "'.");
        }
        // DROP is no privilege of any object — dropping one takes its OWNERSHIP — so granting it is refused for
        // whatever it names, a table, a stream or a schema alike (live-verified).
        if ("DROP".equals(privilege)) {
            throw new RuntimeException("SQL compilation error:\nInvalid object type '" + (relation ? "TABLE" : objectType)
                + "' for privilege 'DROP'.");
        }
        // Live names a masking policy by the last word of its kind here.
        if ("SELECT".equals(privilege) && "MASKING_POLICY".equals(objectType)) {
            throw new RuntimeException("SQL compilation error:\nInvalid object type 'POLICY' for privilege 'SELECT'.");
        }
        // A network policy, a network rule, a password policy and a secret each take a closed set, and name the
        // kind in the refusal as their grants record it — a password policy, like a masking policy, as POLICY.
        final List<String> securityObjectPrivileges = securityObjectPrivileges(objectType);
        if (securityObjectPrivileges != null && !securityObjectPrivileges.contains(privilege)) {
            throw new RuntimeException("SQL compilation error:\nInvalid object type '"
                + ("PASSWORD_POLICY".equals(objectType) ? "POLICY" : objectType) + "' for privilege '"
                + privilege.replace('_', ' ') + "'.");
        }
    }

    /** The privileges a network policy, a network rule, a password policy or a secret takes; null for another kind. */
    private static List<String> securityObjectPrivileges(final String objectType) {
        switch (objectType) {
            case "NETWORK_POLICY":
            case "NETWORK_RULE":
                return List.of("USAGE");
            case "PASSWORD_POLICY":
                return List.of("APPLY");
            case "SECRET":
                return List.of("READ", "USAGE");
            default:
                return null;
        }
    }

    /**
     * The privileges {@code GRANT ALL} expands to for one securable type, as the account lists them after the grant:
     * SHOW GRANTS names each one, revoking one leaves the rest, REVOKE ALL clears them, and OWNERSHIP is never among
     * them. The account adds privileges over time, so each list is its measured state (live-verified); a type not
     * measured keeps its documented set, and an unmapped one the legacy single ALL row.
     */
    private List<String> allPrivilegesFor(final String objectType) {
        switch (objectType) {
            case "TABLE":
                return List.of("APPLYBUDGET", "DELETE", "DELETE_ERROR_TABLE", "EVOLVE_SCHEMA", "INSERT", "REBUILD",
                    "REFERENCES", "SELECT", "SELECT_ERROR_TABLE", "TRUNCATE", "UPDATE");
            case "VIEW":
                return List.of("APPLYBUDGET", "DELETE", "DELETE_ERROR_TABLE", "EVOLVE_SCHEMA", "INSERT", "REBUILD",
                    "REFERENCES", "SELECT", "SELECT_ERROR_TABLE", "TRUNCATE", "UPDATE",
                    "VIEW_EXPANDED_QUERY_PROFILE");
            case "SCHEMA":
                return List.of("ADD_SEARCH_OPTIMIZATION", "ADD_SEMANTIC_VIEW_MATERIALIZATION", "APPLYBUDGET",
                    "CREATE_AGENT", "CREATE_AGENT_TASK", "CREATE_AGGREGATION_POLICY", "CREATE_ALERT",
                    "CREATE_APPLICATION_SERVICE", "CREATE_ARTIFACT_REPOSITORY", "CREATE_AUTHENTICATION_POLICY",
                    "CREATE_BACKUP_POLICY", "CREATE_BACKUP_SET", "CREATE_CONTACT", "CREATE_CORTEX_EXTENSION",
                    "CREATE_CORTEX_SEARCH_SERVICE", "CREATE_DATA_METRIC_FUNCTION", "CREATE_DATA_MOVEMENT_POLICY",
                    "CREATE_DATA_MOVEMENT_RULE", "CREATE_DATASET", "CREATE_DBT_PROJECT", "CREATE_DCM_PROJECT",
                    "CREATE_DYNAMIC_TABLE", "CREATE_EVENT_TABLE", "CREATE_EXPERIMENT", "CREATE_EXTERNAL_AGENT",
                    "CREATE_EXTERNAL_MCP_SERVER", "CREATE_EXTERNAL_TABLE", "CREATE_FEATURE_POLICY",
                    "CREATE_FILE_FORMAT", "CREATE_FUNCTION", "CREATE_GATEWAY", "CREATE_GIT_REPOSITORY",
                    "CREATE_HYBRID_TABLE", "CREATE_ICEBERG_TABLE", "CREATE_IMAGE_REPOSITORY",
                    "CREATE_INTERACTIVE_TABLE", "CREATE_JOIN_POLICY", "CREATE_MAINTENANCE_POLICY",
                    "CREATE_MANAGED_MCP_SERVER", "CREATE_MASKING_POLICY", "CREATE_MATERIALIZED_VIEW",
                    "CREATE_MCP_SERVER", "CREATE_MODEL", "CREATE_MODEL_MONITOR",
                    "CREATE_MULTI_PARTY_APPROVAL_POLICY", "CREATE_NETWORK_RULE", "CREATE_NOTEBOOK",
                    "CREATE_NOTEBOOK_PROJECT", "CREATE_ONLINE_FEATURE_TABLE", "CREATE_OPENFLOW_CONNECTOR",
                    "CREATE_OPENFLOW_RUNTIME", "CREATE_PACKAGES_POLICY", "CREATE_PASSWORD_POLICY", "CREATE_PIPE",
                    "CREATE_PRIVACY_POLICY", "CREATE_PROCEDURE", "CREATE_PROJECTION_POLICY", "CREATE_RESOURCE_GROUP",
                    "CREATE_RESTRICTED_SESSION_SCOPE", "CREATE_ROW_ACCESS_POLICY", "CREATE_SECRET",
                    "CREATE_SEMANTIC_VIEW", "CREATE_SEQUENCE", "CREATE_SERVICE", "CREATE_SERVICE_CLASS",
                    "CREATE_SESSION_POLICY", "CREATE_SNAPSHOT", "CREATE_STAGE", "CREATE_STORAGE_LIFECYCLE_POLICY",
                    "CREATE_STREAM", "CREATE_STREAMLIT", "CREATE_TABLE", "CREATE_TAG", "CREATE_TASK",
                    "CREATE_TEMPORARY_TABLE", "CREATE_TYPE", "CREATE_VARIABLE", "CREATE_VIEW", "CREATE_WORKSPACE",
                    "CREATE_ZEROCOPY_CONNECTOR", "EXECUTE_AUTO_CLASSIFICATION", "MODIFY", "MONITOR", "USAGE");
            case "DATABASE":
                return List.of("APPLYBUDGET", "CREATE_DATABASE_ROLE", "CREATE_SCHEMA", "EXECUTE_AUTO_CLASSIFICATION",
                    "MODIFY", "MONITOR", "USAGE");
            case "WAREHOUSE":
                return List.of("MODIFY", "MONITOR", "OPERATE", "USAGE");
            case "STAGE":
                return List.of("READ", "WRITE");
            case "FUNCTION":
            case "PROCEDURE":
                return List.of("MONITOR", "USAGE");
            case "SEQUENCE":
                return List.of("USAGE");
            case "STREAM":
                return List.of("SELECT");
            case "TASK":
                return List.of("APPLYBUDGET", "MONITOR", "OPERATE");
            case "FILE_FORMAT":
                return List.of("USAGE");
            case "MASKING_POLICY":
            case "ROW_ACCESS_POLICY":
                return List.of("APPLY");
            case "TAG":
                return List.of("APPLY", "APPLYBUDGET", "MODIFY", "MONITOR", "READ");
            case "NETWORK_POLICY":
            case "NETWORK_RULE":
            case "PASSWORD_POLICY":
            case "SECRET":
                return securityObjectPrivileges(objectType);
            default:
                return List.of("ALL");
        }
    }

    private String privilegeName(final ParserRuleContext privCtx) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < privCtx.getChildCount(); i++) {
            if (i > 0) {
                sb.append('_');
            }
            sb.append(privCtx.getChild(i).getText());
        }
        return sb.toString().toUpperCase();
    }
}
