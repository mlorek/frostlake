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

package dev.frostlake.security;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Security manager for enforcing access control based on granted privileges
 */
public class SecurityManager {
    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);

    private final Catalog catalog;
    private final SessionContext sessionContext;

    /** The view owner whose rights stand in for the session's while a view's own query is planned and run. */
    private final ThreadLocal<String> ownerRights = new ThreadLocal<>();

    public SecurityManager(final Catalog catalog, final SessionContext sessionContext) {
        this.catalog = catalog;
        this.sessionContext = sessionContext;
    }

    /**
     * Check if the current user has permission to perform an operation
     */
    public void checkPermission(final Privilege requiredPrivilege, final String objectType, final String objectName) {
        if (!sessionContext.isSecurityEnabled()) {
            return; // Security checks disabled
        }

        final String currentUser = sessionContext.getCurrentUser();

        // SYSTEM user always has all permissions
        if ("SYSTEM".equals(currentUser)) {
            return;
        }

        // Get all roles for the current user (including role hierarchy) — or, inside a view's own query, the view
        // owner's roles (see runWithOwnerRights).
        final String rightsOwner = ownerRights.get();
        final Set<String> effectiveRoles = rightsOwner != null ? rolesUnder(rightsOwner) : getEffectiveRoles();

        // Administrative roles have broad object authority — like the SYSTEM user but at role
        // granularity. Restriction applies to custom roles, matching Snowflake's built-in admins.
        if (effectiveRoles.contains("ACCOUNTADMIN") || effectiveRoles.contains("SYSADMIN")) {
            return;
        }

        // The object's owner role has full control over the object (ownership confers all privileges).
        final String ownerRole = catalog.getObjectOwnerRole(objectType, objectName);
        if (ownerRole != null && effectiveRoles.contains(ownerRole.toUpperCase(Locale.ROOT))) {
            return;
        }

        // A privilege is recorded under the object's resolved full name (GrantNames), however the statement
        // spelled it, so the name asked about is resolved the same way.
        final String recordedName = GrantNames.of(catalog, objectType, objectName);

        // Check if any of the user's roles, or a database role granted to one of them, has the required privilege
        if (holds(holdersUnder(effectiveRoles), objectType, recordedName, requiredPrivilege)) {
            logger.debug("Permission granted: {} on {}:{}", requiredPrivilege, objectType, objectName);
            return;
        }

        // Permission denied
        final String message = String.format(
            "Permission denied: User '%s' with roles %s does not have %s privilege on %s '%s'",
            currentUser, effectiveRoles, requiredPrivilege, objectType, objectName
        );
        logger.warn(message);
        throw new SecurityException(message);
    }

    /**
     * Typed overload of {@link #checkPermission(Privilege, String, String)} for the securable object kinds
     * modelled by {@link SecurableObjectType}. The string form is retained for the open-ended GRANT object-type
     * space (FUNCTION, PROCEDURE, SEQUENCE, ROLE, …), which reaches beyond these constants.
     */
    public void checkPermission(final Privilege requiredPrivilege, final SecurableObjectType objectType,
                                final String objectName) {
        checkPermission(requiredPrivilege, objectType.getCatalogName(), objectName);
    }

    /**
     * Authorize GRANT / REVOKE of a privilege on an object: the current session must be the SYSTEM
     * user, an administrative role (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN), or the object's owner.
     * This closes the self-escalation hole where any role could grant itself privileges.
     */
    public void checkGrantAuthority(final String objectType, final String objectName) {
        if (!sessionContext.isSecurityEnabled() || "SYSTEM".equals(sessionContext.getCurrentUser())) {
            return;
        }
        // SECURITYADMIN manages grants; otherwise require OWNERSHIP — hasPermission(OWNERSHIP) already
        // covers the ACCOUNTADMIN/SYSADMIN bypass, the owner field, and an explicit OWNERSHIP grant.
        if (getEffectiveRoles().contains("SECURITYADMIN")
                || hasPermission(Privilege.OWNERSHIP, objectType, objectName)) {
            return;
        }
        throw new SecurityException(String.format(
            "Cannot grant privileges on %s '%s': requires OWNERSHIP of the object or an administrative"
            + " role (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN)", objectType, objectName));
    }

    /**
     * Authorize ALTER of an object: the current session must own the object, hold an administrative
     * role, or have been granted ALTER (or OWNERSHIP/ALL) on it. Delegates to {@link #checkPermission}
     * with {@link Privilege#ALTER} — the securityEnabled / SYSTEM-user bypasses are applied there.
     */
    public void checkAlter(final SecurableObjectType objectType, final String objectName) {
        checkPermission(Privilege.ALTER, objectType.getCatalogName(), objectName);
    }

    /**
     * Whether the session may act as an object's owner: whether {@code owner} is the primary role, one of the
     * secondary roles, or a role any of those inherits, however many levels down. No administrative role
     * stands in for it — ACCOUNTADMIN does not own what another role owns unless that role is granted to it.
     * Anything passes while security checks are off. An owner's name always names a role: a dropped role's
     * objects pass to the role that dropped it, and a renamed role's follow its name (see RoleOwnership).
     *
     * @param owner the object's owner role; null when none is recorded, which passes
     */
    public boolean actsAsOwner(final String owner) {
        if (!sessionContext.isSecurityEnabled() || owner == null) {
            return true;
        }
        return getEffectiveRoles().contains(owner.toUpperCase(Locale.ROOT));
    }

    /**
     * Whether the session holds a privilege on an object: one of its roles — the primary role, a role granted to
     * the user, a role any of those inherits, PUBLIC, or a database role granted to one of them — owns the
     * object or was granted the privilege. No administrative role stands in for that: ACCOUNTADMIN reads, writes
     * and alters only what it holds (live-verified). Inside a view's own query the view's owner is asked beside
     * the session, since either's right to a relation lets the view read it. An object with no recorded owner
     * passes, as does anything while security checks are off.
     *
     * @param privilege  the privilege the statement needs
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name in full
     * @param owner      the role that owns the object, or null when none is recorded
     * @return whether the statement may go on
     */
    public boolean holdsPrivilege(final Privilege privilege, final String objectType, final String objectName,
                                  final String owner) {
        if (!sessionContext.isSecurityEnabled() || owner == null) {
            return true;
        }
        final Set<String> roles = askedRoles();
        return roles.contains(owner.toUpperCase(Locale.ROOT)) || holdsExactly(holdersUnder(roles), objectType,
            GrantNames.of(catalog, objectType, objectName), privilege);
    }

    /**
     * Whether the session holds any privilege at all on an object, which is what a statement that only needs to
     * see it asks: its owner, or the holder of whatever grant.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name in full
     * @param owner      the role that owns the object, or null when none is recorded
     * @return whether the statement may go on
     */
    public boolean holdsAnyPrivilege(final String objectType, final String objectName, final String owner) {
        if (!sessionContext.isSecurityEnabled() || owner == null) {
            return true;
        }
        return holdAny(askedRoles(), objectType, objectName, owner);
    }

    /**
     * Whether the rights a statement runs with reach an object, which is what lists it in INFORMATION_SCHEMA:
     * they own it, or hold whatever privilege on it. Inside a stored procedure that runs with owner's rights
     * these are the owner's rights alone - what the caller holds lists nothing there (live-verified). An object
     * with no recorded owner is reached, as is everything while security checks are off.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name in full
     * @param owner      the role that owns the object, or null when none is recorded
     * @return whether the object is one the rights reach
     */
    public boolean reaches(final String objectType, final String objectName, final String owner) {
        if (!sessionContext.isSecurityEnabled() || owner == null) {
            return true;
        }
        return holdAny(reachingRoles(), objectType, objectName, owner);
    }

    /**
     * Whether the rights a statement runs with see an object, which is what SHOW lists, and what DESCRIBE and
     * GET_DDL answer: they reach it (see {@link #reaches}), or hold MANAGE GRANTS, which sees every object of the
     * account. ACCOUNTADMIN and SECURITYADMIN hold it from the start (documented).
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name in full
     * @param owner      the role that owns the object, or null when none is recorded
     * @return whether the object is one the rights see
     */
    public boolean sees(final String objectType, final String objectName, final String owner) {
        if (!sessionContext.isSecurityEnabled() || owner == null) {
            return true;
        }
        final Set<String> roles = reachingRoles();
        return holdAny(roles, objectType, objectName, owner) || managesGrants(roles);
    }

    /**
     * The roles {@link #reaches} asks: the session's, or inside a stored procedure that runs with owner's rights the
     * owner's alone, with PUBLIC beside them.
     */
    private Set<String> reachingRoles() {
        final String executableOwner = sessionContext.getExecutableOwner();
        if (executableOwner == null) {
            return askedRoles();
        }
        final Set<String> roles = rolesUnder(executableOwner.toUpperCase(Locale.ROOT));
        roles.add("PUBLIC");
        return roles;
    }

    /** Whether one of the roles holds MANAGE GRANTS: ACCOUNTADMIN and SECURITYADMIN, or a role it was granted to. */
    private boolean managesGrants(final Set<String> roles) {
        if (roles.contains("ACCOUNTADMIN") || roles.contains("SECURITYADMIN")) {
            return true;
        }
        for (final Role holder : holdersUnder(roles)) {
            if (holder.hasPrivilege("ACCOUNT", "ACCOUNT", Privilege.MANAGE_GRANTS)) {
                return true;
            }
        }
        return false;
    }

    private boolean holdAny(final Set<String> roles, final String objectType, final String objectName,
                            final String owner) {
        if (roles.contains(owner.toUpperCase(Locale.ROOT))) {
            return true;
        }
        final String recorded = GrantNames.of(catalog, objectType, objectName);
        for (final Role holder : holdersUnder(roles)) {
            if (!holder.getPrivileges(objectType, recorded).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The roles a privilege is asked of: the session's, PUBLIC, and inside a view's query or a stored procedure
     * that runs with owner's rights the owner's too.
     */
    private Set<String> askedRoles() {
        final Set<String> roles = getEffectiveRoles();
        roles.add("PUBLIC");
        final String rightsOwner = ownerRights.get();
        if (rightsOwner != null) {
            roles.addAll(rolesUnder(rightsOwner));
        }
        final String executableOwner = sessionContext.getExecutableOwner();
        if (executableOwner != null) {
            roles.addAll(rolesUnder(executableOwner.toUpperCase(Locale.ROOT)));
        }
        return roles;
    }

    /**
     * Whether one of {@code holders} was granted exactly this privilege, or OWNERSHIP. ALL is no privilege of
     * its own here: a GRANT ALL records each privilege it stands for on that kind of object, and what it does
     * not stand for — MODIFY on a table — it does not give.
     */
    private static boolean holdsExactly(final List<Role> holders, final String objectType, final String recordedName,
                                        final Privilege privilege) {
        for (final Role holder : holders) {
            if (holder.hasPrivilege(objectType, recordedName, privilege)
                    || holder.hasPrivilege(objectType, recordedName, Privilege.OWNERSHIP)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the session may create an object in a container. A CREATE is authorized by the primary role alone
     * — a secondary role authorizes none — through the roles that one inherits, PUBLIC, which every role holds,
     * and the database roles granted to any of them: one of them owns the container, or holds the CREATE
     * privilege or ALL on it. No administrative role stands in for that, and no user is exempt. Inside a stored
     * procedure that runs with owner's rights the procedure's owner is asked in the primary role's place, and
     * inside a view's own query the view's owner. A container with no recorded owner, or one whose owner
     * is no longer a role, passes, as does anything while security checks are off.
     *
     * @param createPrivilege the CREATE privilege the new object needs
     * @param containerType   the container's kind as privileges record it, {@code SCHEMA} or {@code DATABASE}
     * @param containerName   the container's name in full
     * @param owner           the role that owns the container, or null when none is recorded
     */
    public boolean mayCreateIn(final Privilege createPrivilege, final String containerType, final String containerName,
                               final String owner) {
        if (!sessionContext.isSecurityEnabled() || owner == null) {
            return true;
        }
        final String ownerRole = owner.toUpperCase(Locale.ROOT);
        final String rightsOwner = ownerRights.get();
        final String acting = rightsOwner != null ? rightsOwner : sessionContext.getCreatingRole();
        if (acting == null || !catalog.roleExists(ownerRole)) {
            return true;
        }
        final Set<String> roles = rolesUnder(acting.toUpperCase(Locale.ROOT));
        roles.add("PUBLIC");
        return roles.contains(ownerRole) || holds(holdersUnder(roles), containerType,
            GrantNames.of(catalog, containerType, containerName), createPrivilege);
    }

    /**
     * Whether the session reaches an object through one of the roles a privilege is asked of owning it (see
     * {@link #holdsPrivilege}).
     *
     * @param owner the role that owns the object, or null when none is recorded
     * @return whether an asked role owns it
     */
    public boolean ownedByAskedRole(final String owner) {
        return owner == null || !sessionContext.isSecurityEnabled() || askedRoles().contains(owner.toUpperCase(Locale.ROOT));
    }

    /**
     * The first container of a schema's object the session cannot use: a role uses an object of a schema only with
     * USAGE on the database and on the schema that contain it, whatever it holds on the object itself (documented).
     * Owning the container, ALL and OWNERSHIP grants count as USAGE.
     *
     * @param databaseName the object's database
     * @param schemaName   the object's schema
     * @return "DATABASE" or "SCHEMA" for the first one missed, or null when both are used
     */
    public String missedContainer(final String databaseName, final String schemaName) {
        if (!sessionContext.isSecurityEnabled()) {
            return null;
        }
        final Set<String> roles = askedRoles();
        if (!uses(roles, "DATABASE", databaseName)) {
            return "DATABASE";
        }
        return uses(roles, "SCHEMA", databaseName + "." + schemaName) ? null : "SCHEMA";
    }

    private boolean uses(final Set<String> roles, final String containerType, final String containerName) {
        final String owner = catalog.getObjectOwnerRole(containerType, containerName);
        if (owner == null || roles.contains(owner.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return holds(holdersUnder(roles), containerType, GrantNames.of(catalog, containerType, containerName),
            Privilege.USAGE);
    }

    /** Whether one of {@code holders} holds a privilege on an object: the privilege itself, ALL or OWNERSHIP. */
    private static boolean holds(final List<Role> holders, final String objectType, final String recordedName,
                                 final Privilege privilege) {
        for (final Role holder : holders) {
            if (holder.hasPrivilege(objectType, recordedName, privilege)
                    || holder.hasPrivilege(objectType, recordedName, Privilege.ALL)
                    || holder.hasPrivilege(objectType, recordedName, Privilege.OWNERSHIP)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The roles a privilege is held through for a set of account roles: each of them that exists, and every
     * database role granted to one of them or to another database role so held, however many levels down. A
     * database role of a database that is gone holds nothing.
     */
    private List<Role> holdersUnder(final Set<String> accountRoles) {
        final List<Role> holders = new ArrayList<>();
        final List<String> pending = new ArrayList<>();
        for (final String roleName : accountRoles) {
            try {
                final Role role = catalog.getRole(roleName);
                holders.add(role);
                pending.addAll(role.getDatabaseRoleGrants().keySet());
            } catch (final RuntimeException unresolvable) {
                logger.debug("Role {} not found while checking permissions", roleName);
            }
        }
        final Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            final String qualified = pending.remove(pending.size() - 1);
            final int dot = qualified.lastIndexOf('.');
            if (dot < 0 || !seen.add(qualified)) {
                continue;
            }
            final DatabaseRole held;
            try {
                held = catalog.getDatabase(qualified.substring(0, dot)).getDatabaseRole(qualified.substring(dot + 1));
            } catch (final RuntimeException dropped) {
                continue;
            }
            if (held != null) {
                holders.add(held);
                pending.addAll(held.getDatabaseRoleGrants().keySet());
            }
        }
        return holders;
    }

    /**
     * Check if current user has permission (returns boolean instead of throwing exception)
     */
    public boolean hasPermission(final Privilege requiredPrivilege, final String objectType, final String objectName) {
        try {
            checkPermission(requiredPrivilege, objectType, objectName);
            return true;
        } catch (final SecurityException e) {
            return false;
        }
    }

    /**
     * Enters the rights a view's own query runs with: the view owner's, not the querying role's. Snowflake checks a
     * view's underlying objects against the role that owns the view, so a role granted SELECT on the view reads it
     * without any privilege on the tables beneath. The session itself is untouched: CURRENT_ROLE() still answers
     * the querying role. An owner that is no role here leaves the session's rights in force.
     *
     * @param ownerRole the view's owner
     * @return the rights in force before, for {@link #leaveOwnerRights}
     */
    public String enterOwnerRights(final String ownerRole) {
        final String previous = ownerRights.get();
        if (ownerRole != null && catalog.roleExists(ownerRole.toUpperCase(Locale.ROOT))) {
            ownerRights.set(ownerRole.toUpperCase(Locale.ROOT));
        }
        return previous;
    }

    /**
     * Returns to the rights in force before {@link #enterOwnerRights}.
     *
     * @param previous what {@link #enterOwnerRights} answered
     */
    public void leaveOwnerRights(final String previous) {
        if (previous == null) {
            ownerRights.remove();
        } else {
            ownerRights.set(previous);
        }
    }

    /** A role and every role granted to it, however many levels down. */
    private Set<String> rolesUnder(final String roleName) {
        final Set<String> roles = new HashSet<>();
        final List<String> pending = new ArrayList<>();
        pending.add(roleName);
        while (!pending.isEmpty()) {
            final String next = pending.remove(pending.size() - 1);
            if (!roles.add(next)) {
                continue;
            }
            try {
                pending.addAll(catalog.getRole(next).getGrantedRoles());
            } catch (final RuntimeException unresolvable) {
                logger.debug("Role {} not found while traversing role hierarchy", next);
            }
        }
        return roles;
    }

    /**
     * Get all effective roles for the current user, including role hierarchy
     */
    private Set<String> getEffectiveRoles() {
        final Set<String> effectiveRoles = new HashSet<>();
        final String currentUser = sessionContext.getCurrentUser();

        // Add all active roles from session
        effectiveRoles.addAll(sessionContext.getActiveRoles());

        // Add roles granted to the user
        try {
            final User user = catalog.getUser(currentUser);
            effectiveRoles.addAll(user.getGrantedRoles());
        } catch (final RuntimeException e) {
            logger.debug("User {} not found while getting effective roles", currentUser);
        }

        // Add roles from role hierarchy (roles granted to roles)
        final Set<String> rolesToCheck = new HashSet<>(effectiveRoles);
        final Set<String> checkedRoles = new HashSet<>();

        while (!rolesToCheck.isEmpty()) {
            final String roleName = rolesToCheck.iterator().next();
            rolesToCheck.remove(roleName);

            if (checkedRoles.contains(roleName)) {
                continue; // Already processed
            }
            checkedRoles.add(roleName);

            try {
                final Role role = catalog.getRole(roleName);
                final Set<String> grantedRoles = role.getGrantedRoles();
                for (final String grantedRole : grantedRoles) {
                    if (!checkedRoles.contains(grantedRole)) {
                        effectiveRoles.add(grantedRole);
                        rolesToCheck.add(grantedRole);
                    }
                }
            } catch (final RuntimeException e) {
                logger.debug("Role {} not found while traversing role hierarchy", roleName);
            }
        }

        return effectiveRoles;
    }

    /**
     * Get the session context
     */
    public SessionContext getSessionContext() {
        return sessionContext;
    }
}
