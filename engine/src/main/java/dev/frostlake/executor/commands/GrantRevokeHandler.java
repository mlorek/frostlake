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
import dev.frostlake.executor.InformationSchemaGuard;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private String principal(final FrostlakeParser.PrincipalNameContext name) {
        return PrincipalNames.of(name, queryExecutor);
    }

    /** The canonical parts of the object a statement names, an IDENTIFIER() reference resolved. */
    private String[] objectParts(final FrostlakeParser.GrantedObjectNameContext name) {
        if (name.wholeObjectName() == null) {
            // The word IDENTIFIER standing as the object's own name.
            return new String[] {name.getText().toUpperCase(Locale.ROOT)};
        }
        return queryExecutor.resolveWholeObjectNameParts(name.wholeObjectName());
    }

    /**
     * A database and a warehouse are named by one part: an IDENTIFIER() reference spelling a path names neither,
     * and is answered by the sentence that names nothing (live-verified).
     */
    private static void requireReferencedPath(final String objectType,
                                              final FrostlakeParser.GrantedObjectNameContext name,
                                              final String[] parts) {
        final boolean reference = name.wholeObjectName() != null
            && name.wholeObjectName().identifierArgument() != null;
        if (reference && parts.length > 1 && ("DATABASE".equals(objectType) || "WAREHOUSE".equals(objectType))) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
    }

    /** {@link #objectParts}, as one name. */
    private String objectText(final FrostlakeParser.GrantedObjectNameContext name) {
        if (name.wholeObjectName() == null) {
            return name.getText().toUpperCase(Locale.ROOT);
        }
        return queryExecutor.resolveWholeObjectName(name.wholeObjectName());
    }

    /** No privilege is granted on, or revoked from, INFORMATION_SCHEMA or one of its views (live-verified). */
    private void refuseSystemObject(final String writtenType, final String[] parts) {
        if ("TABLE".equals(writtenType) || "VIEW".equals(writtenType)) {
            InformationSchemaGuard.refuseWrite(catalog, parts);
        } else if ("SCHEMA".equals(writtenType)) {
            InformationSchemaGuard.refuseSchemaGrant(parts);
        }
    }

    public Object handleGrant(final FrostlakeParser.GrantStatementContext ctx) {
        try {
            final DatabaseRole databaseGrantee = ctx.databaseRoleGrantee() == null ? null
                : AccessControlHandler.databaseRole(catalog,
                    PrincipalNames.parts(ctx.databaseRoleGrantee().referencedName(), queryExecutor));
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
                final String[] scopeParts = PrincipalNames.parts(scope.referencedName(), queryExecutor);
                final String scopeName = AccessControlHandler.scopeName(catalog, scope.SCHEMA() != null, scopeParts);
                if (scope.SCHEMA() != null) {
                    InformationSchemaGuard.refuseSchemaGrant(scopeParts);
                }
                if (!isFuture && "PIPE".equals(objectKind)) {
                    throw new RuntimeException(SqlCompilationError.of("Bulk grant on objects of type PIPE to "
                        + bulkGranteeKind(ctx.USER() != null, ctx.databaseRoleGrantee() != null) + " is restricted."));
                }
                final List<String> privileges = bulkPrivileges(ctx.privilegeList(), objectKind);
                final List<FrostlakeParser.PrincipalNameContext> ids = ctx.principalName();
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : ids != null && !ids.isEmpty() ? principal(ids.get(ids.size() - 1)) : "";
                final boolean isUser = ctx.USER() != null;
                if (databaseGrantee == null) {
                    requireGrantee(isUser, targetName);
                }
                final boolean ownership = rejectMisplacedOwnership(ctx.privilegeList(), ctx.currentGrants());
                if (ownership && isUser) {
                    throw new RuntimeException(OWNERSHIP_TO_USER);
                }
                if (isFuture && isUser) {
                    throw new RuntimeException(SqlCompilationError.of("Future grants to users are not supported."));
                }
                requireBulkAuthority(isFuture, objectKind, scopeType, scopeName,
                    ownership ? Collections.<String>emptyList() : privileges);
                if (ownership && !isFuture && databaseGrantee == null) {
                    // The objects are counted before they change hands, as the privileges of a bulk grant are.
                    final int inScope = AccessControlHandler.objectsInScope(catalog, objectKind, scopeType, scopeName)
                        .size();
                    if (new OwnershipTransfer(catalog, queryExecutor)
                            .transferAll(objectType, scope.SCHEMA() != null, scopeParts, targetName,
                                ctx.currentGrants())) {
                        logger.trace("Transferred ownership of all {} in {} {} to {}", objectType, scopeType,
                            scopeName, targetName);
                        return objectsAffected(inScope);
                    }
                }
                if (isFuture) {
                    final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRoleExact(targetName);
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
                final String roleName = principal(ctx.principalName(0));
                final String targetName2 = principal(ctx.principalName(1));

                if ("PUBLIC".equals(roleName)) {
                    // Every user and role holds PUBLIC already: the grant changes nothing, and says so once the
                    // grantee is found.
                    if (ctx.USER() != null) {
                        catalog.getUser(targetName2);
                    } else {
                        catalog.getRoleExact(targetName2);
                    }
                    return StatusResults.of("Granting role PUBLIC has no effect.  Every user and role has role PUBLIC"
                        + " implicitly granted.");
                }
                requireRoleAuthority(ctx.USER() != null, roleName, targetName2);
                if (ctx.USER() != null) {
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
                final String roleName = principal(ctx.principalName(0));
                requireAccountAuthority();

                for (final FrostlakeParser.GlobalPrivilegeContext privCtx : ctx.globalPrivilegeList().globalPrivilege()) {
                    final String privilege = privilegeName(privCtx);
                    grantTo(null, false, roleName, privilege, "ACCOUNT", "ACCOUNT", null, grantOption);
                    logger.trace("Granted global privilege {} to role {}", privilege, roleName);
                }

            } else if (ctx.ACCOUNT() != null) {
                // GRANT privileges ON ACCOUNT TO USER/ROLE target_name
                // identifiers: 0=target_name
                final String targetName = principal(ctx.principalName(0));
                final boolean isUser = ctx.USER() != null;
                requireAccountAuthority();

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
                // An object named without its kind is a table or a view (live-verified).
                final String writtenType = ctx.objectType() == null ? "TABLE" : SecurableKinds.of(ctx.objectType());
                // A table or a view the current schema does not hold is looked for along the session's
                // SEARCH_PATH, and the grant is recorded on the one found there (live-verified).
                final boolean relation = "TABLE".equals(writtenType) || "VIEW".equals(writtenType);
                // A grantee written as a reference is read before the object's own reference (live-verified).
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : granteeName(ctx);
                final String writtenName = objectText(ctx.grantedObjectName());
                final String objectName = relation ? queryExecutor.searchPathQualified(writtenName) : writtenName;
                final boolean isUser = ctx.USER() != null;
                final String[] parts = objectName.equals(writtenName)
                    ? objectParts(ctx.grantedObjectName()) : QualifiedName.parse(objectName).parts();
                requireReferencedPath(writtenType, ctx.grantedObjectName(), parts);
                final Integer arity = ctx.LPAREN() == null ? null
                    : Integer.valueOf(ctx.identifierList() == null ? 0 : ctx.identifierList().identifier().size());
                requireArgumentTypes(writtenType, parts, ctx.LPAREN() != null);
                requireTypeWords(writtenType, ctx.identifierList());
                // The object is found first, then the grantee, and only then is the grant itself judged
                // (live-verified).
                queryExecutor.requireSecurable(writtenType, parts, arity);
                refuseSystemObject(writtenType, parts);
                final String objectType = grantedKind(writtenType, parts);
                final List<String> writtenTypes = writtenArgumentTypes(ctx.LPAREN() != null, ctx.identifierList());
                // A routine's privileges are each overload's own, under its signature (GrantNames.routine).
                final List<String> recordedNames = isRoutine(objectType)
                    ? queryExecutor.routineGrantNames(objectType, parts, writtenTypes)
                    : Collections.singletonList(recordedName(objectType, parts, arity, objectName));
                if (databaseGrantee == null) {
                    refuseUnmodelledGrantee(ctx);
                    requireGrantee(isUser, targetName);
                }
                final boolean ownership = ctx.OWNERSHIP() != null
                    || rejectMisplacedOwnership(ctx.privilegeList(), ctx.currentGrants());

                // Only an owner / administrative role may grant privileges on an object.
                if (queryExecutor.getSecurityManager() != null) {
                    final String owner = ownerOf(objectType, parts, arity, writtenTypes, objectName);
                    final List<String> passedOn = ownership ? Collections.<String>emptyList()
                        : grantedPrivileges(ctx.privilegeList(), objectType);
                    for (final String recorded : recordedNames) {
                        queryExecutor.getSecurityManager().checkGrantAuthority(objectType,
                            isRoutine(objectType) ? recorded : objectName, owner, passedOn);
                    }
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
                        for (final String recorded : recordedNames) {
                            catalog.grantPrivilegeToRole("OWNERSHIP", objectType,
                                GrantNames.of(catalog, objectType, recorded), targetName);
                        }
                    }
                    logger.trace("Granted OWNERSHIP on {} {} to role {}", objectType, objectName, targetName);
                } else if (ownership && new OwnershipTransfer(catalog, queryExecutor)
                        .transfer(objectType, parts, arity, objectName, databaseGrantee, ctx.currentGrants())) {
                    // A database role owns what it is granted the ownership of, as an account role does.
                    logger.trace("Granted OWNERSHIP on {} {} to database role {}", objectType, objectName, targetName);
                } else {
                    // A grant on an object is recorded in its owner's name, whichever role runs it (live-verified).
                    final GrantedObject granted = queryExecutor.grantedObject(objectType, parts, arity, writtenTypes);
                    final String grantor = granted != null && granted.getOwner() != null
                        && !granted.getOwner().isEmpty() ? granted.getOwner() : null;
                    for (final String recorded : recordedNames) {
                        if (ctx.privilegeList().ALL() != null) {
                            // GRANT ALL stores the EXPANSION, never an ALL marker — live-verified:
                            // SHOW GRANTS lists the individual privileges, revoking one leaves the
                            // rest, and OWNERSHIP is never part of the set. A type without a modeled
                            // vocabulary keeps the legacy ALL row.
                            for (final String privilege : allPrivilegesFor(objectType)) {
                                grantTo(databaseGrantee, isUser, targetName, privilege, objectType, recorded, grantor,
                                    grantOption);
                            }
                        } else {
                            for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                                final String privilege = privilegeName(privCtx);
                                rejectInvalidPrivilegeForObjectType(privilege, objectType);
                                grantTo(databaseGrantee, isUser, targetName, privilege, objectType, recorded, grantor,
                                    grantOption);
                            }
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
                : AccessControlHandler.databaseRole(catalog,
                    PrincipalNames.parts(ctx.databaseRoleGrantee().referencedName(), queryExecutor));
            final boolean optionOnly = ctx.grantOptionFor() != null;
            if (ctx.bulkObjectType() != null) {
                // REVOKE [GRANT OPTION FOR] privs ON ALL/FUTURE <types> IN <scope> FROM ROLE/DATABASE ROLE target
                final boolean isFuture = ctx.FUTURE() != null;
                final String objectKind = singularKind(bulkWords(ctx.bulkObjectType()));
                final FrostlakeParser.BulkScopeContext scope = ctx.bulkScope();
                final String scopeType = scope.DATABASE() != null ? "DATABASE"
                    : "SCHEMA";
                final String scopeName = AccessControlHandler.scopeName(catalog, scope.SCHEMA() != null,
                    PrincipalNames.parts(scope.referencedName(), queryExecutor));
                if (!isFuture && "PIPE".equals(objectKind)) {
                    throw new RuntimeException(SqlCompilationError.of("Bulk revoke on objects of type PIPE from "
                        + bulkGranteeKind(ctx.USER() != null, ctx.databaseRoleGrantee() != null) + " is restricted."));
                }
                final List<String> privileges = bulkPrivileges(ctx.privilegeList(), objectKind);
                final List<FrostlakeParser.PrincipalNameContext> ids = ctx.principalName();
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : ids != null && !ids.isEmpty() ? principal(ids.get(ids.size() - 1)) : "";
                final boolean isUser = ctx.USER() != null;
                if (databaseGrantee == null) {
                    requireGrantee(isUser, targetName);
                }
                if (isFuture) {
                    requireBulkAuthority(true, objectKind, scopeType, scopeName, Collections.<String>emptyList());
                    if (!isUser) {
                        final Role holder = databaseGrantee != null ? databaseGrantee
                            : catalog.getRoleExact(targetName);
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
                final String roleName = principal(ctx.principalName(0));
                final String targetName = principal(ctx.principalName(1));
                requireRoleAuthority(ctx.USER() != null, roleName, targetName);

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
                final String roleName = principal(ctx.principalName(0));
                requireAccountAuthority();

                boolean revoked = false;
                for (final FrostlakeParser.GlobalPrivilegeContext privCtx : ctx.globalPrivilegeList().globalPrivilege()) {
                    revoked |= revokeFrom(null, false, roleName, privilegeName(privCtx), "ACCOUNT", "ACCOUNT",
                        optionOnly);
                }
                return objectsAffected(revoked ? 1 : 0);

            } else if (ctx.ACCOUNT() != null) {
                // REVOKE privileges ON ACCOUNT FROM USER/ROLE target_name
                // identifiers: 0=target_name
                final String targetName = principal(ctx.principalName(0));
                final boolean isUser = ctx.USER() != null;
                requireAccountAuthority();

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
                // An object named without its kind is a table or a view (live-verified).
                final String writtenType = ctx.objectType() == null ? "TABLE" : SecurableKinds.of(ctx.objectType());
                final String targetName = databaseGrantee != null ? databaseGrantee.getQualifiedName()
                    : principal(ctx.principalName(0));
                final String objectName = objectText(ctx.grantedObjectName());
                final boolean isUser = ctx.USER() != null;
                final String[] parts = objectParts(ctx.grantedObjectName());
                requireReferencedPath(writtenType, ctx.grantedObjectName(), parts);
                final Integer arity = ctx.LPAREN() == null ? null
                    : Integer.valueOf(ctx.identifierList() == null ? 0 : ctx.identifierList().identifier().size());
                requireArgumentTypes(writtenType, parts, ctx.LPAREN() != null);
                requireTypeWords(writtenType, ctx.identifierList());
                // The object is found before the grantee or any authority is judged (live-verified).
                queryExecutor.requireSecurable(writtenType, parts, arity);
                refuseSystemObject(writtenType, parts);
                final String objectType = grantedKind(writtenType, parts);
                // A routine's privileges are each overload's own, under its signature (GrantNames.routine).
                final List<String> recordedNames = isRoutine(objectType)
                    ? queryExecutor.routineGrantNames(objectType, parts,
                        writtenArgumentTypes(ctx.LPAREN() != null, ctx.identifierList()))
                    : Collections.singletonList(recordedName(objectType, parts, arity, objectName));

                if (ctx.OWNERSHIP() != null || namesOwnership(ctx.privilegeList())) {
                    // An object always has an owner: live refuses to revoke it, once the grantee is found.
                    if (databaseGrantee == null) {
                        requireGrantee(isUser, targetName);
                    }
                    throw new RuntimeException("SQL execution error: OWNERSHIP can only be transferred.");
                }
                // A REVOKE naming its object counts every privilege it took back: two for SELECT, INSERT, and
                // one for each privilege ALL stands for that the grantee held (live-verified).
                int revoked = 0;
                for (final String recorded : recordedNames) {
                    if (ctx.privilegeList().ALL() != null) {
                        // REVOKE ALL removes the expanded set (and a legacy stored ALL marker),
                        // mirroring the grant-side expansion.
                        final List<String> allToRevoke = new ArrayList<>(allPrivilegesFor(objectType));
                        if (!allToRevoke.contains("ALL")) {
                            allToRevoke.add("ALL");
                        }
                        for (final String privilege : allToRevoke) {
                            if (revokeFrom(databaseGrantee, isUser, targetName, privilege, objectType, recorded,
                                    optionOnly)) {
                                revoked++;
                            }
                        }
                    } else {
                        for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                            if (revokeFrom(databaseGrantee, isUser, targetName, privilegeName(privCtx),
                                    objectType, recorded, optionOnly)) {
                                revoked++;
                            }
                        }
                    }
                }
                logger.trace("Revoked privileges on {} {} from {}", objectType, objectName, targetName);
                return objectsAffected(revoked);
            }

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * A routine is named with its argument types wherever a privilege on it is granted, revoked or listed:
     * routines overload, and the account refuses a bare name whether or not it has overloads, a procedure's in
     * the same words as a function's (live-verified).
     */
    static void requireArgumentTypes(final String objectType, final String[] parts, final boolean listWritten) {
        if (!listWritten && isRoutine(objectType)) {
            throw new RuntimeException("Argument types of function '" + parts[parts.length - 1]
                + "' must be specified.");
        }
    }

    /**
     * A routine's argument list names types. A plain word there is no type, and is refused as one before the
     * routine is looked for, the first such word in the order written: {@code GRANT USAGE ON FUNCTION f1(x)} is
     * {@code Unsupported data type 'X'.} (live-verified).
     */
    private static void requireTypeWords(final String objectType, final FrostlakeParser.IdentifierListContext types) {
        if (types == null || !isRoutine(objectType)) {
            return;
        }
        for (final FrostlakeParser.IdentifierContext type : types.identifier()) {
            final int token = type.getStart().getType();
            if (token == FrostlakeLexer.IDENTIFIER || token == FrostlakeLexer.QUOTED_IDENTIFIER) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Unsupported data type '" + SqlIdentifiers.canonical(type) + "'."));
            }
        }
    }

    /**
     * The name a privilege on an object that is no routine is recorded under: the full name of the object the
     * statement's name reaches, as the catalog holds it, so that every spelling of one object keeps one set of
     * privileges. A kind with no object to look up keeps the name as written.
     */
    private String recordedName(final String objectType, final String[] parts, final Integer arity,
                                final String written) {
        try {
            final GrantedObject granted = queryExecutor.grantedObject(objectType, parts, arity, null);
            if (granted != null && granted.getName() != null) {
                return granted.getName();
            }
        } catch (final RuntimeException unresolved) {
            // The statement reports what it cannot find itself; the name stays as written until it does.
        }
        return written;
    }

    /** Whether privileges on this kind are an overload's own, recorded under its signature. */
    private static boolean isRoutine(final String objectType) {
        return "FUNCTION".equals(objectType) || "PROCEDURE".equals(objectType);
    }

    /**
     * The argument types a statement writes after a routine's name, as written, or null when it writes no argument
     * list at all.
     */
    private List<String> writtenArgumentTypes(final boolean listWritten,
                                              final FrostlakeParser.IdentifierListContext types) {
        if (!listWritten) {
            return null;
        }
        final List<String> written = new ArrayList<>();
        if (types != null) {
            for (final FrostlakeParser.IdentifierContext type : types.identifier()) {
                written.add(visitor.getText(type));
            }
        }
        return written;
    }

    /**
     * Grants one privilege on one object to a user, an account role or a database role, recording the grant
     * option when the grant carries one (a user never holds one).
     *
     * @param databaseGrantee the database role granted to, or null for a user or an account role
     * @param grantor the role the grant is recorded in the name of, or null for the current role
     */
    private void grantTo(final DatabaseRole databaseGrantee, final boolean isUser, final String targetName,
                         final String privilege, final String objectType, final String written,
                         final String grantor, final boolean grantOption) {
        // Recorded under the object's resolved full name, never the spelling (GrantNames).
        final String objectName = GrantNames.of(catalog, objectType, written);
        if (isUser) {
            catalog.grantPrivilegeToUser(privilege, objectType, objectName, targetName, grantor);
            // A user keeps the grant option as a role does (live-verified).
            if (grantOption) {
                catalog.getUser(targetName).setGrantOption(objectType, objectName,
                    Privilege.valueOf(privilege.toUpperCase(Locale.ROOT)), true);
            }
            return;
        }
        final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRoleExact(targetName);
        final Privilege granted = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        holder.grantPrivilege(objectType, objectName, granted, grantor != null ? grantor : catalog.currentRoleForOwner());
        if (grantOption) {
            holder.setGrantOption(objectType, objectName, granted, true);
        }
    }

    /**
     * A bulk statement is a GRANT or a REVOKE on each object it reaches, so each object asks the authority its own
     * statement would, every one before anything changes. A future grant asks MANAGE GRANTS, or the ownership of
     * the schema when the schema has managed access.
     */
    private void requireBulkAuthority(final boolean future, final String objectKind, final String scopeType,
                                      final String scopeName, final List<String> privileges) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security == null) {
            return;
        }
        if (future) {
            final Schema schema = "SCHEMA".equals(scopeType)
                ? catalog.resolveSchema(QualifiedName.parse(scopeName)) : null;
            final boolean managed = schema != null && schema.isManagedAccess();
            security.checkManageGrants("future grants in " + scopeType.toLowerCase(Locale.ROOT) + " '" + scopeName
                + "'", managed ? "SCHEMA" : null, scopeName, managed ? schema.getOwner() : null);
            return;
        }
        for (final Map.Entry<String, String> object : ownersInScope(objectKind, scopeType, scopeName).entrySet()) {
            security.checkGrantAuthority(objectKind, object.getKey(), object.getValue(), privileges);
        }
    }

    /**
     * The role that owns each object a bulk statement reaches, by the name {@link AccessControlHandler#objectsInScope}
     * gives it: a routine's overloads each by their own signature.
     */
    private Map<String, String> ownersInScope(final String objectKind, final String scopeType,
                                              final String scopeName) {
        final Map<String, String> owners = new LinkedHashMap<>();
        if (!isRoutine(objectKind)) {
            for (final String name : AccessControlHandler.objectsInScope(catalog, objectKind, scopeType, scopeName)) {
                owners.put(name, ownerOf(objectKind, QualifiedName.parse(name).parts(), null, null, name));
            }
            return owners;
        }
        final List<Schema> schemas = new ArrayList<>();
        if ("DATABASE".equals(scopeType)) {
            schemas.addAll(catalog.databaseExact(scopeName).getAllSchemas());
        } else {
            schemas.add(catalog.resolveSchema(QualifiedName.parse(scopeName)));
        }
        for (final Schema schema : schemas) {
            if ("FUNCTION".equals(objectKind)) {
                for (final Function function : schema.getFunctions()) {
                    owners.put(GrantNames.routine(schema, function.getName(), function.getParameters()),
                        function.getOwner());
                }
            } else {
                for (final Procedure procedure : schema.getProcedures()) {
                    owners.put(GrantNames.routine(schema, procedure.getName(), procedure.getParameters()),
                        procedure.getOwner());
                }
            }
        }
        return owners;
    }

    /** The role that owns an object a statement names, found as its grants find it; null when none is recorded. */
    private String ownerOf(final String objectType, final String[] parts, final Integer arity,
                           final List<String> writtenTypes, final String objectName) {
        try {
            final GrantedObject granted = queryExecutor.grantedObject(objectType, parts, arity, writtenTypes);
            if (granted != null) {
                return granted.getOwner() == null || granted.getOwner().isEmpty() ? null : granted.getOwner();
            }
        } catch (final RuntimeException unresolved) {
            // A kind this cannot look up keeps the catalog's own answer below.
        }
        return catalog.getObjectOwnerRole(objectType.replace('_', ' '), objectName);
    }

    /** The privileges a GRANT naming its object passes on: ALL as the set it stands for on that kind. */
    private List<String> grantedPrivileges(final FrostlakeParser.PrivilegeListContext list, final String objectType) {
        final List<String> privileges = new ArrayList<>();
        if (list.ALL() != null) {
            privileges.addAll(allPrivilegesFor(objectType));
            return privileges;
        }
        for (final FrostlakeParser.PrivilegeContext privilege : list.privilege()) {
            privileges.add(privilegeName(privilege));
        }
        return privileges;
    }

    /**
     * GRANT ROLE and REVOKE ROLE are decided by the role's owner beside the roles that decide every grant; a system
     * role has no owner that decides it. The grantee is found first and the role then, as the statement finds them.
     */
    private void requireRoleAuthority(final boolean toUser, final String roleName, final String granteeName) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security == null) {
            return;
        }
        if (toUser) {
            catalog.getUser(granteeName);
        } else {
            catalog.getRoleExact(granteeName);
        }
        final Role role = catalog.getRoleExact(roleName);
        security.checkManageGrants("role '" + role.getName() + "'", "ROLE", role.getName(),
            catalog.isSystemRole(role.getName()) ? null : role.getOwner());
    }

    /** A privilege on the account is granted and revoked by the roles that decide every grant alone. */
    private void requireAccountAuthority() {
        if (queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().checkManageGrants("privileges on the account", null, null, null);
        }
    }

    /**
     * Revokes one privilege on one object from a user, an account role or a database role — or, with
     * {@code optionOnly}, only the grant option it carries.
     */
    private boolean revokeFrom(final DatabaseRole databaseGrantee, final boolean isUser, final String targetName,
                               final String privilege, final String objectType, final String written,
                               final boolean optionOnly) {
        final String objectName = GrantNames.of(catalog, objectType, written);
        final Privilege revoked = Privilege.valueOf(privilege.toUpperCase(Locale.ROOT));
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (isUser) {
            if (security != null && !security.revokesGrant(objectType, objectName,
                    catalog.getUser(targetName).getPrivilegeGrantor(objectType, objectName, revoked))) {
                return false;
            }
            if (optionOnly) {
                final boolean had = catalog.getUser(targetName).hasGrantOption(objectType, objectName, revoked);
                catalog.getUser(targetName).setGrantOption(objectType, objectName, revoked, false);
                return had;
            }
            final boolean held = catalog.getUser(targetName).hasPrivilege(objectType, objectName, revoked);
            catalog.revokePrivilegeFromUser(privilege, objectType, objectName, targetName);
            return held;
        }
        final Role holder = databaseGrantee != null ? databaseGrantee : catalog.getRoleExact(targetName);
        if (security != null && !security.revokesGrant(objectType, objectName,
                holder.getPrivilegeGrantor(objectType, objectName, revoked))) {
            return false;
        }
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
     * N objects affected.} A bulk GRANT counts the objects in its scope, and a bulk REVOKE the objects it took a
     * privilege, or a grant option, back from; a REVOKE that names its object counts the privileges it took back.
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

    /**
     * A grantee named without a principal name: ROLE written alone names a role called ROLE, and an application
     * role is named by its reference (live-verified).
     */
    private String granteeName(final FrostlakeParser.GrantStatementContext ctx) {
        if (!ctx.principalName().isEmpty()) {
            return principal(ctx.principalName(0));
        }
        if (ctx.APPLICATION() != null) {
            return applicationRoleName(ctx);
        }
        return "ROLE";
    }

    /** An application role's name, with the current database when its own is not written. */
    private String applicationRoleName(final FrostlakeParser.GrantStatementContext ctx) {
        final String[] parts = PrincipalNames.parts(ctx.referencedName(), queryExecutor);
        final String database = parts.length > 1 ? parts[0] : catalog.getCurrentDatabase();
        return QualifiedName.join(database, parts[parts.length - 1]);
    }

    /**
     * A share and an application role are grantees no catalog object models, so a grant to one is refused as the
     * account refuses a grantee that is not there: the share named with the account in one quoted part
     * (live-verified).
     */
    private void refuseUnmodelledGrantee(final FrostlakeParser.GrantStatementContext ctx) {
        if (ctx.SHARE() != null) {
            throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Share",
                DropCommandHandler.shareName(catalog.getAccountLocator(), principal(ctx.principalName(0)))));
        }
        if (ctx.APPLICATION() != null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Application role", applicationRoleName(ctx)));
        }
    }

    /** A grantee that does not exist is refused by its kind and name before anything about the grant. */
    private void requireGrantee(final boolean isUser, final String name) {
        if (isUser) {
            catalog.getUser(name);
        } else {
            catalog.getRoleExact(name);
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

    /**
     * The privileges a network policy, a network rule, a password policy, a secret or an external volume takes; null
     * for another kind.
     */
    private static List<String> securityObjectPrivileges(final String objectType) {
        switch (objectType) {
            case "NETWORK_POLICY":
            case "NETWORK_RULE":
            case "EXTERNAL_VOLUME":
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
            case "EXTERNAL_VOLUME":
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
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
