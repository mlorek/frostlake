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
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Security manager for enforcing access control based on granted privileges
 */
public class SecurityManager {
    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);

    private final Catalog catalog;
    private final SessionContext sessionContext;

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

        String currentUser = sessionContext.getCurrentUser();

        // SYSTEM user always has all permissions
        if ("SYSTEM".equals(currentUser)) {
            return;
        }

        // Get all roles for the current user (including role hierarchy)
        Set<String> effectiveRoles = getEffectiveRoles();

        // Administrative roles have broad object authority — like the SYSTEM user but at role
        // granularity. Restriction applies to custom roles, matching Snowflake's built-in admins.
        if (effectiveRoles.contains("ACCOUNTADMIN") || effectiveRoles.contains("SYSADMIN")) {
            return;
        }

        // The object's owner role has full control over the object (ownership confers all privileges).
        final String ownerRole = catalog.getObjectOwnerRole(objectType, objectName);
        if (ownerRole != null && effectiveRoles.contains(ownerRole.toUpperCase())) {
            return;
        }

        // Check if any of the user's roles has the required privilege
        for (final String roleName : effectiveRoles) {
            try {
                Role role = catalog.getRole(roleName);

                // Check for specific privilege
                if (role.hasPrivilege(objectType, objectName, requiredPrivilege)) {
                    logger.debug("Permission granted: {} on {}:{} via role {}",
                                requiredPrivilege, objectType, objectName, roleName);
                    return;
                }

                // Check for ALL privilege
                if (role.hasPrivilege(objectType, objectName, Privilege.ALL)) {
                    logger.debug("Permission granted: ALL (includes {}) on {}:{} via role {}",
                                requiredPrivilege, objectType, objectName, roleName);
                    return;
                }

                // Check for OWNERSHIP privilege (ownership grants all privileges)
                if (role.hasPrivilege(objectType, objectName, Privilege.OWNERSHIP)) {
                    logger.debug("Permission granted: OWNERSHIP (includes {}) on {}:{} via role {}",
                                requiredPrivilege, objectType, objectName, roleName);
                    return;
                }

            } catch (final RuntimeException e) {
                // Role doesn't exist, skip it
                logger.debug("Role {} not found while checking permissions", roleName);
            }
        }

        // Permission denied
        String message = String.format(
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
     * Get all effective roles for the current user, including role hierarchy
     */
    private Set<String> getEffectiveRoles() {
        Set<String> effectiveRoles = new HashSet<>();
        String currentUser = sessionContext.getCurrentUser();

        // Add all active roles from session
        effectiveRoles.addAll(sessionContext.getActiveRoles());

        // Add roles granted to the user
        try {
            User user = catalog.getUser(currentUser);
            effectiveRoles.addAll(user.getGrantedRoles());
        } catch (final RuntimeException e) {
            logger.debug("User {} not found while getting effective roles", currentUser);
        }

        // Add roles from role hierarchy (roles granted to roles)
        Set<String> rolesToCheck = new HashSet<>(effectiveRoles);
        Set<String> checkedRoles = new HashSet<>();

        while (!rolesToCheck.isEmpty()) {
            String roleName = rolesToCheck.iterator().next();
            rolesToCheck.remove(roleName);

            if (checkedRoles.contains(roleName)) {
                continue; // Already processed
            }
            checkedRoles.add(roleName);

            try {
                Role role = catalog.getRole(roleName);
                Set<String> grantedRoles = role.getGrantedRoles();
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
