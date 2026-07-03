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


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Snowflake-compatible user
 */
public class User {
    private String name;
    private String password;
    private String defaultRole;
    private final Set<String> grantedRoles;
    private final Map<String, Set<Privilege>> objectPrivileges;
    private final Map<String, Map<String, Set<Privilege>>> columnPrivileges;
    private final Map<String, String> privilegeGrantors = new HashMap<>();  // OBJTYPE:OBJNAME:PRIV -> grantor role
    private final Map<String, String> roleGrantors = new HashMap<>();        // granted role name -> grantor role
    private final LocalDateTime createdTime;
    private boolean enabled;
    private String comment;

    public User(final String name) {
        this.name = name.toUpperCase();
        this.grantedRoles = new HashSet<>();
        this.objectPrivileges = new HashMap<>();
        this.columnPrivileges = new HashMap<>();
        this.createdTime = LocalDateTime.now();
        this.enabled = true;
    }

    public User(final String name, final String password, final String defaultRole) {
        this(name);
        this.password = password;
        // Normalise to upper-case to match how roles are stored (CREATE and ALTER both land here).
        this.defaultRole = defaultRole == null ? null : defaultRole.toUpperCase();
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

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(final String defaultRole) {
        // Normalise to upper-case to match how roles are stored (CREATE and ALTER both land here).
        this.defaultRole = defaultRole == null ? null : defaultRole.toUpperCase();
    }

    public Set<String> getGrantedRoles() {
        return grantedRoles;
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

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        objectPrivileges.computeIfAbsent(key, (final var k) -> new HashSet<>()).add(privilege);
    }

    // ── Grantor tracking (who ran the GRANT) — parallel to the privilege/role sets ──

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege, final String grantor) {
        grantPrivilege(objectType, objectName, privilege);
        privilegeGrantors.put(objectType.toUpperCase() + ":" + objectName.toUpperCase() + ":" + privilege.name(), grantor);
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

    public void revokePrivilege(final String objectType, final String objectName, final Privilege privilege) {
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Set<Privilege> privileges = objectPrivileges.get(key);
        if (privileges != null) {
            privileges.remove(privilege);
            if (privileges.isEmpty()) {
                objectPrivileges.remove(key);
            }
        }
    }

    public boolean hasPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Set<Privilege> privileges = objectPrivileges.get(key);
        return privileges != null && privileges.contains(privilege);
    }

    public Set<Privilege> getPrivileges(final String objectType, final String objectName) {
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        return objectPrivileges.getOrDefault(key, new HashSet<>());
    }

    public Map<String, Set<Privilege>> getAllPrivileges() {
        return new HashMap<>(objectPrivileges);
    }

    public void grantColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        columnPrivileges.computeIfAbsent(key, (final var k) -> new HashMap<>())
            .computeIfAbsent(columnName.toUpperCase(), (final var k) -> new HashSet<>())
            .add(privilege);
    }

    public void revokeColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            Set<Privilege> privileges = columns.get(columnName.toUpperCase());
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
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            Set<Privilege> privileges = columns.get(columnName.toUpperCase());
            return privileges != null && privileges.contains(privilege);
        }
        return false;
    }

    public Set<String> getColumnsWithPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        Set<String> result = new HashSet<>();
        String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
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
        Map<String, Map<String, Set<Privilege>>> result = new HashMap<>();
        for (final Map.Entry<String, Map<String, Set<Privilege>>> entry : columnPrivileges.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public void rename(final String newName) {
        // Match the constructor, which upper-cases the name; a verbatim rename would
        // otherwise leave getName() inconsistent with every freshly-created user.
        this.name = newName.toUpperCase();
    }
}
