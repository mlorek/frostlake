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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.User;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SHOW / DESCRIBE handlers for the security object family: users, roles and grants. Extracted from
 * {@link ShowCommandExecutor}, which delegates here. The {@code SecurityManager} is read live from the
 * facade because it is installed after construction.
 */
final class ShowSecurityExecutor {

    private final Catalog catalog;
    private final ShowCommandExecutor facade;

    ShowSecurityExecutor(final Catalog catalog, final ShowCommandExecutor facade) {
        this.catalog = catalog;
        this.facade = facade;
    }

    public ResultSet showUsers() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("login_name", StringType.VARCHAR),
            new ResultSetColumn("display_name", StringType.VARCHAR),
            new ResultSetColumn("first_name", StringType.VARCHAR),
            new ResultSetColumn("last_name", StringType.VARCHAR),
            new ResultSetColumn("email", StringType.VARCHAR),
            new ResultSetColumn("mins_to_unlock", StringType.VARCHAR),
            new ResultSetColumn("days_to_expiry", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("disabled", StringType.VARCHAR),
            new ResultSetColumn("must_change_password", StringType.VARCHAR),
            new ResultSetColumn("snowflake_lock", StringType.VARCHAR),
            new ResultSetColumn("default_warehouse", StringType.VARCHAR),
            new ResultSetColumn("default_namespace", StringType.VARCHAR),
            new ResultSetColumn("default_role", StringType.VARCHAR),
            new ResultSetColumn("has_password", StringType.VARCHAR),
            new ResultSetColumn("has_rsa_public_key", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR)
        );
        List<Row> rows = new ArrayList<>();
        for (final User user : catalog.getAllUsers()) {
            rows.add(new Row(Arrays.asList(
                user.getName(),
                ShowResultHelpers.createdOnText(user.getCreatedTime()),
                user.getName(),
                user.getName(),
                null, null, null,
                null, null,
                user.getComment(),
                user.isEnabled() ? "false" : "true",
                "false", "false",
                null, null,
                user.getDefaultRole(),
                user.getPassword() != null ? "true" : "false",
                "false",
                user.getOwner()
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showRoles() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("is_inherited", StringType.VARCHAR),
            new ResultSetColumn("assigned_to_users", NumericType.INTEGER),
            new ResultSetColumn("granted_to_roles", NumericType.INTEGER),
            new ResultSetColumn("granted_roles", NumericType.INTEGER),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR)
        );
        final SecurityManager securityManager = facade.getSecurityManager();
        // is_current must reflect the session's actual current role, not a hard-coded SYSADMIN.
        String curRole = securityManager != null ? securityManager.getSessionContext().getCurrentRole() : null;
        List<Row> rows = new ArrayList<>();
        for (final Role role : catalog.getAllRoles()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOnText(role.getCreatedTime()),
                role.getName(),
                "N",
                role.getName().equals(curRole) ? "Y" : "N",
                "N",
                0, 0, role.getGrantedRoles().size(),
                role.getOwner(),
                role.getComment()
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet describeUser(final String name) {
        final User user = catalog.getUser(name);
        if (user == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("User", name));
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("name", user.getName())));
        if (user.getComment() != null) {
            rows.add(new Row(Arrays.asList("comment", user.getComment())));
        }
        if (user.getDefaultRole() != null) {
            rows.add(new Row(Arrays.asList("default_role", user.getDefaultRole())));
        }
        if (user.getOwner() != null) {
            rows.add(new Row(Arrays.asList("owner", user.getOwner())));
        }
        return propertyValueResult(rows);
    }

    public ResultSet showGrantsOnObject(final String objectType, final String objectName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR)
        );
        List<Row> rows = new ArrayList<>();
        for (final Role role : catalog.getAllRoles()) {
            for (final Privilege priv : role.getPrivileges(objectType, objectName)) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOnText(role.getCreatedTime()),
                    priv.toString(),
                    objectType, objectName,
                    "ROLE", role.getName(),
                    "false", role.getPrivilegeGrantor(objectType, objectName, priv)
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showGrantsTo(final String targetType, final String targetName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR)
        );
        List<Row> rows = new ArrayList<>();
        if ("USER".equals(targetType)) {
            User user = catalog.getUser(targetName);
            for (final String roleName : user.getGrantedRoles()) {
                // Live Snowflake never surfaces PUBLIC membership here — re-probed on a real account
                //: a fresh user shows zero grants, and after an EXPLICIT
                // "GRANT ROLE PUBLIC TO USER u" the listing is STILL empty, while granting any other
                // role immediately shows one row. Every user is in PUBLIC, so the grant is a no-op to
                // report. The membership itself stays modeled — only this listing skips it.
                if ("PUBLIC".equals(roleName)) {
                    continue;
                }
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOnText(user.getCreatedTime()),
                    "USAGE", "ROLE", roleName,
                    "USER", user.getName(),
                    "false", user.getRoleGrantor(roleName)
                )));
            }
        } else if ("ROLE".equals(targetType)) {
            Role role = catalog.getRole(targetName);
            for (final String grantedRoleName : role.getGrantedRoles()) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOnText(role.getCreatedTime()),
                    "USAGE", "ROLE", grantedRoleName,
                    "ROLE", role.getName(),
                    "false", role.getRoleGrantor(grantedRoleName)
                )));
            }
            for (final Map.Entry<String, Set<Privilege>> entry : role.getAllPrivileges().entrySet()) {
                String[] parts = entry.getKey().split(":");
                String objType = parts[0];
                String objName = parts[1];
                for (final Privilege priv : entry.getValue()) {
                    rows.add(new Row(Arrays.asList(
                        ShowResultHelpers.createdOnText(role.getCreatedTime()),
                        priv.toString(), objType, objName,
                        "ROLE", role.getName(),
                        "false", role.getPrivilegeGrantor(objType, objName, priv)
                    )));
                }
            }
        }
        return new ResultSet(columns, rows);
    }

    /** Build a two-column (property, value) describe result. */
    private ResultSet propertyValueResult(final List<Row> rows) {
        return ShowResultHelpers.propertyValueResult(rows);
    }
}
