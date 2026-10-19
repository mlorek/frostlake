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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Snowflake-compatible role
 */
public class Role implements Taggable {
    private String name;
    /** What this role holds: the privileges, roles and database roles granted to it, with what was recorded. */
    private final GrantLedger grants = new GrantLedger();
    private LocalDateTime createdTime;
    private String comment;
    /** Privileges granted on the future objects of a kind in a schema or database. */
    private final List<FutureGrant> futureGrants = new ArrayList<>();
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
        // The canonical name the statement resolved: an unquoted name in upper case, a quoted one as written.
        this.name = name;
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

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        grants.grantPrivilege(objectType, objectName, privilege);
    }

    /** Takes back a privilege and everything recorded about its grant: its grantor, its moment and its grant option. */
    public void revokePrivilege(final String objectType, final String objectName, final Privilege privilege) {
        grants.revokePrivilege(objectType, objectName, privilege);
    }

    public boolean hasPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        return grants.hasPrivilege(objectType, objectName, privilege);
    }

    public Set<Privilege> getPrivileges(final String objectType, final String objectName) {
        return grants.getPrivileges(objectType, objectName);
    }

    public Map<String, Set<Privilege>> getAllPrivileges() {
        return grants.getAllPrivileges();
    }

    public void grantRole(final String roleName) {
        grants.grantRole(roleName);
    }

    /** Takes a role back, with the grantor and the moment recorded for its grant. */
    public void revokeRole(final String roleName) {
        grants.revokeRole(roleName);
    }

    public boolean hasRole(final String roleName) {
        return grants.hasRole(roleName);
    }

    /** The roles granted, as a copy. */
    public Set<String> getGrantedRoles() {
        return grants.getGrantedRoles();
    }

    // ── Grantor tracking (who ran the GRANT) — parallel to the privilege/role sets above ──

    /** Grants a privilege, recording the role that granted it; a grant dates from when it was first made. */
    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege, final String grantor) {
        grants.grantPrivilege(objectType, objectName, privilege, grantor);
    }

    /** When a privilege on an object was granted, as SHOW GRANTS reports it; null where no moment was recorded. */
    public Instant getPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege) {
        return grants.getPrivilegeGrantTime(objectType, objectName, privilege);
    }

    /** Puts back when a privilege was granted, as a restored snapshot recorded it. */
    public void setPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                                      final Instant granted) {
        grants.setPrivilegeGrantTime(objectType, objectName, privilege, granted);
    }

    /** The role a privilege was granted by, SYSADMIN where none was recorded. */
    public String getPrivilegeGrantor(final String objectType, final String objectName, final Privilege privilege) {
        return grants.getPrivilegeGrantor(objectType, objectName, privilege);
    }

    /** The role a grant of a privilege on an object was recorded as made by, or null where none was recorded. */
    public String recordedPrivilegeGrantor(final String objectType, final String objectName,
                                           final Privilege privilege) {
        return grants.recordedPrivilegeGrantor(objectType, objectName, privilege);
    }

    /** Grants a role, recording the role that granted it and the statement's own instant, which a replay restores. */
    public void grantRole(final String roleName, final String grantor) {
        grants.grantRole(roleName, grantor);
    }

    /** When a role was granted, or null for a grant with no moment recorded. */
    public Instant getRoleGrantTime(final String roleName) {
        return grants.getRoleGrantTime(roleName);
    }

    /** Puts back the moment a restored grant was made at, or none when the snapshot recorded no moment for it. */
    public void restoreRoleGrantTime(final String roleName, final Instant granted) {
        grants.restoreRoleGrantTime(roleName, granted);
    }

    /** The role a role was granted by, SYSADMIN where none was recorded. */
    public String getRoleGrantor(final String roleName) {
        return grants.getRoleGrantor(roleName);
    }

    public void grantColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        grants.grantColumnPrivilege(objectType, objectName, columnName, privilege);
    }

    public void revokeColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        grants.revokeColumnPrivilege(objectType, objectName, columnName, privilege);
    }

    /** Whether a column is held: a privilege on the whole object reaches every column of it. */
    public boolean hasColumnPrivilege(final String objectType, final String objectName, final String columnName, final Privilege privilege) {
        return grants.hasColumnPrivilege(objectType, objectName, columnName, privilege);
    }

    public Set<String> getColumnsWithPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        return grants.getColumnsWithPrivilege(objectType, objectName, privilege);
    }

    public Map<String, Map<String, Set<Privilege>>> getAllColumnPrivileges() {
        return grants.getAllColumnPrivileges();
    }

    /** Records whether a privilege held on an object carries the grant option: true records it, false takes it back. */
    public void setGrantOption(final String objectType, final String objectName, final Privilege privilege,
                               final boolean holds) {
        grants.setGrantOption(objectType, objectName, privilege, holds);
    }

    /** Whether a privilege held on an object carries the grant option. */
    public boolean hasGrantOption(final String objectType, final String objectName, final Privilege privilege) {
        return grants.hasGrantOption(objectType, objectName, privilege);
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

    /** Grants a database role, by its qualified name. */
    public void grantDatabaseRole(final String qualifiedName, final String grantor) {
        grants.grantDatabaseRole(qualifiedName, grantor);
    }

    /** Takes a database role back. */
    public void revokeDatabaseRole(final String qualifiedName) {
        grants.revokeDatabaseRole(qualifiedName);
    }

    /** The database roles granted, by qualified name, each with the role that granted it. */
    public Map<String, String> getDatabaseRoleGrants() {
        return grants.getDatabaseRoleGrants();
    }

    /** Puts back when a database role was granted, as a restored snapshot recorded it. */
    public void setDatabaseRoleGrantTime(final String qualifiedName, final Instant granted) {
        grants.setDatabaseRoleGrantTime(qualifiedName, granted);
    }

    /** When a database role was granted, or null when it is not held. */
    public Instant getDatabaseRoleGrantTime(final String qualifiedName) {
        return grants.getDatabaseRoleGrantTime(qualifiedName);
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
        grants.copyFrom(source.grants, fromDatabase, toDatabase);
        comment = source.comment;
        owner = source.owner;
        tags.putAll(source.tags);
    }

    /** Applies a change of recorded names to the privileges held on objects. */
    public void remapGrants(final GrantRemap remap) {
        grants.remapGrants(remap);
    }

    /** Takes back the privileges held on one object: all of them, or with {@code ownershipOnly} OWNERSHIP alone. */
    public void purgeGrants(final String objectType, final String objectName, final boolean ownershipOnly) {
        grants.purgeGrants(objectType, objectName, ownershipOnly);
    }

    public void rename(final String newName) {
        // Match the constructor, which upper-cases the name; a verbatim rename would
        // otherwise leave getName() inconsistent with every freshly-created role.
        this.name = newName;
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
