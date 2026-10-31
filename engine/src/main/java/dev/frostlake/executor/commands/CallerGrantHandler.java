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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.CallerGrant;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * GRANT CALLER, REVOKE CALLER and SHOW CALLER GRANTS (documented): the caller grants that say which of its caller's
 * privileges an executable running with restricted caller's rights may use, held by the executable's owner, a role
 * or a database role. A grant names privileges, or ALL of them, on one object, or INHERITED on every object of a kind
 * in the account, a database or a schema. Granting and revoking one takes MANAGE CALLER GRANTS on the account and at
 * least one privilege on the object; Snowflake Native Apps, which this engine does not model, name nothing.
 */
final class CallerGrantHandler {

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    CallerGrantHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /** GRANT CALLER, in any of its forms. */
    ResultSet grant(final FrostlakeParser.GrantCallerContext ctx) {
        requireAuthority();
        final List<String> privileges = privileges(ctx.callerPrivilegeList());
        if (ctx.INHERITED() == null) {
            final String[] target = target(ctx.objectType(), ctx.grantedObjectName());
            final Role grantee = grantee(ctx.callerGrantee());
            for (final String privilege : privileges) {
                grantee.addCallerGrant(privilege, target[0], target[1], null, null, StatementClock.instant());
            }
        } else {
            final String kind = kind(ctx.callerObjectKinds());
            final String[] scope = scope(ctx.callerGrantScope());
            final Role grantee = grantee(ctx.callerGrantee());
            for (final String privilege : privileges) {
                grantee.addCallerGrant(privilege, kind, null, scope[0], scope[1], StatementClock.instant());
            }
        }
        return StatusResults.of(StatusResults.EXECUTED);
    }

    /** REVOKE CALLER, in any of its forms: ALL takes back every caller grant on what it names. */
    ResultSet revoke(final FrostlakeParser.RevokeCallerContext ctx) {
        requireAuthority();
        final List<String> privileges = privileges(ctx.callerPrivilegeList());
        if (ctx.INHERITED() == null) {
            final String[] target = target(ctx.objectType(), ctx.grantedObjectName());
            final Role grantee = revokee(ctx.callerRevokee());
            for (final String privilege : privileges) {
                grantee.revokeCallerGrant(privilege, target[0], target[1], null, null);
            }
        } else {
            final String kind = kind(ctx.callerObjectKinds());
            final String[] scope = scope(ctx.callerGrantScope());
            final Role grantee = revokee(ctx.callerRevokee());
            for (final String privilege : privileges) {
                grantee.revokeCallerGrant(privilege, kind, null, scope[0], scope[1]);
            }
        }
        return StatusResults.of(StatusResults.EXECUTED);
    }

    /**
     * SHOW CALLER GRANTS: those on an object - granted on it, or inherited on its kind where it lives - every one in
     * the account, or those an executable owner holds; ordered by grantee, kind and privilege.
     */
    ResultSet show(final FrostlakeParser.AccessControlStatementContext ctx) {
        if (ctx.TO() == null && ctx.ON() == null) {
            // Without a target the listing would be the user's, and a user holds none (live-verified).
            throw new RuntimeException(SqlCompilationError.of("Caller grants are not applicable to 'USER'."));
        }
        final List<Role> grantees = new ArrayList<>();
        String[] target = null;
        if (ctx.TO() != null) {
            grantees.add(ctx.referencedName() != null
                ? AccessControlHandler.databaseRole(catalog, PrincipalNames.parts(ctx.referencedName(), queryExecutor))
                : catalog.getRoleExact(PrincipalNames.of(ctx.principalName(), queryExecutor)));
        } else {
            if (ctx.objectType() != null) {
                target = target(ctx.objectType(), ctx.grantedObjectName());
            }
            grantees.addAll(catalog.getAllRoles());
            for (final Database database : catalog.getAllDatabases()) {
                grantees.addAll(database.getDatabaseRoles());
            }
        }
        final List<Row> rows = new ArrayList<>();
        for (final Role role : grantees) {
            for (final CallerGrant grant : role.getCallerGrants()) {
                if (target == null || covers(grant, target[0], target[1])) {
                    rows.add(row(role, grant));
                }
            }
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row a, final Row b) {
                for (final int column : new int[] {9, 2, 3, 1}) {
                    final int order = String.valueOf(a.getValue(column)).compareTo(String.valueOf(b.getValue(column)));
                    if (order != 0) {
                        return order;
                    }
                }
                return 0;
            }
        });
        return new ResultSet(columns(), rows);
    }

    private static List<ResultSetColumn> columns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_inherited", StringType.VARCHAR),
            new ResultSetColumn("inherited_from", StringType.VARCHAR),
            new ResultSetColumn("inherited_from_database", StringType.VARCHAR),
            new ResultSetColumn("inherited_from_schema", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR));
    }

    private static Row row(final Role role, final CallerGrant grant) {
        final String from = grant.getInheritedFrom();
        String database = null;
        String schema = null;
        if ("DATABASE".equals(from)) {
            database = grant.getScopeName();
        } else if ("SCHEMA".equals(from)) {
            final String[] parts = QualifiedName.parse(grant.getScopeName()).parts();
            database = parts[0];
            schema = parts[parts.length - 1];
        }
        return new Row(Arrays.<Object>asList(
            ShowResultHelpers.createdOn(grant.getCreatedOn()),
            AccessControlListings.privilegeText(grant.getPrivilege()),
            grant.getObjectKind(),
            grant.getObjectName(),
            from == null ? "false" : "true",
            from,
            database,
            schema,
            role instanceof DatabaseRole ? AccessControlListings.DATABASE_ROLE : "ROLE",
            role instanceof DatabaseRole ? ((DatabaseRole) role).getQualifiedName() : role.getName()));
    }

    /** Whether a caller grant names some privilege on an object: on the object itself, or on its kind where it is. */
    private static boolean covers(final CallerGrant grant, final String kind, final String recorded) {
        final String[] containers = GrantNames.containers(kind, recorded);
        final String database = containers.length > 0 ? containers[0] : null;
        final String schema = containers.length > 1 ? QualifiedName.join(containers[0], containers[1]) : null;
        return grant.covers(grant.getPrivilege(), kind, recorded, database, schema);
    }

    /** Granting or revoking a caller grant takes MANAGE CALLER GRANTS on the account (documented). */
    private void requireAuthority() {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security != null && !security.holdsAccountPrivilege(Privilege.MANAGE_CALLER_GRANTS)) {
            throw new SecurityException("Cannot grant or revoke caller grants: requires MANAGE CALLER GRANTS on the"
                + " account (ACCOUNTADMIN holds it)");
        }
    }

    /** The privileges a caller grant names, by their enumeration spelling, or ALL. */
    private static List<String> privileges(final FrostlakeParser.CallerPrivilegeListContext list) {
        final List<String> privileges = new ArrayList<>();
        if (list == null) {
            privileges.add("ALL");
            return privileges;
        }
        for (final FrostlakeParser.PrivilegeContext privilege : list.privilege()) {
            final StringBuilder name = new StringBuilder();
            for (int i = 0; i < privilege.getChildCount(); i++) {
                if (i > 0) {
                    name.append('_');
                }
                name.append(privilege.getChild(i).getText().toUpperCase(Locale.ROOT));
            }
            privileges.add(name.toString());
        }
        return privileges;
    }

    /**
     * The object a caller grant names, which must be there and on which the session holds some privilege: its kind as
     * privileges record it, then its name in full.
     */
    private String[] target(final FrostlakeParser.ObjectTypeContext type,
                            final FrostlakeParser.GrantedObjectNameContext name) {
        final String kind = SecurableKinds.of(type);
        final String written = name.wholeObjectName() == null ? name.getText().toUpperCase(Locale.ROOT)
            : queryExecutor.resolveWholeObjectName(name.wholeObjectName());
        final boolean relation = "TABLE".equals(kind) || "VIEW".equals(kind) || "MATERIALIZED_VIEW".equals(kind)
            || "DYNAMIC_TABLE".equals(kind) || "EVENT_TABLE".equals(kind);
        final String objectName = relation ? queryExecutor.searchPathQualified(written) : written;
        queryExecutor.requireSecurable(kind, QualifiedName.parse(objectName).parts());
        final String recorded = GrantNames.of(catalog, kind, objectName);
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security != null
                && !security.holdsAnyPrivilege(kind, recorded, catalog.getObjectOwnerRole(kind, recorded))) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        return new String[] {kind, recorded};
    }

    /**
     * The singular kind an inherited caller grant names in the plural: TABLES is TABLE, DYNAMIC TABLES is
     * DYNAMIC_TABLE.
     */
    private static String kind(final FrostlakeParser.CallerObjectKindsContext kinds) {
        final StringBuilder words = new StringBuilder();
        final ParserRuleContext plural = kinds.bulkObjectType() != null
            ? kinds.bulkObjectType() : kinds;
        for (int i = 0; i < plural.getChildCount(); i++) {
            if (i > 0) {
                words.append('_');
            }
            words.append(plural.getChild(i).getText().toUpperCase(Locale.ROOT));
        }
        final String text = words.toString();
        return text.endsWith("S") ? text.substring(0, text.length() - 1) : text;
    }

    /** Where an inherited caller grant reaches: ACCOUNT, or DATABASE or SCHEMA and its name in full. */
    private String[] scope(final FrostlakeParser.CallerGrantScopeContext scope) {
        if (scope.ACCOUNT() != null) {
            return new String[] {"ACCOUNT", null};
        }
        if (scope.APPLICATION() != null) {
            throw new RuntimeException(SqlCompilationError.of("Application '"
                + PrincipalNames.of(scope.principalName(), queryExecutor) + "' does not exist or not authorized."));
        }
        final boolean schema = scope.SCHEMA() != null;
        return new String[] {schema ? "SCHEMA" : "DATABASE", AccessControlHandler.scopeName(catalog, schema,
            PrincipalNames.parts(scope.referencedName(), queryExecutor))};
    }

    /** The role or the database role a caller grant is given to; an application names none. */
    private Role grantee(final FrostlakeParser.CallerGranteeContext grantee) {
        if (grantee.APPLICATION() != null) {
            throw new RuntimeException(SqlCompilationError.of("Application '"
                + PrincipalNames.of(grantee.principalName(), queryExecutor) + "' does not exist or not authorized."));
        }
        if (grantee.DATABASE() != null) {
            return AccessControlHandler.databaseRole(catalog,
                PrincipalNames.parts(grantee.referencedName(), queryExecutor));
        }
        return catalog.getRoleExact(PrincipalNames.of(grantee.principalName(), queryExecutor));
    }

    /** The role or the database role a caller grant is taken back from. */
    private Role revokee(final FrostlakeParser.CallerRevokeeContext revokee) {
        if (revokee.DATABASE() != null) {
            return AccessControlHandler.databaseRole(catalog,
                PrincipalNames.parts(revokee.referencedName(), queryExecutor));
        }
        return catalog.getRoleExact(PrincipalNames.of(revokee.principalName(), queryExecutor));
    }
}
