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

package dev.frostlake.metastore.model;


import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Taggable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Snowflake-compatible role
 */
public class Role implements Taggable {
    private String name;
    private final Map<String, Set<Privilege>> objectPrivileges;  // objectType:objectName -> privileges
    private final Map<String, Map<String, Set<Privilege>>> columnPrivileges;  // objectType:objectName -> columnName -> privileges
    private final Set<String> grantedRoles;  // Roles granted to this role (role hierarchy)
    private final Map<String, String> privilegeGrantors = new HashMap<>();  // OBJTYPE:OBJNAME:PRIV -> grantor role
    private final Map<String, Instant> privilegeGrantTimes = new HashMap<>();  // OBJTYPE:OBJNAME:PRIV -> when granted
    private final Map<String, String> roleGrantors = new HashMap<>();        // granted role name -> grantor role
    private final LocalDateTime createdTime;
    private String comment;
    /** OBJTYPE:OBJNAME:PRIV of each privilege held WITH GRANT OPTION. */
    private final Set<String> grantOptions = new HashSet<>();
    /** Privileges granted on the future objects of a kind in a schema or database. */
    private final List<FutureGrant> futureGrants = new ArrayList<>();
    /** Database roles granted to this role, by qualified name, and the role that granted each. */
    private final Map<String, String> databaseRoleGrantors = new LinkedHashMap<>();
    /** When each database role was granted to this role. */
    private final Map<String, Instant> databaseRoleGrantTimes = new HashMap<>();
    private final Map<String, String> tags = new HashMap<>();

    public Role(final String name) {
        this(name, LocalDateTime.ofInstant(StatementClock.instant(), ZoneId.systemDefault()));
    }

    /**
     * A role created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the role's name
     * @param createdTime when it was created, on the host's wall clock
     */
    public Role(final String name, final LocalDateTime createdTime) {
        this.name = name.toUpperCase();
        this.objectPrivileges = new HashMap<>();
        this.columnPrivileges = new HashMap<>();
        this.grantedRoles = new HashSet<>();
        this.createdTime = createdTime;
    }

    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE. */
    private String owner = "SYSADMIN";

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Set<Privilege> granted = objectPrivileges.get(key);
        if (granted == null) {
            granted = new HashSet<>();
            objectPrivileges.put(key, granted);
        }
        granted.add(privilege);
    }

    public void revokePrivilege(final String objectType, final String objectName, final Privilege privilege) {
        grantOptions.remove(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name());
        privilegeGrantTimes.remove(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name());
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        final Set<Privilege> privileges = objectPrivileges.get(key);
        if (privileges != null) {
            privileges.remove(privilege);
            if (privileges.isEmpty()) {
                objectPrivileges.remove(key);
            }
        }
    }

    public boolean hasPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        final Set<Privilege> privileges = objectPrivileges.get(key);
        return privileges != null && privileges.contains(privilege);
    }

    public Set<Privilege> getPrivileges(final String objectType, final String objectName) {
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        return objectPrivileges.getOrDefault(key, new HashSet<>());
    }

    public Map<String, Set<Privilege>> getAllPrivileges() {
        return new HashMap<>(objectPrivileges);
    }

    public void grantRole(final String roleName) {
        grantedRoles.add(roleName.toUpperCase());
    }

    public void revokeRole(final String roleName) {
        grantedRoles.remove(roleName.toUpperCase());
    }

    public boolean hasRole(final String roleName) {
        return grantedRoles.contains(roleName.toUpperCase());
    }

    public Set<String> getGrantedRoles() {
        return new HashSet<>(grantedRoles);
    }

    // ── Grantor tracking (who ran the GRANT) — parallel to the privilege/role sets above ──

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege, final String grantor) {
        grantPrivilege(objectType, objectName, privilege);
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name();
        privilegeGrantors.put(key, grantor);
        // A grant dates from when it was first made; granting it again, or recording a new grantor, keeps that.
        privilegeGrantTimes.putIfAbsent(key, StatementClock.instant());
    }

    /**
     * When a privilege on an object was granted to this role, as SHOW GRANTS reports it.
     *
     * @return the moment of the grant, or null for a grant with no recorded moment
     */
    public Instant getPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege) {
        return privilegeGrantTimes.get(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name());
    }

    /** Puts back when a privilege was granted to this role, as a restored snapshot recorded it. */
    public void setPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                                      final Instant granted) {
        privilegeGrantTimes.put(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name(),
            granted);
    }

    public String getPrivilegeGrantor(final String objectType, final String objectName, final Privilege privilege) {
        return privilegeGrantors.getOrDefault(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name(), "SYSADMIN");
    }

    public void grantRole(final String roleName, final String grantor) {
        grantRole(roleName);
        roleGrantors.put(roleName.toUpperCase(), grantor);
    }

    public String getRoleGrantor(final String roleName) {
        return roleGrantors.getOrDefault(roleName.toUpperCase(), "SYSADMIN");
    }

    public void grantColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Map<String, Set<Privilege>> byColumn = columnPrivileges.get(key);
        if (byColumn == null) {
            byColumn = new HashMap<>();
            columnPrivileges.put(key, byColumn);
        }
        final String upperColumn = columnName.toUpperCase();
        Set<Privilege> granted = byColumn.get(upperColumn);
        if (granted == null) {
            granted = new HashSet<>();
            byColumn.put(upperColumn, granted);
        }
        granted.add(privilege);
    }

    public void revokeColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            final Set<Privilege> privileges = columns.get(columnName.toUpperCase());
            if (privileges != null) {
                privileges.remove(privilege);
                if (privileges.isEmpty()) {
                    columns.remove(columnName.toUpperCase());
                }
                if (columns.isEmpty()) {
                    columnPrivileges.remove(key);
                }
            }
        }
    }

    public boolean hasColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        // First check if there's a table-level privilege (table-level grants access to all columns)
        if (hasPrivilege(objectType, objectName, privilege)) {
            return true;
        }

        // Then check column-level privilege
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            final Set<Privilege> privileges = columns.get(columnName.toUpperCase());
            return privileges != null && privileges.contains(privilege);
        }
        return false;
    }

    public Set<String> getColumnsWithPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final Set<String> result = new HashSet<>();
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            for (final Map.Entry<String, Set<Privilege>> entry : columns.entrySet()) {
                if (entry.getValue().contains(privilege)) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    public Map<String, Map<String, Set<Privilege>>> getAllColumnPrivileges() {
        final Map<String, Map<String, Set<Privilege>>> result = new HashMap<>();
        for (final Map.Entry<String, Map<String, Set<Privilege>>> entry : columnPrivileges.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Records whether a privilege this role holds on an object carries the grant option.
     *
     * @param holds true to record the option, false to take it back and leave the privilege
     */
    public void setGrantOption(final String objectType, final String objectName, final Privilege privilege,
                               final boolean holds) {
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name();
        if (holds) {
            grantOptions.add(key);
        } else {
            grantOptions.remove(key);
        }
    }

    /** Whether a privilege this role holds on an object carries the grant option. */
    public boolean hasGrantOption(final String objectType, final String objectName, final Privilege privilege) {
        return grantOptions.contains(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name());
    }

    /** The future grants this role holds, in the order they were made. */
    public List<FutureGrant> getFutureGrants() {
        return new ArrayList<>(futureGrants);
    }

    /**
     * Records a future grant, or updates the grant option of the same one made before.
     *
     * @return the grant as recorded
     */
    public FutureGrant addFutureGrant(final String objectKind, final String scopeKind, final String scopeName,
                                      final String privilege, final boolean grantOption) {
        return addFutureGrant(objectKind, scopeKind, scopeName, privilege, grantOption, StatementClock.instant());
    }

    /**
     * As {@link #addFutureGrant(String, String, String, String, boolean)}, dated: a restored snapshot brings a
     * future grant back with the moment it was first made.
     */
    public FutureGrant addFutureGrant(final String objectKind, final String scopeKind, final String scopeName,
                                      final String privilege, final boolean grantOption, final Instant createdOn) {
        for (final FutureGrant existing : futureGrants) {
            if (existing.matches(objectKind, scopeKind, scopeName, privilege)) {
                if (grantOption) {
                    existing.setGrantOption(true);
                }
                return existing;
            }
        }
        final FutureGrant grant = new FutureGrant(objectKind, scopeKind, scopeName, privilege, grantOption,
            createdOn);
        futureGrants.add(grant);
        return grant;
    }

    /**
     * Takes back a future grant, or only its grant option.
     *
     * @param optionOnly true to keep the privilege and take back the grant option alone
     */
    public void revokeFutureGrant(final String objectKind, final String scopeKind, final String scopeName,
                                  final String privilege, final boolean optionOnly) {
        for (int i = 0; i < futureGrants.size(); i++) {
            final FutureGrant existing = futureGrants.get(i);
            if (existing.matches(objectKind, scopeKind, scopeName, privilege)) {
                if (optionOnly) {
                    existing.setGrantOption(false);
                } else {
                    futureGrants.remove(i);
                }
                return;
            }
        }
    }

    /** Grants a database role, by its qualified name, to this role. */
    public void grantDatabaseRole(final String qualifiedName, final String grantor) {
        databaseRoleGrantors.put(qualifiedName, grantor);
        databaseRoleGrantTimes.putIfAbsent(qualifiedName, StatementClock.instant());
    }

    /** Takes a database role back from this role. */
    public void revokeDatabaseRole(final String qualifiedName) {
        databaseRoleGrantors.remove(qualifiedName);
        databaseRoleGrantTimes.remove(qualifiedName);
    }

    /** The database roles granted to this role, by qualified name, each with the role that granted it. */
    public Map<String, String> getDatabaseRoleGrants() {
        return new LinkedHashMap<>(databaseRoleGrantors);
    }

    /** Puts back when a database role was granted to this role, as a restored snapshot recorded it. */
    public void setDatabaseRoleGrantTime(final String qualifiedName, final Instant granted) {
        databaseRoleGrantTimes.put(qualifiedName, granted);
    }

    /** When a database role was granted to this role, or null when it is not held. */
    public Instant getDatabaseRoleGrantTime(final String qualifiedName) {
        return databaseRoleGrantTimes.get(qualifiedName);
    }

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName, value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName);
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName);
    }

    @Override
    public Map<String, String> getTagValues() {
        return new HashMap<>(tags);
    }

    public void rename(final String newName) {
        // Match the constructor, which upper-cases the name; a verbatim rename would
        // otherwise leave getName() inconsistent with every freshly-created role.
        this.name = newName.toUpperCase();
    }
}
