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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.FutureGrant;
import dev.frostlake.metastore.model.ManagedAccount;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.User;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The listings of database roles, of the grants that name them, of grants on roles, of future grants and of
 * managed accounts. The grants listings keep the column shapes of their account-role siblings: SHOW GRANTS TO
 * and OF a database role those of SHOW GRANTS TO and OF ROLE, SHOW GRANTS ON a role that of SHOW GRANTS ON an
 * object, and SHOW FUTURE GRANTS its own, whose columns read {@code grant_on} and {@code grant_to}.
 */
public final class AccessControlListings {

    /** How a database role is named as a grantee. */
    public static final String DATABASE_ROLE = "DATABASE_ROLE";

    private final Catalog catalog;

    /**
     * @param catalog the catalog the listings read
     */
    public AccessControlListings(final Catalog catalog) {
        this.catalog = catalog;
    }

    /** SHOW DATABASE ROLES IN DATABASE: the database's roles, by name. */
    public ResultSet databaseRoles(final Database database) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("is_inherited", StringType.VARCHAR),
            new ResultSetColumn("granted_to_roles", NumericType.INTEGER),
            new ResultSetColumn("granted_to_database_roles", NumericType.INTEGER),
            new ResultSetColumn("granted_database_roles", NumericType.INTEGER),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        final List<DatabaseRole> roles = database == null ? new ArrayList<DatabaseRole>() : database.getDatabaseRoles();
        for (final DatabaseRole role : roles) {
            int toRoles = 0;
            for (final Role holder : catalog.getAllRoles()) {
                if (holder.getDatabaseRoleGrants().containsKey(role.getQualifiedName())) {
                    toRoles++;
                }
            }
            int toDatabaseRoles = 0;
            for (final DatabaseRole holder : roles) {
                if (holder.getDatabaseRoleGrants().containsKey(role.getQualifiedName())) {
                    toDatabaseRoles++;
                }
            }
            rows.add(new Row(Arrays.<Object>asList(
                ShowResultHelpers.createdOn(role.getCreatedTime()),
                role.getName(), "N", "N", "N",
                toRoles, toDatabaseRoles, role.getDatabaseRoleGrants().size(),
                ShowResultHelpers.text(role.getOwner()),
                ShowResultHelpers.text(role.getComment()),
                ShowResultHelpers.OWNER_ROLE_TYPE)));
        }
        return new ResultSet(columns, sortedByName(rows, 1));
    }

    /** The eight columns of SHOW GRANTS TO ROLE, which SHOW GRANTS TO DATABASE ROLE shares. */
    private static List<ResultSetColumn> grantsToColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR)
        );
    }

    /**
     * The grants a role holds as SHOW GRANTS TO lists them: the database roles granted to it, then each privilege
     * on each object, with the grant option each carries.
     *
     * @param role the grantee
     * @param granteeKind ROLE or DATABASE_ROLE
     * @param granteeName the grantee as the listing names it
     */
    public ResultSet grantsTo(final Role role, final String granteeKind, final String granteeName) {
        final List<Row> rows = new ArrayList<>();
        for (final Map.Entry<String, String> held : role.getDatabaseRoleGrants().entrySet()) {
            if (!databaseRoleExists(held.getKey())) {
                continue;
            }
            final Instant granted = role.getDatabaseRoleGrantTime(held.getKey());
            rows.add(new Row(Arrays.<Object>asList(
                granted != null ? ShowResultHelpers.createdOn(granted) : ShowResultHelpers.createdOn(role.getCreatedTime()),
                "USAGE", DATABASE_ROLE, held.getKey(), granteeKind, granteeName, "false", held.getValue())));
        }
        if (role instanceof DatabaseRole) {
            // A database role holds USAGE on its own database from the moment it exists, granted by no one; a USAGE
            // granted to it explicitly is listed beside that one, not in its place.
            final String database = ((DatabaseRole) role).getDatabase();
            rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(role.getCreatedTime()), "USAGE",
                "DATABASE", database, granteeKind, granteeName, "false", "")));
            for (final String granted : role.getGrantedRoles()) {
                rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(role.getCreatedTime()),
                    "USAGE", "ROLE", granted, granteeKind, granteeName, "false", role.getRoleGrantor(granted))));
            }
        }
        for (final Map.Entry<String, Set<Privilege>> entry : role.getAllPrivileges().entrySet()) {
            final int split = entry.getKey().indexOf(':');
            final String objectType = entry.getKey().substring(0, split);
            final String objectName = entry.getKey().substring(split + 1);
            for (final Privilege privilege : entry.getValue()) {
                final Instant granted = role.getPrivilegeGrantTime(objectType, objectName, privilege);
                rows.add(new Row(Arrays.<Object>asList(
                    granted != null ? ShowResultHelpers.createdOn(granted) : ShowResultHelpers.createdOn(role.getCreatedTime()),
                    privilege.displayName(), objectType, objectName, granteeKind, granteeName,
                    role.hasGrantOption(objectType, objectName, privilege) ? "true" : "false",
                    role.getPrivilegeGrantor(objectType, objectName, privilege))));
            }
        }
        return new ResultSet(grantsToColumns(), sortedGrants(rows));
    }

    /**
     * SHOW GRANTS TO's order: by the kind of object granted on, then its name, then the privilege — not the order
     * the grants were made in (live-verified with the grants made shuffled across a database, two schemas and a
     * view: DATABASE before SCHEMA before VIEW, ORD_A before ORD_B, and each object's privileges alphabetical).
     */
    static List<Row> sortedGrants(final List<Row> rows) {
        final List<Row> sorted = new ArrayList<>(rows);
        Collections.sort(sorted, new Comparator<Row>() {
            @Override
            public int compare(final Row a, final Row b) {
                for (final int column : new int[] {2, 3, 1}) {
                    final int order = String.valueOf(a.getValue(column)).compareTo(String.valueOf(b.getValue(column)));
                    if (order != 0) {
                        return order;
                    }
                }
                return 0;
            }
        });
        return sorted;
    }

    /**
     * Whether the database role a grant names still exists: a database role goes with its database, and a grant
     * of it is not listed while the database is dropped.
     *
     * @param qualified the role's qualified name, {@code DB.ROLE}
     */
    public boolean databaseRoleExists(final String qualified) {
        final int dot = qualified.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        try {
            final Database database = catalog.getDatabase(qualified.substring(0, dot));
            return database != null && database.getDatabaseRole(qualified.substring(dot + 1)) != null;
        } catch (final RuntimeException dropped) {
            return false;
        }
    }

    /** SHOW GRANTS OF DATABASE ROLE: the account roles, database roles and users it is granted to. */
    public ResultSet grantsOfDatabaseRole(final DatabaseRole role) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("role", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR)
        );
        final String qualified = role.getQualifiedName();
        final List<Row> rows = new ArrayList<>();
        for (final Role holder : catalog.getAllRoles()) {
            addHolder(rows, holder, qualified, "ROLE", holder.getName());
        }
        final Database database = catalog.getDatabase(role.getDatabase());
        if (database != null) {
            for (final DatabaseRole holder : database.getDatabaseRoles()) {
                // A database role as GRANTEE is named BARE in every listing, where the same role as the
                // OBJECT of a grant keeps its database (live-verified).
                addHolder(rows, holder, qualified, DATABASE_ROLE, holder.getName());
            }
        }
        for (final User user : catalog.getAllUsers()) {
            final String grantor = user.getDatabaseRoleGrants().get(qualified);
            if (grantor != null) {
                rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(user.getCreatedTime()),
                    qualified, "USER", user.getName(), grantor)));
            }
        }
        return new ResultSet(columns, rows);
    }

    private static void addHolder(final List<Row> rows, final Role holder, final String qualified,
                                  final String kind, final String name) {
        final String grantor = holder.getDatabaseRoleGrants().get(qualified);
        if (grantor != null) {
            final Instant granted = holder.getDatabaseRoleGrantTime(qualified);
            rows.add(new Row(Arrays.<Object>asList(
                granted != null ? ShowResultHelpers.createdOn(granted) : ShowResultHelpers.createdOn(holder.getCreatedTime()),
                qualified, kind, name, grantor)));
        }
    }

    /**
     * SHOW GRANTS ON ROLE and ON DATABASE ROLE: the OWNERSHIP of the role, held by its owner, and a USAGE row for
     * each holder of the role, in the columns of SHOW GRANTS ON an object, ordered by grantee and then privilege.
     * An account role's holders listed are the account roles it is granted to — the users that hold it are not
     * listed there (they are under SHOW GRANTS OF ROLE); a database role's are the account roles, database roles
     * and users it is granted to.
     *
     * @param role the role asked about
     */
    public ResultSet grantsOnRole(final Role role) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("granted_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("granted_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR),
            new ResultSetColumn("granted_by", StringType.VARCHAR),
            new ResultSetColumn("granted_by_role_type", StringType.VARCHAR)
        );
        final boolean databaseRole = role instanceof DatabaseRole;
        final String kind = databaseRole ? DATABASE_ROLE : "ROLE";
        final String name = databaseRole ? ((DatabaseRole) role).getQualifiedName() : role.getName();
        final List<Row> rows = new ArrayList<>();
        final String owner = role.getOwner();
        if (owner != null && !owner.isEmpty()) {
            rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(role.getCreatedTime()), "OWNERSHIP",
                kind, name, "ROLE", owner, "true", owner, ShowResultHelpers.OWNER_ROLE_TYPE)));
        }
        if (databaseRole) {
            for (final Role holder : catalog.getAllRoles()) {
                addUsage(rows, holder.getDatabaseRoleGrants().get(name), holder.getDatabaseRoleGrantTime(name),
                    holder, kind, name, "ROLE");
            }
            final Database database = catalog.getDatabase(((DatabaseRole) role).getDatabase());
            if (database != null) {
                for (final DatabaseRole holder : database.getDatabaseRoles()) {
                    addUsage(rows, holder.getDatabaseRoleGrants().get(name), holder.getDatabaseRoleGrantTime(name),
                        holder, kind, name, DATABASE_ROLE);
                }
            }
            for (final User user : catalog.getAllUsers()) {
                final String grantor = user.getDatabaseRoleGrants().get(name);
                if (grantor != null) {
                    rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(user.getCreatedTime()),
                        "USAGE", kind, name, "USER", user.getName(), "false", grantor,
                        ShowResultHelpers.OWNER_ROLE_TYPE)));
                }
            }
        } else {
            for (final Role holder : catalog.getAllRoles()) {
                if (holder.getGrantedRoles().contains(name)) {
                    addUsage(rows, holder.getRoleGrantor(name), null, holder, kind, name, "ROLE");
                }
            }
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row left, final Row right) {
                final int byGrantee = String.valueOf(left.getValue(5)).compareTo(String.valueOf(right.getValue(5)));
                return byGrantee != 0 ? byGrantee
                    : String.valueOf(left.getValue(1)).compareTo(String.valueOf(right.getValue(1)));
            }
        });
        return new ResultSet(columns, rows);
    }

    /** One holder's USAGE of a role, as SHOW GRANTS ON the role lists it; nothing when the holder has no grant. */
    private static void addUsage(final List<Row> rows, final String grantor, final Instant granted,
                                 final Role holder, final String kind, final String name, final String holderKind) {
        if (grantor == null) {
            return;
        }
        final Object createdOn = granted != null ? ShowResultHelpers.createdOn(granted)
            : ShowResultHelpers.createdOn(holder.getCreatedTime());
        rows.add(new Row(Arrays.<Object>asList(createdOn, "USAGE", kind, name, holderKind, holder.getName(), "false",
            grantor, ShowResultHelpers.OWNER_ROLE_TYPE)));
    }

    /**
     * SHOW FUTURE GRANTS: every future grant a role or database role holds that the filter admits.
     *
     * @param scopeKind SCHEMA or DATABASE to list one scope's grants, or null for every scope
     * @param scopeName the scope's name, with scopeKind
     * @param grantee the one grantee to list, or null for every role and database role
     */
    public ResultSet futureGrants(final String scopeKind, final String scopeName, final Role grantee) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("privilege", StringType.VARCHAR),
            new ResultSetColumn("grant_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("grant_to", StringType.VARCHAR),
            new ResultSetColumn("grantee_name", StringType.VARCHAR),
            new ResultSetColumn("grant_option", StringType.VARCHAR)
        );
        final List<Role> grantees = new ArrayList<>();
        if (grantee != null) {
            grantees.add(grantee);
        } else {
            grantees.addAll(catalog.getAllRoles());
            for (final Database database : catalog.getAllDatabases()) {
                grantees.addAll(database.getDatabaseRoles());
            }
        }
        final List<Row> rows = new ArrayList<>();
        for (final Role role : grantees) {
            final boolean databaseRole = role instanceof DatabaseRole;
            for (final FutureGrant grant : role.getFutureGrants()) {
                if (scopeKind != null && (!scopeKind.equals(grant.getScopeKind())
                        || !scopeName.equals(grant.getScopeName()))) {
                    continue;
                }
                rows.add(new Row(Arrays.<Object>asList(
                    ShowResultHelpers.createdOn(grant.getCreatedOn()),
                    privilegeText(grant.getPrivilege()),
                    grant.getObjectKind(),
                    grant.getScopeName() + ".<" + grant.getObjectKind() + ">",
                    databaseRole ? DATABASE_ROLE : "ROLE",
                    role.getName(),
                    grant.hasGrantOption() ? "true" : "false")));
            }
        }
        // Ordered by grantee, then by the kind granted on, the scope and the privilege — not in the order the
        // grants were made.
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row a, final Row b) {
                for (final int column : new int[] {5, 2, 3, 1}) {
                    final int order = String.valueOf(a.getValue(column)).compareTo(String.valueOf(b.getValue(column)));
                    if (order != 0) {
                        return order;
                    }
                }
                return 0;
            }
        });
        return new ResultSet(columns, rows);
    }

    /** The rows ordered by the name in one of their cells, as the SHOW listings order them. */
    private static List<Row> sortedByName(final List<Row> rows, final int nameIndex) {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row left, final Row right) {
                return String.valueOf(left.getValue(nameIndex)).compareTo(String.valueOf(right.getValue(nameIndex)));
            }
        });
        return rows;
    }

    /** A privilege recorded in its enumeration spelling, as the listings spell it: words apart. */
    public static String privilegeText(final String privilege) {
        return privilege.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    /**
     * SHOW MANAGED ACCOUNTS: the reader accounts this account manages, in the account's eighteen columns. The
     * columns about a moved or renamed URL answer as they do for an account that never moved: an empty old URL
     * and no moments.
     */
    public ResultSet managedAccounts(final List<ManagedAccount> accounts, final String cloud, final String region) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("account_name", StringType.VARCHAR),
            new ResultSetColumn("cloud", StringType.VARCHAR),
            new ResultSetColumn("region", StringType.VARCHAR),
            new ResultSetColumn("account_locator", StringType.VARCHAR),
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("account_url", StringType.VARCHAR),
            new ResultSetColumn("account_locator_url", StringType.VARCHAR),
            new ResultSetColumn("is_reader", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("region_group", StringType.VARCHAR),
            new ResultSetColumn("old_account_url", StringType.VARCHAR),
            new ResultSetColumn("account_old_url_saved_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("account_old_url_last_used", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("organization_old_url", StringType.VARCHAR),
            new ResultSetColumn("organization_old_url_saved_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("organization_old_url_last_used", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("tenant_type", StringType.VARCHAR),
            new ResultSetColumn("domain_names", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final ManagedAccount account : accounts) {
            final String locator = account.getLocator().toLowerCase(Locale.ROOT);
            rows.add(new Row(Arrays.<Object>asList(account.getName(), cloud, region, account.getLocator(),
                ShowResultHelpers.createdOn(account.getCreatedOn()),
                "https://" + account.getName().toLowerCase(Locale.ROOT) + ".snowflakecomputing.com",
                "https://" + locator + ".snowflakecomputing.com",
                "true", ShowResultHelpers.text(account.getComment()), null,
                ShowResultHelpers.text(null), null, null, ShowResultHelpers.text(null), null, null, null, null)));
        }
        return new ResultSet(columns, sortedByName(rows, 0));
    }
}
