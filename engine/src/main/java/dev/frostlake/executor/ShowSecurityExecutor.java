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
import dev.frostlake.types.DateTimeType;
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
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
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
            new ResultSetColumn("default_secondary_roles", StringType.VARCHAR),
            new ResultSetColumn("ext_authn_duo", StringType.VARCHAR),
            new ResultSetColumn("ext_authn_uid", StringType.VARCHAR),
            new ResultSetColumn("mins_to_bypass_mfa", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("last_success_login", StringType.VARCHAR),
            new ResultSetColumn("expires_at_time", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("locked_until_time", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("has_password", StringType.VARCHAR),
            new ResultSetColumn("has_rsa_public_key", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("has_mfa", StringType.VARCHAR),
            new ResultSetColumn("has_pat", StringType.VARCHAR),
            new ResultSetColumn("has_workload_identity", StringType.VARCHAR),
            new ResultSetColumn("is_from_organization_user", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final User user : catalog.getAllUsers()) {
            rows.add(new Row(Arrays.asList(
                user.getName(),
                ShowResultHelpers.createdOn(user.getCreatedTime()),
                user.getLoginName(),
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                null, null,
                ShowResultHelpers.text(user.getComment()),
                // SHOW USERS spells its flags as lower-case words, not the Y / N most SHOW output uses.
                String.valueOf(!user.isEnabled()),
                String.valueOf(user.isMustChangePassword()),
                "false",
                user.getDefaultWarehouse(),
                user.getDefaultNamespace(),
                user.getDefaultRole(),
                user.getDefaultSecondaryRoles(),
                "false", null, null,
                user.getOwner(),
                null, null, null,
                String.valueOf(user.getPassword() != null),
                "false",
                user.getUserType(),
                "false", "false", "false", "false"
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showRoles() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("is_inherited", StringType.VARCHAR),
            new ResultSetColumn("assigned_to_users", NumericType.INTEGER),
            new ResultSetColumn("granted_to_roles", NumericType.INTEGER),
            new ResultSetColumn("granted_roles", NumericType.INTEGER),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("is_from_organization_user_group", StringType.VARCHAR)
        );
        final SecurityManager securityManager = facade.getSecurityManager();
        // is_current must reflect the session's actual current role, not a hard-coded SYSADMIN.
        String curRole = securityManager != null ? securityManager.getSessionContext().getCurrentRole() : null;
        List<Row> rows = new ArrayList<>();
        for (final Role role : catalog.getAllRoles()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(role.getCreatedTime()),
                role.getName(),
                "N",
                role.getName().equals(curRole) ? "Y" : "N",
                "N",
                0, 0, role.getGrantedRoles().size(),
                ShowResultHelpers.text(role.getOwner()),
                ShowResultHelpers.text(role.getComment()),
                "N"
            )));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * DESCRIBE USER: one row per user property, in the order and with the defaults and descriptions
     * a real account returns. Properties the engine does not model report what live reports for a
     * freshly created user. PASSWORD is always null, as live never returns password material.
     *
     * <p>Both the value and default cells spell "nothing here" as the four-character text
     * {@code null}, not as a SQL NULL — measured, and the same habit SHOW TASKS has.
     */
    public ResultSet describeUser(final String name) {
        final User user = catalog.getUser(name);
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR),
            new ResultSetColumn("default", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR)
        );
        final Object[][] properties = {
            {"NAME", user.getName(), "null", "Name"},
            {"COMMENT", textOrNull(user.getComment()), "null", "user comment associated to an object in the dictionary"},
            {"DISPLAY_NAME", user.getDisplayName(), "null", "Display name of the associated object"},
            {"TYPE", user.getUserType(), "null", "Type of the account, application package, data exchange, data exchange listing, replication group, secret, network rule, user, or cortex extension."},
            {"LOGIN_NAME", user.getLoginName(), "null", "Login name of the user"},
            {"FIRST_NAME", textOrNull(user.getFirstName()), "null", "First name of the user"},
            {"MIDDLE_NAME", textOrNull(user.getMiddleName()), "null", "Middle name of the user"},
            {"LAST_NAME", textOrNull(user.getLastName()), "null", "Last name of the user"},
            {"EMAIL", textOrNull(user.getEmail()), "null", "Email address of the user"},
            {"PASSWORD", "null", "null", "Password of the user"},
            {"MUST_CHANGE_PASSWORD", String.valueOf(user.isMustChangePassword()), "false", "User must change the password"},
            {"DISABLED", String.valueOf(!user.isEnabled()), "false", "Whether the entity is disabled"},
            {"SNOWFLAKE_LOCK", "false", "false", "Whether the user, account, or organization is locked by Snowflake"},
            {"SNOWFLAKE_SUPPORT", "false", "false", "Snowflake Support is allowed to use the user or account"},
            {"DAYS_TO_EXPIRY", "null", "null", "User record will be treated as expired after specified number of days"},
            {"MINS_TO_UNLOCK", "null", "null", "Temporary lock on the user will be removed after specified number of minutes"},
            {"DEFAULT_WAREHOUSE", textOrNull(user.getDefaultWarehouse()), "null", "Default warehouse"},
            {"DEFAULT_NAMESPACE", textOrNull(user.getDefaultNamespace()), "null", "Default database namespace prefix for this user"},
            {"DEFAULT_ROLE", textOrNull(user.getDefaultRole()), "null", "Primary principal of user session will be set to this role"},
            {"DEFAULT_SECONDARY_ROLES", user.getDefaultSecondaryRoles(), "[ALL]", "The secondary roles will be set to all roles provided here."},
            {"EXT_AUTHN_DUO", "false", "false", "Whether Duo Security is enabled as second factor authentication"},
            {"EXT_AUTHN_UID", "null", "null", "External authentication ID of the user"},
            {"DEFAULT_MFA_METHOD", "null", "null", "Default MFA method for the user"},
            {"HAS_MFA", "false", "false", "Whether the user is enrolled in multi-factor authentication"},
            {"HAS_PAT", "false", "false", "Whether the user has a programmatic access token"},
            {"HAS_WORKLOAD_IDENTITY", "false", "false", "Whether the user has workload identity defined"},
            {"HAS_KEYPAIR", "false", "false", "Whether the user has a key pair"},
            {"IS_EMAIL_VERIFIED", "false", "false", "Whether the user's email address has been verified"},
            {"MINS_TO_BYPASS_MFA", "null", "null", "Temporary bypass MFA for the user for a specified number of minutes"},
            {"MINS_TO_BYPASS_NETWORK_POLICY", "null", "null", "Temporary bypass network policy on the user for a specified number of minutes"},
            {"RSA_PUBLIC_KEY", "null", "null", "RSA public key of the user"},
            {"RSA_PUBLIC_KEY_FP", "null", "null", "Fingerprint of user's RSA public key."},
            {"RSA_PUBLIC_KEY_LAST_SET_TIME", "null", "null", "The timestamp at which the RSA public key was last set for the user. Defaults to null if no RSA public key has been set yet."},
            {"RSA_PUBLIC_KEY_2", "null", "null", "Second RSA public key of the user"},
            {"RSA_PUBLIC_KEY_2_FP", "null", "null", "Fingerprint of user's second RSA public key."},
            {"RSA_PUBLIC_KEY_2_LAST_SET_TIME", "null", "null", "The timestamp at which the second RSA public key was last set for the user. Defaults to null if no second RSA public key has been set yet."},
            {"SCIM_USER_NAME", "null", "null", "User name of an user (required for SCIM provisioning)"},
            {"PASSWORD_LAST_SET_TIME", "null", "null", "The timestamp on which the last non-null password was set for the user. Default to null if no password has been set yet."},
            {"CUSTOM_LANDING_PAGE_URL", "null", "null", "Custom Landing Page of the user"},
            {"CUSTOM_LANDING_PAGE_URL_FLUSH_NEXT_UI_LOAD", "false", "false", "Whether or not to flush the custom landing page of the user on next UI load"},
            {"IS_FROM_ORGANIZATION_USER", "false", "false", "Whether the user is imported from an organization user."},
            {"ALLOWED_INTERFACES", "[ALL]", "null", "List of interfaces this user is allowed to access. Must be provided as a list: ('ALL'), ('SNOWFLAKE_INTELLIGENCE', 'STREAMLIT')"},
            {"LOCK_DETAILS", "{\"isAdminLocked\":false,\"isMfaLocked\":false,\"isMfaOTPLocked\":false,\"isMfaTOTPLocked\":false,\"isPasswordLocked\":false,\"isSnowflakeLocked\":false}", "null", "Details about why the user is locked, if the user is locked."}
        };
        final List<Row> rows = new ArrayList<>();
        for (final Object[] property : properties) {
            rows.add(new Row(Arrays.asList(property[0], property[1], property[2], property[3])));
        }
        return new ResultSet(columns, rows);
    }

    /** A property value, or the text "null" that DESCRIBE prints when it carries none. */
    private static String textOrNull(final String value) {
        return value != null ? value : "null";
    }

    public ResultSet showGrantsOnObject(final String objectType, final String objectName) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR),
            new ResultSetColumn("granted_by_role_type", StringType.VARCHAR)
        );
        final String reportedName = qualifiedObjectName(objectType, objectName);
        final List<Row> rows = new ArrayList<>();
        for (final Role role : catalog.getAllRoles()) {
            for (final Privilege priv : role.getPrivileges(objectType, objectName)) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(role.getCreatedTime()),
                    priv.toString(),
                    objectType, reportedName,
                    "ROLE", role.getName(),
                    "false", role.getPrivilegeGrantor(objectType, objectName, priv),
                    ShowResultHelpers.OWNER_ROLE_TYPE
                )));
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * How SHOW GRANTS names the object it was asked about: a schema-scoped object is reported with
     * its database and schema, an account-level one by its bare name.
     */
    private String qualifiedObjectName(final String objectType, final String objectName) {
        if (objectName.indexOf('.') >= 0 || !isSchemaScoped(objectType)) {
            return objectName;
        }
        final String databaseName = catalog.getCurrentDatabase();
        final String schemaName = catalog.getCurrentSchema();
        if (databaseName == null || schemaName == null) {
            return objectName;
        }
        return databaseName + "." + schemaName + "." + objectName;
    }

    /** Whether objects of this type live inside a schema rather than at the account level. */
    private boolean isSchemaScoped(final String objectType) {
        return !"DATABASE".equalsIgnoreCase(objectType)
            && !"SCHEMA".equalsIgnoreCase(objectType)
            && !"WAREHOUSE".equalsIgnoreCase(objectType)
            && !"ROLE".equalsIgnoreCase(objectType)
            && !"USER".equalsIgnoreCase(objectType)
            && !"INTEGRATION".equalsIgnoreCase(objectType);
    }

    /**
     * SHOW GRANTS OF ROLE: who holds the role. Live's five columns are its own shape — narrower
     * than every other grants listing.
     */
    public ResultSet showGrantsOfRole(final String roleName) {
        final Role role = catalog.getRole(roleName);
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("role", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final User user : catalog.getAllUsers()) {
            if (user.getGrantedRoles().contains(role.getName())) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(user.getCreatedTime()),
                    role.getName(), "USER", user.getName(), user.getRoleGrantor(role.getName()))));
            }
        }
        for (final Role holder : catalog.getAllRoles()) {
            if (holder.getGrantedRoles().contains(role.getName())) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(holder.getCreatedTime()),
                    role.getName(), "ROLE", holder.getName(),
                    holder.getRoleGrantor(role.getName()))));
            }
        }
        return new ResultSet(columns, rows);
    }

    /**
     * Bare SHOW GRANTS: everything granted to the session's current user. Live carries a {@code role}
     * column here that the ON and TO ROLE listings do not.
     */
    public ResultSet showGrantsForCurrentUser() {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("role", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        final String currentUser = catalog.currentUserForStage();
        if (catalog.hasUser(currentUser)) {
            final User user = catalog.getUser(currentUser);
            for (final String roleName : user.getGrantedRoles()) {
                // As in SHOW GRANTS TO USER, live never surfaces the PUBLIC membership every user has.
                if ("PUBLIC".equals(roleName)) {
                    continue;
                }
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(user.getCreatedTime()),
                    "USAGE", "ROLE", roleName, roleName,
                    "USER", user.getName(),
                    "false", user.getRoleGrantor(roleName))));
            }
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showGrantsTo(final String targetType, final String targetName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
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
                    ShowResultHelpers.createdOn(user.getCreatedTime()),
                    "USAGE", "ROLE", roleName,
                    "USER", user.getName(),
                    "false", user.getRoleGrantor(roleName)
                )));
            }
        } else if ("ROLE".equals(targetType)) {
            Role role = catalog.getRole(targetName);
            for (final String grantedRoleName : role.getGrantedRoles()) {
                rows.add(new Row(Arrays.asList(
                    ShowResultHelpers.createdOn(role.getCreatedTime()),
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
                        ShowResultHelpers.createdOn(role.getCreatedTime()),
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
