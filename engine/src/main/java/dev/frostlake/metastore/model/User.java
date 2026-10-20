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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Snowflake-compatible user
 */
public class User implements Taggable {
    private String name;
    private final Map<String, String> tags = new HashMap<>();
    private String password;
    private String defaultRole;
    /** What this user holds: the privileges, roles and database roles granted to it, with what was recorded. */
    private final GrantLedger grants = new GrantLedger();
    private LocalDateTime createdTime;
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

    /** The session parameters set on the user, which the sessions the user starts run with. */
    private final ObjectParameters parameters = new ObjectParameters();

    /** The session parameters set on the user (its sessions inherit them). */
    public ObjectParameters getParameters() {
        return parameters;
    }

    public User(final String name) {
        // The canonical name a statement resolved: an unquoted name upper-cased, a quoted one verbatim.
        this.name = name;
        // A user created without one takes their own name for both, so the defaults are
        // MATERIALISED here rather than substituted on read. Two behaviours depend on it: UNSET
        // DISPLAY_NAME leaves nothing behind (a read-time fallback could not tell that apart from
        // never having set one), and RENAME moves only the NAME — a renamed user keeps the login
        // and display names they had, defaults included.
        this.displayName = this.name;
        this.loginName = this.name;
        this.createdTime = StatementClock.hostLocal();
        this.enabled = true;
    }

    public User(final String name, final String password, final String defaultRole) {
        this(name);
        this.password = password;
        // Normalise to upper-case to match how roles are stored (CREATE and ALTER both land here).
        this.defaultRole = defaultRole == null ? null : defaultRole.toUpperCase(Locale.ROOT);
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
        this.defaultRole = defaultRole == null ? null : defaultRole.toUpperCase(Locale.ROOT);
    }

    /** The roles granted, as a copy. */
    public Set<String> getGrantedRoles() {
        return grants.getGrantedRoles();
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

    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege) {
        grants.grantPrivilege(objectType, objectName, privilege);
    }

    // ── Grantor tracking (who ran the GRANT) — parallel to the privilege/role sets ──

    /** Grants a privilege, recording the role that granted it; a grant dates from when it was first made. */
    public void grantPrivilege(final String objectType, final String objectName, final Privilege privilege, final String grantor) {
        grants.grantPrivilege(objectType, objectName, privilege, grantor);
    }

    /** The role a privilege was granted by, SYSADMIN where none was recorded. */
    public String getPrivilegeGrantor(final String objectType, final String objectName, final Privilege privilege) {
        return grants.getPrivilegeGrantor(objectType, objectName, privilege);
    }

    /** Puts back when a privilege was granted, as a restored snapshot recorded it. */
    public void setPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege,
                                      final Instant granted) {
        grants.setPrivilegeGrantTime(objectType, objectName, privilege, granted);
    }

    /** When a privilege on an object was granted, as SHOW GRANTS reports it; null where no moment was recorded. */
    public Instant getPrivilegeGrantTime(final String objectType, final String objectName, final Privilege privilege) {
        return grants.getPrivilegeGrantTime(objectType, objectName, privilege);
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

    /**
     * True when the role reached this user through an explicit {@code GRANT ROLE} (which records its
     * grantor) rather than implicitly at user creation (PUBLIC, granted without a grantor entry).
     * SHOW GRANTS TO USER lists only explicit grants — live Snowflake shows zero rows for a fresh user.
     */
    public boolean hasExplicitRoleGrant(final String roleName) {
        return grants.hasExplicitRoleGrant(roleName);
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

    /** Applies a change of recorded names to the privileges held on objects. */
    public void remapGrants(final GrantRemap remap) {
        grants.remapGrants(remap);
    }

    /** Takes back the privileges held on one object: all of them, or with {@code ownershipOnly} OWNERSHIP alone. */
    public void purgeGrants(final String objectType, final String objectName, final boolean ownershipOnly) {
        grants.purgeGrants(objectType, objectName, ownershipOnly);
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

    /** Whether a statement wrote the user's DEFAULT_SECONDARY_ROLES, which a session of the user then starts from. */
    private boolean defaultSecondaryRolesWritten;

    /**
     * Records the secondary roles a statement writes for the user's sessions to start from.
     *
     * @param defaultSecondaryRoles the roles as the JSON array live reports
     */
    public void writeDefaultSecondaryRoles(final String defaultSecondaryRoles) {
        this.defaultSecondaryRoles = defaultSecondaryRoles;
        this.defaultSecondaryRolesWritten = true;
    }

    /**
     * Whether a statement wrote the user's DEFAULT_SECONDARY_ROLES; the account's default it starts with is not
     * applied to a session.
     *
     * @return whether one did
     */
    public boolean isDefaultSecondaryRolesWritten() {
        return defaultSecondaryRolesWritten;
    }

    /**
     * Restores whether a statement wrote the user's DEFAULT_SECONDARY_ROLES.
     *
     * @param written whether one did
     */
    public void restoreDefaultSecondaryRolesWritten(final boolean written) {
        this.defaultSecondaryRolesWritten = written;
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

    /**
     * Put back the time a restored user was created at.
     *
     * @param created the recorded creation time, or null to keep the one it has
     */
    public void restoreCreatedTime(final LocalDateTime created) {
        if (created != null) {
            this.createdTime = created;
        }
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
        // The canonical name, as the constructor takes it.
        this.name = newName;
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

    /** When a database role was granted, or null when it is not held. */
    public Instant getDatabaseRoleGrantTime(final String qualifiedName) {
        return grants.getDatabaseRoleGrantTime(qualifiedName);
    }

    /** Puts back when a database role was granted, as a restored snapshot recorded it. */
    public void setDatabaseRoleGrantTime(final String qualifiedName, final Instant granted) {
        grants.setDatabaseRoleGrantTime(qualifiedName, granted);
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
