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

import dev.frostlake.metastore.Taggable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Snowflake-compatible user
 */
public class User implements Taggable {
    private String name;
    /** Database roles granted to this user, by qualified name, and the role that granted each. */
    private final Map<String, String> databaseRoleGrantors = new LinkedHashMap<>();
    private final Map<String, String> tags = new HashMap<>();
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
    private String loginName;
    private String displayName;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String defaultWarehouse;
    private String defaultNamespace;
    private String defaultSecondaryRoles = "[\"ALL\"]";
    private boolean mustChangePassword;
    private String userType = "PERSON";
    private Instant expiresAt;
    private Instant lockedUntil;
    private Instant mfaBypassUntil;
    private String rsaPublicKey;
    private String rsaPublicKeyFp;
    private Instant rsaPublicKeyLastSetTime;
    private String rsaPublicKey2;
    private String rsaPublicKey2Fp;
    private Instant rsaPublicKey2LastSetTime;

    public User(final String name) {
        this.name = name.toUpperCase();
        // A user created without one takes their own name for both, so the defaults are
        // MATERIALISED here rather than substituted on read. Two behaviours depend on it: UNSET
        // DISPLAY_NAME leaves nothing behind (a read-time fallback could not tell that apart from
        // never having set one), and RENAME moves only the NAME — a renamed user keeps the login
        // and display names they had, defaults included.
        this.displayName = this.name;
        this.loginName = this.name;
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
        final String key = objectType.toUpperCase() + ":" + objectName.toUpperCase();
        Set<Privilege> granted = objectPrivileges.get(key);
        if (granted == null) {
            granted = new HashSet<>();
            objectPrivileges.put(key, granted);
        }
        granted.add(privilege);
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

    /**
     * True when the role reached this user through an explicit {@code GRANT ROLE} (which records its
     * grantor) rather than implicitly at user creation (PUBLIC, granted without a grantor entry).
     * SHOW GRANTS TO USER lists only explicit grants — live Snowflake shows zero rows for a fresh user.
     */
    public boolean hasExplicitRoleGrant(final String roleName) {
        return roleGrantors.containsKey(roleName.toUpperCase());
    }

    public void revokePrivilege(final String objectType, final String objectName, final Privilege privilege) {
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

    /** The name this user logs in with; live falls back to the user's name when it is unset. */
    /** The user's login name: their own name unless one was given, and unchanged by a rename. */
    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(final String loginName) {
        this.loginName = loginName;
    }

    /** The user's display name: their own name unless one was given, and null once unset. */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(final String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getDefaultWarehouse() {
        return defaultWarehouse;
    }

    public void setDefaultWarehouse(final String defaultWarehouse) {
        this.defaultWarehouse = defaultWarehouse;
    }

    /** The database.schema a new session starts in, as SHOW USERS' default_namespace. */
    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(final String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    /** The secondary roles a session activates, which live reports as a JSON array. */
    public String getDefaultSecondaryRoles() {
        return defaultSecondaryRoles;
    }

    public void setDefaultSecondaryRoles(final String defaultSecondaryRoles) {
        this.defaultSecondaryRoles = defaultSecondaryRoles;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(final boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    /** PERSON, SERVICE or LEGACY_SERVICE — live defaults a new user to PERSON. */
    public String getUserType() {
        return userType;
    }

    public void setUserType(final String userType) {
        this.userType = userType;
    }

    /** When the user expires ({@code DAYS_TO_EXPIRY} after it was set), or null for a permanent user. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** When the temporary lock set by {@code MINS_TO_UNLOCK} lifts, or null when none was set. */
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(final Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    /** When the MFA bypass set by {@code MINS_TO_BYPASS_MFA} ends, or null when none was set. */
    public Instant getMfaBypassUntil() {
        return mfaBypassUntil;
    }

    public void setMfaBypassUntil(final Instant mfaBypassUntil) {
        this.mfaBypassUntil = mfaBypassUntil;
    }

    /**
     * The first public key exactly as it was written — armour and line breaks included — the empty text when it
     * was set to {@code ''}, or null.
     */
    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    /** The first public key's {@code SHA256:} fingerprint, or null when it holds no key. */
    public String getRsaPublicKeyFp() {
        return rsaPublicKeyFp;
    }

    /** When a key was last put in the first slot; clearing the slot keeps it. */
    public Instant getRsaPublicKeyLastSetTime() {
        return rsaPublicKeyLastSetTime;
    }

    /**
     * Sets or clears the first public key.
     *
     * @param key the key as written, the empty text, or null
     * @param fingerprint its fingerprint, or null when it holds no key
     * @param setTime when a key was put in the slot, or null to keep the recorded time
     */
    public void setRsaPublicKey(final String key, final String fingerprint, final Instant setTime) {
        this.rsaPublicKey = key;
        this.rsaPublicKeyFp = fingerprint;
        if (setTime != null) {
            this.rsaPublicKeyLastSetTime = setTime;
        }
    }

    /** The second public key as it was written, as {@link #getRsaPublicKey()} describes the first. */
    public String getRsaPublicKey2() {
        return rsaPublicKey2;
    }

    /** The second public key's fingerprint, or null. */
    public String getRsaPublicKey2Fp() {
        return rsaPublicKey2Fp;
    }

    /** When a key was last put in the second slot. */
    public Instant getRsaPublicKey2LastSetTime() {
        return rsaPublicKey2LastSetTime;
    }

    /** Sets or clears the second public key, as {@link #setRsaPublicKey} does the first. */
    public void setRsaPublicKey2(final String key, final String fingerprint, final Instant setTime) {
        this.rsaPublicKey2 = key;
        this.rsaPublicKey2Fp = fingerprint;
        if (setTime != null) {
            this.rsaPublicKey2LastSetTime = setTime;
        }
    }

    /** Whether either slot holds a key: SHOW USERS' has_rsa_public_key and DESCRIBE USER's HAS_KEYPAIR. */
    public boolean hasRsaPublicKey() {
        return rsaPublicKeyFp != null || rsaPublicKey2Fp != null;
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

    /** Grants a database role, by its qualified name, to this user. */
    public void grantDatabaseRole(final String qualifiedName, final String grantor) {
        databaseRoleGrantors.put(qualifiedName, grantor);
    }

    /** Takes a database role back from this user. */
    public void revokeDatabaseRole(final String qualifiedName) {
        databaseRoleGrantors.remove(qualifiedName);
    }

    /** The database roles granted to this user, by qualified name, each with the role that granted it. */
    public Map<String, String> getDatabaseRoleGrants() {
        return new LinkedHashMap<>(databaseRoleGrantors);
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
}
