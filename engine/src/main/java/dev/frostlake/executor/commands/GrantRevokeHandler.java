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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
            if (ctx.bulkObjectType() != null) {
                // GRANT privs ON ALL/FUTURE <types> IN DATABASE/SCHEMA/ACCOUNT <name> TO USER/ROLE target
                final boolean isFuture = ctx.FUTURE() != null;
                final String objectType = ctx.bulkObjectType().getText().toUpperCase();
                final FrostlakeParser.BulkScopeContext scope = ctx.bulkScope();
                final String scopeType = scope.DATABASE() != null ? "DATABASE"
                    : scope.SCHEMA() != null ? "SCHEMA" : "ACCOUNT";
                final String scopeName = scope.qualifiedName() != null
                    ? scope.qualifiedName().getText().toUpperCase() : "ACCOUNT";
                // The scope must exist before anything is granted in it (live-verified).
                if (scope.SCHEMA() != null) {
                    catalog.resolveSchema(QualifiedName.of(ParseTreeText.qualifiedNameParts(scope.qualifiedName())));
                } else if (scope.DATABASE() != null) {
                    catalog.databaseExact(ParseTreeText.qualifiedNameParts(scope.qualifiedName())[0]);
                }
                String privilege = "ALL";
                if (ctx.privilegeList().ALL() == null) {
                    final StringBuilder names = new StringBuilder();
                    for (final FrostlakeParser.PrivilegeContext p : ctx.privilegeList().privilege()) {
                        names.append(names.length() == 0 ? "" : ",").append(p.getText().toUpperCase());
                    }
                    if (names.length() > 0) {
                        privilege = names.toString();
                    }
                }
                final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
                final String targetName = ids != null && !ids.isEmpty() ? ids.get(ids.size() - 1).getText() : "";
                final boolean isUser = ctx.USER() != null;
                final String grantKey = (isFuture ? "FUTURE_" : "ALL_") + objectType + "_IN_" + scopeType;
                if (isUser) {
                    catalog.grantPrivilegeToUser(privilege, grantKey, scopeName, targetName);
                } else {
                    catalog.grantPrivilegeToRole(privilege, grantKey, scopeName, targetName);
                }
                logger.trace("Granted {} on {}{} in {} {} to {}", privilege,
                    isFuture ? "FUTURE " : "ALL ", objectType, scopeType, scopeName, targetName);

            } else if (ctx.ROLE() != null && ctx.objectType() == null && ctx.globalPrivilegeList() == null) {
                // GRANT ROLE role_name TO USER/ROLE target_name
                // identifiers: 0=role_name, 1=target_name
                final String roleName = visitor.getText(ctx.identifier(0));
                final String targetName2 = visitor.getText(ctx.identifier(1));

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
                final String roleName = visitor.getText(ctx.identifier(0));

                for (final FrostlakeParser.GlobalPrivilegeContext privCtx : ctx.globalPrivilegeList().globalPrivilege()) {
                    final String privilege = privilegeName(privCtx);
                    catalog.grantPrivilegeToRole(privilege, "ACCOUNT", "ACCOUNT", roleName);
                    logger.trace("Granted global privilege {} to role {}", privilege, roleName);
                }

            } else if (ctx.ACCOUNT() != null) {
                // GRANT privileges ON ACCOUNT TO USER/ROLE target_name
                // identifiers: 0=target_name
                final String targetName = visitor.getText(ctx.identifier(0));
                final boolean isUser = ctx.USER() != null;

                if (ctx.privilegeList().ALL() != null) {
                    if (isUser) {
                        catalog.grantPrivilegeToUser("ALL", "ACCOUNT", "ACCOUNT", targetName);
                        logger.trace("Granted ALL privileges on ACCOUNT to user {}", targetName);
                    } else {
                        catalog.grantPrivilegeToRole("ALL", "ACCOUNT", "ACCOUNT", targetName);
                        logger.trace("Granted ALL privileges on ACCOUNT to role {}", targetName);
                    }
                } else {
                    for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                        final String privilege = privilegeName(privCtx);
                        if (isUser) {
                            catalog.grantPrivilegeToUser(privilege, "ACCOUNT", "ACCOUNT", targetName);
                            logger.trace("Granted {} on ACCOUNT to user {}", privilege, targetName);
                        } else {
                            catalog.grantPrivilegeToRole(privilege, "ACCOUNT", "ACCOUNT", targetName);
                            logger.trace("Granted {} on ACCOUNT to role {}", privilege, targetName);
                        }
                    }
                }

            } else if (ctx.privilegeList() != null || ctx.OWNERSHIP() != null) {
                // GRANT privileges/OWNERSHIP ON object TO USER/ROLE target_name
                // identifiers: 0=target_name
                final String objectName = visitor.getText(ctx.qualifiedName());
                final String targetName = visitor.getText(ctx.identifier(0));
                final String objectType = ctx.objectType().getText().toUpperCase();
                final boolean isUser = ctx.USER() != null;
                // The object is found before the grantee or any authority is judged (live-verified).
                queryExecutor.requireSecurable(objectType, ParseTreeText.qualifiedNameParts(ctx.qualifiedName()),
                    ctx.LPAREN() == null ? null
                        : Integer.valueOf(ctx.identifierList() == null ? 0 : ctx.identifierList().identifier().size()));

                // Only an owner / administrative role may grant privileges on an object.
                if (queryExecutor.getSecurityManager() != null) {
                    queryExecutor.getSecurityManager().checkGrantAuthority(objectType, objectName);
                }

                if (ctx.OWNERSHIP() != null) {
                    // Ownership belongs to ROLES only. Live-verified on a real account:
                    // GRANT OWNERSHIP ON TABLE t TO USER u fails "SQL execution error: Cannot grant
                    // OWNERSHIP to users." while the same statement TO ROLE succeeds.
                    if (isUser) {
                        throw new RuntimeException("SQL execution error: Cannot grant OWNERSHIP to users.");
                    } else {
                        catalog.grantPrivilegeToRole("OWNERSHIP", objectType, objectName, targetName);
                        logger.trace("Granted OWNERSHIP on {} {} to role {}", objectType, objectName, targetName);
                    }
                } else {
                    // Handle privilege list
                    if (ctx.privilegeList().ALL() != null) {
                        // GRANT ALL stores the EXPANSION, never an ALL marker — live-verified:
                        // SHOW GRANTS lists the individual privileges, revoking one leaves the
                        // rest, and OWNERSHIP is never part of the set. A type without a modeled
                        // vocabulary keeps the legacy ALL row.
                        final List<String> expansion = allPrivilegesFor(objectType);
                        for (final String privilege : expansion) {
                            if (isUser) {
                                catalog.grantPrivilegeToUser(privilege, objectType, objectName, targetName);
                            } else {
                                catalog.grantPrivilegeToRole(privilege, objectType, objectName, targetName);
                            }
                        }
                        logger.trace("Granted ALL privileges ({}) on {} {} to {} {}", expansion,
                            objectType, objectName, isUser ? "user" : "role", targetName);
                    } else {
                        // Grant specific privileges
                        for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                            final String privilege = privilegeName(privCtx);
                            rejectInvalidPrivilegeForObjectType(privilege, objectType);
                            // OWNERSHIP also matches the generic privilegeList alternative, which the
                            // parser prefers over the dedicated GRANT OWNERSHIP one — so the ROLES-only
                            // rule has to be enforced here too. Live-verified: GRANT OWNERSHIP
                            // ON TABLE t TO USER u fails "SQL execution error: Cannot grant OWNERSHIP to
                            // users." while the same statement TO ROLE succeeds.
                            if (isUser && "OWNERSHIP".equals(privilege)) {
                                throw new RuntimeException("SQL execution error: Cannot grant OWNERSHIP to users.");
                            }
                            if (isUser) {
                                catalog.grantPrivilegeToUser(privilege, objectType, objectName, targetName);
                                logger.trace("Granted {} on {} {} to user {}", privilege, objectType, objectName, targetName);
                            } else {
                                catalog.grantPrivilegeToRole(privilege, objectType, objectName, targetName);
                                logger.trace("Granted {} on {} {} to role {}", privilege, objectType, objectName, targetName);
                            }
                        }
                    }
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
            if (ctx.ROLE() != null && ctx.objectType() == null && ctx.globalPrivilegeList() == null) {
                // REVOKE ROLE role_name FROM USER/ROLE target_name
                // This is the first alternative - revoking a role from a user or role
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

                for (final FrostlakeParser.GlobalPrivilegeContext privCtx : ctx.globalPrivilegeList().globalPrivilege()) {
                    final String privilege = privilegeName(privCtx);
                    catalog.revokePrivilegeFromRole(privilege, "ACCOUNT", "ACCOUNT", roleName);
                    logger.trace("Revoked global privilege {} from role {}", privilege, roleName);
                }

            } else if (ctx.ACCOUNT() != null) {
                // REVOKE privileges ON ACCOUNT FROM USER/ROLE target_name
                // identifiers: 0=target_name
                final String targetName = visitor.getText(ctx.identifier(0));
                final boolean isUser = ctx.USER() != null;

                if (ctx.privilegeList().ALL() != null) {
                    if (isUser) {
                        catalog.revokePrivilegeFromUser("ALL", "ACCOUNT", "ACCOUNT", targetName);
                        logger.trace("Revoked ALL privileges on ACCOUNT from user {}", targetName);
                    } else {
                        catalog.revokePrivilegeFromRole("ALL", "ACCOUNT", "ACCOUNT", targetName);
                        logger.trace("Revoked ALL privileges on ACCOUNT from role {}", targetName);
                    }
                } else {
                    for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                        final String privilege = privilegeName(privCtx);
                        if (isUser) {
                            catalog.revokePrivilegeFromUser(privilege, "ACCOUNT", "ACCOUNT", targetName);
                            logger.trace("Revoked {} on ACCOUNT from user {}", privilege, targetName);
                        } else {
                            catalog.revokePrivilegeFromRole(privilege, "ACCOUNT", "ACCOUNT", targetName);
                            logger.trace("Revoked {} on ACCOUNT from role {}", privilege, targetName);
                        }
                    }
                }

            } else if (ctx.privilegeList() != null || ctx.OWNERSHIP() != null) {
                // REVOKE privileges/OWNERSHIP ON object FROM USER/ROLE target_name
                // identifiers: 0=target_name
                final String objectName = visitor.getText(ctx.qualifiedName());
                final String targetName = visitor.getText(ctx.identifier(0));
                final String objectType = ctx.objectType().getText().toUpperCase();
                final boolean isUser = ctx.USER() != null;
                // The object is found before the grantee or any authority is judged (live-verified).
                queryExecutor.requireSecurable(objectType, ParseTreeText.qualifiedNameParts(ctx.qualifiedName()),
                    ctx.LPAREN() == null ? null
                        : Integer.valueOf(ctx.identifierList() == null ? 0 : ctx.identifierList().identifier().size()));

                if (ctx.OWNERSHIP() != null) {
                    // Revoke ownership
                    if (isUser) {
                        catalog.revokePrivilegeFromUser("OWNERSHIP", objectType, objectName, targetName);
                        logger.trace("Revoked OWNERSHIP on {} {} from user {}", objectType, objectName, targetName);
                    } else {
                        catalog.revokePrivilegeFromRole("OWNERSHIP", objectType, objectName, targetName);
                        logger.trace("Revoked OWNERSHIP on {} {} from role {}", objectType, objectName, targetName);
                    }
                } else {
                    // Handle privilege list
                    if (ctx.privilegeList().ALL() != null) {
                        // REVOKE ALL removes the expanded set (and a legacy stored ALL marker),
                        // mirroring the grant-side expansion.
                        final List<String> allToRevoke = new ArrayList<>(allPrivilegesFor(objectType));
                        if (!allToRevoke.contains("ALL")) {
                            allToRevoke.add("ALL");
                        }
                        for (final String privilege : allToRevoke) {
                            if (isUser) {
                                catalog.revokePrivilegeFromUser(privilege, objectType, objectName, targetName);
                            } else {
                                catalog.revokePrivilegeFromRole(privilege, objectType, objectName, targetName);
                            }
                        }
                        logger.trace("Revoked ALL privileges on {} {} from {} {}", objectType, objectName,
                            isUser ? "user" : "role", targetName);
                    } else {
                        // Revoke specific privileges
                        for (final FrostlakeParser.PrivilegeContext privCtx : ctx.privilegeList().privilege()) {
                            final String privilege = privilegeName(privCtx);
                            if (isUser) {
                                catalog.revokePrivilegeFromUser(privilege, objectType, objectName, targetName);
                                logger.trace("Revoked {} on {} {} from user {}", privilege, objectType, objectName, targetName);
                            } else {
                                catalog.revokePrivilegeFromRole(privilege, objectType, objectName, targetName);
                                logger.trace("Revoked {} on {} {} from role {}", privilege, objectType, objectName, targetName);
                            }
                        }
                    }
                }
            }

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
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
                || (schemaLevelCreate && "DATABASE".equals(objectType))) {
            throw new RuntimeException("SQL compilation error:\nInvalid object type '" + objectType
                + "' for privilege '" + privilege.replace('_', ' ') + "'.");
        }
    }

    /**
     * The privileges {@code GRANT ALL} expands to for one securable type. Live stores the
     * expansion, never an ALL marker: SHOW GRANTS lists individual privileges, revoking one leaves
     * the rest, REVOKE ALL clears them, and OWNERSHIP is never included. The TABLE / VIEW / SCHEMA
     * / DATABASE sets are the measured core restricted to the privileges this engine models (a
     * real account also lists edition-dependent extras such as APPLYBUDGET); the remaining types
     * follow the documented stable sets. An unmapped type falls back to the legacy single ALL row.
     */
    private List<String> allPrivilegesFor(final String objectType) {
        switch (objectType) {
            case "TABLE":
            case "VIEW":
                // Live expands a view's ALL to the same DML set as a table's.
                return List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES");
            case "SCHEMA":
                return List.of("MODIFY", "MONITOR", "USAGE",
                    "CREATE_TABLE", "CREATE_VIEW", "CREATE_STAGE", "CREATE_FILE_FORMAT",
                    "CREATE_SEQUENCE", "CREATE_FUNCTION", "CREATE_PROCEDURE", "CREATE_PIPE",
                    "CREATE_STREAM", "CREATE_TASK", "CREATE_MASKING_POLICY",
                    "CREATE_ROW_ACCESS_POLICY", "CREATE_TAG");
            case "DATABASE":
                return List.of("CREATE_SCHEMA", "MODIFY", "MONITOR", "USAGE");
            case "WAREHOUSE":
                return List.of("MODIFY", "MONITOR", "OPERATE", "USAGE");
            case "STAGE":
                return List.of("READ", "USAGE", "WRITE");
            case "FUNCTION":
            case "PROCEDURE":
            case "SEQUENCE":
                return List.of("USAGE");
            case "STREAM":
                return List.of("SELECT");
            case "TASK":
                return List.of("MONITOR", "OPERATE");
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
