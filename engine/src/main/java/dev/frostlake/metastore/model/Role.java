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
import java.util.Locale;
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
    private LocalDateTime createdTime;
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
        this.name = name.toUpperCase(Locale.ROOT);
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

    /**
     * Put back the time a restored role was created at.
     *
     * @param created the recorded creation time, or null to keep the one it has
     */
    public void restoreCreatedTime(final LocalDateTime created) {
        if (created != null) {
            this.createdTime = created;
        }
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    /**
     * The key a grant on an object is kept under: the object's kind, then its name exactly as it is recorded. A
     * name is one object's alone, so a quoted {@code "t"} and an unquoted {@code T} are two objects with two sets
     * of grants (live-verified); the callers hand in canonical names.
     */
    private static String key(final String objectType, final String objectName) {
        return objectType.toUpperCase(Locale.ROOT) + ":" + objectName;
    }

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final String key = key(objectType, objectName);
        Set<Privilege> granted = objectPrivileges.get(key);
        if (granted == null) {
            granted = new HashSet<>();
            objectPrivileges.put(key, granted);
        }
        granted.add(privilege);
    }

    public void revokePrivilege(final String objectType, final String objectName, final Privilege privilege) {
        grantOptions.remove(key(objectType, objectName) + ":" + privilege.name());
        privilegeGrantTimes.remove(key(objectType, objectName) + ":" + privilege.name());
        final String key = key(objectType, objectName);
        final Set<Privilege> privileges = objectPrivileges.get(key);
        if (privileges != null) {
            privileges.remove(privilege);
            if (privileges.isEmpty()) {
                objectPrivileges.remove(key);
            }
        }
    }

    public boolean hasPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final String key = key(objectType, objectName);
        final Set<Privilege> privileges = objectPrivileges.get(key);
        return privileges != null && privileges.contains(privilege);
    }

    public Set<Privilege> getPrivileges(final String objectType, final String objectName) {
        final String key = key(objectType, objectName);
        return objectPrivileges.getOrDefault(key, new HashSet<>());
    }

    public Map<String, Set<Privilege>> getAllPrivileges() {
        return new HashMap<>(objectPrivileges);
    }

    public void grantRole(final String roleName) {
        grantedRoles.add(roleName.toUpperCase(Locale.ROOT));
    }

    public void revokeRole(final String roleName) {
        grantedRoles.remove(roleName.toUpperCase(Locale.ROOT));
        roleGrantTimes.remove(roleName.toUpperCase(Locale.ROOT));
    }

    public boolean hasRole(final String roleName) {
        return grantedRoles.contains(roleName.toUpperCase(Locale.ROOT));
    }

    public Set<String> getGrantedRoles() {
        return new HashSet<>(grantedRoles);
    }

    // ── Grantor tracking (who ran the GRANT) — parallel to the privilege/role sets above ──

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege, final String grantor) {
        grantPrivilege(objectType, objectName, privilege);
        final String key = key(objectType, objectName) + ":" + privilege.name();
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
        return privilegeGrantTimes.get(key(objectType, objectName) + ":" + privilege.name());
    }

    /** Puts back when a privilege was granted to this role, as a restored snapshot recorded it. */
    public void setPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                                      final Instant granted) {
        privilegeGrantTimes.put(key(objectType, objectName) + ":" + privilege.name(),
            granted);
    }

    public String getPrivilegeGrantor(final String objectType, final String objectName, final Privilege privilege) {
        return privilegeGrantors.getOrDefault(key(objectType, objectName) + ":" + privilege.name(), "SYSADMIN");
    }

    public void grantRole(final String roleName, final String grantor) {
        grantRole(roleName);
        roleGrantors.put(roleName.toUpperCase(Locale.ROOT), grantor);
        // The grant is stamped with the statement's own instant, which a replay restores.
        roleGrantTimes.put(roleName.toUpperCase(Locale.ROOT), StatementClock.instant());
    }

    /** When each role in grantedRoles was granted, by role name; none for a grant made with no moment recorded. */
    private final Map<String, Instant> roleGrantTimes = new HashMap<>();

    /**
     * When a role was granted to this one.
     *
     * @param roleName the granted role
     * @return the moment, or null for a grant with none recorded
     */
    public Instant getRoleGrantTime(final String roleName) {
        return roleGrantTimes.get(roleName.toUpperCase(Locale.ROOT));
    }

    /**
     * Put back the moment a restored grant was made at.
     *
     * @param roleName the granted role
     * @param granted  when it was granted, or null when the snapshot recorded no moment for it
     */
    public void restoreRoleGrantTime(final String roleName, final Instant granted) {
        if (granted != null) {
            roleGrantTimes.put(roleName.toUpperCase(Locale.ROOT), granted);
        } else {
            roleGrantTimes.remove(roleName.toUpperCase(Locale.ROOT));
        }
    }

    public String getRoleGrantor(final String roleName) {
        return roleGrantors.getOrDefault(roleName.toUpperCase(Locale.ROOT), "SYSADMIN");
    }

    public void grantColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        final String key = key(objectType, objectName);
        Map<String, Set<Privilege>> byColumn = columnPrivileges.get(key);
        if (byColumn == null) {
            byColumn = new HashMap<>();
            columnPrivileges.put(key, byColumn);
        }
        final String upperColumn = columnName.toUpperCase(Locale.ROOT);
        Set<Privilege> granted = byColumn.get(upperColumn);
        if (granted == null) {
            granted = new HashSet<>();
            byColumn.put(upperColumn, granted);
        }
        granted.add(privilege);
    }

    public void revokeColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        final String key = key(objectType, objectName);
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            final Set<Privilege> privileges = columns.get(columnName.toUpperCase(Locale.ROOT));
            if (privileges != null) {
                privileges.remove(privilege);
                if (privileges.isEmpty()) {
                    columns.remove(columnName.toUpperCase(Locale.ROOT));
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
        final String key = key(objectType, objectName);
        final Map<String, Set<Privilege>> columns = columnPrivileges.get(key);
        if (columns != null) {
            final Set<Privilege> privileges = columns.get(columnName.toUpperCase(Locale.ROOT));
            return privileges != null && privileges.contains(privilege);
        }
        return false;
    }

    public Set<String> getColumnsWithPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        final Set<String> result = new HashSet<>();
        final String key = key(objectType, objectName);
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
        final String key = key(objectType, objectName) + ":" + privilege.name();
        if (holds) {
            grantOptions.add(key);
        } else {
            grantOptions.remove(key);
        }
    }

    /** Whether a privilege this role holds on an object carries the grant option. */
    public boolean hasGrantOption(final String objectType, final String objectName, final Privilege privilege) {
        return grantOptions.contains(key(objectType, objectName) + ":" + privilege.name());
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

    /**
     * Gives this role what another holds, with the names under one database recorded under another's: how a database
     * clone's database roles start (documented: a database role is cloned with its database). The privileges keep
     * their grantors, grant times and grant options; a database role granted to the source from its own database is
     * granted to this one from the clone. The comment, owner and tags come along. Future grants are not copied.
     *
     * @param source       the role to copy
     * @param fromDatabase the source's database, canonical
     * @param toDatabase   the clone's database, canonical
     */
    public void copyGrantsFrom(final Role source, final String fromDatabase, final String toDatabase) {
        final Set<String> kinds = new HashSet<>();
        for (final Map.Entry<String, Set<Privilege>> held : source.objectPrivileges.entrySet()) {
            objectPrivileges.put(held.getKey(), new HashSet<>(held.getValue()));
            kinds.add(held.getKey().substring(0, held.getKey().indexOf(':')));
        }
        for (final Map.Entry<String, Map<String, Set<Privilege>>> held : source.columnPrivileges.entrySet()) {
            final Map<String, Set<Privilege>> columns = new HashMap<>();
            for (final Map.Entry<String, Set<Privilege>> column : held.getValue().entrySet()) {
                columns.put(column.getKey(), new HashSet<>(column.getValue()));
            }
            columnPrivileges.put(held.getKey(), columns);
            kinds.add(held.getKey().substring(0, held.getKey().indexOf(':')));
        }
        privilegeGrantors.putAll(source.privilegeGrantors);
        privilegeGrantTimes.putAll(source.privilegeGrantTimes);
        grantOptions.addAll(source.grantOptions);
        grantedRoles.addAll(source.grantedRoles);
        roleGrantors.putAll(source.roleGrantors);
        roleGrantTimes.putAll(source.roleGrantTimes);
        remapGrants(new GrantRemap(kinds, new String[] {fromDatabase}, new String[] {toDatabase}, false, false,
            GrantRemap.ALL));
        final String prefix = fromDatabase + ".";
        for (final Map.Entry<String, String> granted : source.databaseRoleGrantors.entrySet()) {
            final String name = granted.getKey().startsWith(prefix)
                ? toDatabase + "." + granted.getKey().substring(prefix.length()) : granted.getKey();
            databaseRoleGrantors.put(name, granted.getValue());
            final Instant when = source.databaseRoleGrantTimes.get(granted.getKey());
            if (when != null) {
                databaseRoleGrantTimes.put(name, when);
            }
        }
        comment = source.comment;
        owner = source.owner;
        tags.putAll(source.tags);
    }

    /**
     * Applies a change of recorded names to the privileges this role holds on objects.
     *
     * @param remap the change
     */
    public void remapGrants(final GrantRemap remap) {
        GrantKeys.remap(objectPrivileges, columnPrivileges, privilegeGrantors, privilegeGrantTimes, grantOptions, remap);
    }

    /**
     * Takes back the privileges this role holds on one object.
     *
     * @param objectType    the kind the privileges are recorded under
     * @param objectName    the name they are recorded under
     * @param ownershipOnly true to take back only OWNERSHIP
     */
    public void purgeGrants(final String objectType, final String objectName, final boolean ownershipOnly) {
        GrantKeys.purge(objectPrivileges, columnPrivileges, privilegeGrantors, privilegeGrantTimes, grantOptions,
            key(objectType, objectName), ownershipOnly);
    }

    public void rename(final String newName) {
        // Match the constructor, which upper-cases the name; a verbatim rename would
        // otherwise leave getName() inconsistent with every freshly-created role.
        this.name = newName.toUpperCase(Locale.ROOT);
    }

    /**
     * Names the role exactly as given, without the folding an account role's name takes: for a kind of role that
     * keeps a quoted name's case.
     *
     * @param exactName the canonical name
     */
    protected final void nameExactly(final String exactName) {
        this.name = exactName;
    }
}
