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
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.CallerGrant;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
     * Authorize GRANT / REVOKE of a privilege on an object: the current session must be the SYSTEM user, hold an
     * administrative role (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN) or MANAGE GRANTS, or own the object. This closes
     * the self-escalation hole where any role could grant itself privileges.
     */
    public void checkGrantAuthority(final String objectType, final String objectName) {
        checkGrantAuthority(objectType, objectName, catalog.getObjectOwnerRole(objectType, objectName),
            Collections.<String>emptyList());
    }

    /**
     * Authorize GRANT / REVOKE of privileges on one object. Beside the roles that decide every grant (see
     * {@link #checkManageGrants}) the object's owner decides, and a GRANT may pass on the privileges the session
     * holds WITH GRANT OPTION. In a schema WITH MANAGED ACCESS neither of those decides: the schema's owner does.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name, as privileges record it or as the statement wrote it
     * @param owner      the role that owns the object, or null when none is recorded
     * @param privileges the privileges a GRANT passes on, each of which a grant option may allow; empty for none
     */
    public void checkGrantAuthority(final String objectType, final String objectName, final String owner,
                                    final List<String> privileges) {
        if (decidesGrants()) {
            return;
        }
        final Schema managed = GrantNames.managedSchema(catalog, objectType, objectName);
        if (managed != null) {
            final String schemaName = QualifiedName.join(managed.getDatabaseName(), managed.getName());
            if (owns(managed.getOwner(), "SCHEMA", schemaName)) {
                return;
            }
            throw new SecurityException(String.format(
                "Cannot grant privileges on %s '%s': schema '%s' has managed access, where only the schema's owner"
                + " or a role with MANAGE GRANTS decides grants", objectType, objectName, schemaName));
        }
        if (owns(owner, objectType, objectName) || holdsGrantOptions(objectType, objectName, privileges)) {
            return;
        }
        throw new SecurityException(String.format(
            "Cannot grant privileges on %s '%s': requires OWNERSHIP of the object or an administrative"
            + " role (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN)", objectType, objectName));
    }

    /**
     * Authorize ALTER SCHEMA … ENABLE | DISABLE MANAGED ACCESS (documented). Turning it on asks the schema's owner to
     * decide every grant too (MANAGE GRANTS, or an administrative role), and every future grant in the schema to be
     * revoked first; turning it off asks MANAGE GRANTS beside the ownership only while a future grant in the schema is
     * open. The ALTER's own check stands for the owner otherwise.
     *
     * @param schema     the schema
     * @param enable     whether managed access is turned on
     * @param futureOpen whether a future grant in the schema is open
     */
    public void checkManagedAccessSwitch(final Schema schema, final boolean enable, final boolean futureOpen) {
        final String schemaName = QualifiedName.join(schema.getDatabaseName(), schema.getName());
        if (enable && futureOpen) {
            throw new SecurityException(String.format(Locale.ROOT, "Cannot enable managed access on schema '%s': the"
                + " future grants in it must be revoked first", schemaName));
        }
        if (!enable && !futureOpen) {
            return;
        }
        final boolean unchecked = !sessionContext.isSecurityEnabled()
            || "SYSTEM".equals(sessionContext.getCurrentUser()) && !restricted();
        if (unchecked || decidesGrants() && owns(schema.getOwner(), "SCHEMA", schemaName)) {
            return;
        }
        throw new SecurityException(String.format(Locale.ROOT, "Cannot %s managed access on schema '%s'%s: requires"
            + " OWNERSHIP of the schema and MANAGE GRANTS or an administrative role (ACCOUNTADMIN / SECURITYADMIN /"
            + " SYSADMIN)",
            enable ? "enable" : "disable", schemaName, enable ? "" : " while future grants are defined in it"));
    }

    /**
     * Refuses to pass the ownership of an object in a schema WITH MANAGED ACCESS, now or through a future grant, to
     * a role other than the schema's owner or a role granted to it: there ownership passes only to a subordinate role
     * of the schema owner (documented), whichever role grants it.
     *
     * @param schema  the schema the object lives in or the future grant names, or null for none
     * @param what    the object or the future objects as the refusal names them
     * @param grantee the role, or the database role, the ownership passes to
     */
    public void checkManagedOwnershipGrantee(final Schema schema, final String what, final Role grantee) {
        if (schema == null || !schema.isManagedAccess() || schema.getOwner() == null || schema.getOwner().isEmpty()) {
            return;
        }
        for (final Role subordinate : holdersUnder(rolesUnder(catalog.resolveRoleName(schema.getOwner())))) {
            if (subordinate == grantee) {
                return;
            }
        }
        throw new SecurityException(String.format(
            "Cannot grant OWNERSHIP on %s to %s '%s': schema '%s' has managed access, where ownership passes only to"
            + " the schema's owner or a role granted to it", what,
            grantee instanceof DatabaseRole ? "database role" : "role",
            grantee instanceof DatabaseRole ? ((DatabaseRole) grantee).getQualifiedName() : grantee.getName(),
            QualifiedName.join(schema.getDatabaseName(), schema.getName())));
    }

    /**
     * Authorize a grant or a revoke that no object's owner decides, a privilege on the account or a future grant,
     * or that the owner of one object decides beside the roles that decide every grant: a role's owner grants the
     * role, a managed schema's owner its future grants. The roles that decide every grant are the SYSTEM user's,
     * an administrative role (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN) and a role holding MANAGE GRANTS.
     *
     * @param what      the grant's object as the refusal names it
     * @param ownedType the kind of the object whose owner decides too, or null for none
     * @param ownedName that object's name
     * @param owner     the role that owns that object, or null when none is recorded
     */
    public void checkManageGrants(final String what, final String ownedType, final String ownedName,
                                  final String owner) {
        if (decidesGrants() || ownedType != null && owns(owner, ownedType, ownedName)) {
            return;
        }
        throw new SecurityException(String.format(
            "Cannot grant or revoke %s: requires %sMANAGE GRANTS or an administrative role"
            + " (ACCOUNTADMIN / SECURITYADMIN / SYSADMIN)", what,
            ownedType == null ? "" : "OWNERSHIP of the " + ownedType.toLowerCase(Locale.ROOT) + ", "));
    }

    /**
     * Whether a REVOKE takes back one grant on an object. A REVOKE takes back the grants the session made, whose
     * grantor is one of its roles or a role beneath them, and leaves the rest: it succeeds even where it takes back
     * nothing. The roles that decide every grant take back any grant; in a schema WITH MANAGED ACCESS the schema's
     * owner does, and no other role.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name as privileges record it
     * @param grantor    the role recorded as having made the grant, or null when none is recorded
     * @return whether the grant is taken back
     */
    public boolean revokesGrant(final String objectType, final String objectName, final String grantor) {
        if (decidesGrants()) {
            return true;
        }
        final Schema managed = GrantNames.managedSchema(catalog, objectType, objectName);
        if (managed != null) {
            return owns(managed.getOwner(), "SCHEMA", QualifiedName.join(managed.getDatabaseName(), managed.getName()));
        }
        final Set<String> roles = sessionRights();
        return grantor != null
            && (roles.contains(catalog.resolveRoleName(grantor)) || holdsOwningDatabaseRole(roles, grantor));
    }

    /** Whether the session decides every grant: the SYSTEM user, an administrative role, or MANAGE GRANTS. */
    private boolean decidesGrants() {
        if (!sessionContext.isSecurityEnabled()
                || "SYSTEM".equals(sessionContext.getCurrentUser()) && !restricted()) {
            return true;
        }
        final Set<String> roles = sessionRights();
        return roles.contains("SYSADMIN") || managesGrants(roles);
    }

    /** Whether the session's roles own an object: its recorded owner, or the holder of an OWNERSHIP grant on it. */
    private boolean owns(final String owner, final String objectType, final String objectName) {
        final Set<String> roles = sessionRights();
        if (owner != null
                && (roles.contains(catalog.resolveRoleName(owner)) || holdsOwningDatabaseRole(roles, owner))) {
            return true;
        }
        return holdsExactly(holdersUnder(roles), objectType, GrantNames.of(catalog, objectType, objectName),
            Privilege.OWNERSHIP);
    }

    /** Whether the session holds each of the privileges on an object WITH GRANT OPTION; false when none is named. */
    private boolean holdsGrantOptions(final String objectType, final String objectName, final List<String> privileges) {
        if (privileges.isEmpty()) {
            return false;
        }
        final String recorded = GrantNames.of(catalog, objectType, objectName);
        final List<Role> holders = holdersUnder(sessionRights());
        for (final String privilege : privileges) {
            final Privilege named;
            try {
                named = Privilege.valueOf(privilege);
            } catch (final IllegalArgumentException unknown) {
                return false;
            }
            boolean held = false;
            for (final Role holder : holders) {
                held = held || holder.hasGrantOption(objectType, recorded, named);
            }
            if (!held) {
                return false;
            }
        }
        return true;
    }

    /**
     * The role a grant passed on through a grant option is recorded in the name of (documented): the session's
     * primary role where it, or a role beneath it, holds the privilege WITH GRANT OPTION, else the first secondary
     * role in use, by name, that does. Grants the session decides without an option - the roles that decide every
     * grant, the object's owner, a managed schema's owner - are recorded in the owner's name, and answer null here.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name, as privileges record it or as the statement wrote it
     * @param owner      the role that owns the object, or null when none is recorded
     * @param privilege  the privilege passed on
     * @return the grantor to record, or null for the owner
     */
    public String grantOptionGrantor(final String objectType, final String objectName, final String owner,
                                     final String privilege) {
        if (decidesGrants() || GrantNames.managedSchema(catalog, objectType, objectName) != null
                || owns(owner, objectType, objectName)) {
            return null;
        }
        final Privilege named;
        try {
            named = Privilege.valueOf(privilege);
        } catch (final IllegalArgumentException unknown) {
            return null;
        }
        final String recorded = GrantNames.of(catalog, objectType, objectName);
        final List<String> candidates = new ArrayList<>();
        final String executableOwner = sessionContext.getExecutableOwner();
        if (executableOwner != null) {
            candidates.add(catalog.resolveRoleName(executableOwner));
        } else {
            final String primary = sessionContext.getCurrentRole();
            if (primary != null) {
                candidates.add(catalog.resolveRoleName(primary));
            }
            final List<String> secondary = new ArrayList<>(getEffectiveRoles());
            Collections.sort(secondary);
            candidates.addAll(secondary);
        }
        for (final String candidate : candidates) {
            for (final Role holder : holdersUnder(rolesUnder(candidate))) {
                if (holder.hasGrantOption(objectType, recorded, named)) {
                    return candidate;
                }
            }
        }
        return null;
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
        final Set<String> roles = sessionRights();
        return roles.contains(catalog.resolveRoleName(owner)) || holdsOwningDatabaseRole(roles, owner);
    }

    /**
     * Whether an owner that is a database role, named by its qualified name, is held under these roles: granted to
     * one of them, or to a database role granted to one, however far down.
     */
    private boolean holdsOwningDatabaseRole(final Set<String> roles, final String owner) {
        if (owner.indexOf('.') < 0) {
            return false;
        }
        for (final Role holder : holdersUnder(roles)) {
            if (holder instanceof DatabaseRole && owner.equals(((DatabaseRole) holder).getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the session holds a privilege on an object: one of its roles — the primary role, a secondary role
     * in use, a role any of those inherits, PUBLIC, or a database role granted to one of them — owns the
     * object or was granted the privilege. No administrative role stands in for that: ACCOUNTADMIN reads, writes
     * and alters only what it holds (live-verified). Inside a stored procedure that runs with owner's rights the
     * owner's roles are asked in the session's place, and inside a view's own query the view's owner is asked
     * beside them, since either's right to a relation lets the view read it. An object with no recorded owner
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
        if (restricted()) {
            return callerGranted(privilege.name(), objectType, objectName)
                && callerHolds(privilege, objectType, objectName, owner);
        }
        final Set<String> roles = askedRoles();
        return roles.contains(catalog.resolveRoleName(owner)) || holdsOwningDatabaseRole(roles, owner)
            || holdsExactly(holdersUnder(roles), objectType, GrantNames.of(catalog, objectType, objectName),
                privilege);
    }

    /**
     * Whether a caller grant the restricted body's owner holds names a privilege on an object (see
     * {@link #restricted}): granted on the object itself, on every object of its kind in its schema, its database or
     * the account, or with ALL CALLER PRIVILEGES.
     *
     * @param privilege  the privilege, by its enumeration name, or null for any one
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name in full
     * @return whether one does
     */
    private boolean callerGranted(final String privilege, final String objectType, final String objectName) {
        final String owner = sessionContext.getRestrictedOwner();
        final String ownerRole = owner == null ? null : catalog.resolveRoleName(owner);
        if (ownerRole == null || !catalog.roleExists(ownerRole)) {
            return false;
        }
        final String recorded = GrantNames.of(catalog, objectType, objectName);
        final String[] containers = GrantNames.containers(objectType, recorded);
        final String database = containers.length > 0 ? containers[0] : null;
        final String schema = containers.length > 1 ? QualifiedName.join(containers[0], containers[1]) : null;
        for (final CallerGrant grant : catalog.getRoleExact(ownerRole).getCallerGrants()) {
            if (grant.covers(privilege == null ? grant.getPrivilege() : privilege, objectType, recorded, database,
                    schema)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the caller of a restricted body holds a privilege on an object by its own rights, which a caller grant
     * lets the body use: the session's roles, PUBLIC among them, own it or were granted it; the SYSTEM user holds
     * every one.
     */
    private boolean callerHolds(final Privilege privilege, final String objectType, final String objectName,
                                final String owner) {
        if ("SYSTEM".equals(sessionContext.getCurrentUser()) || owner == null) {
            return true;
        }
        final Set<String> roles = getEffectiveRoles();
        roles.add("PUBLIC");
        return roles.contains(catalog.resolveRoleName(owner)) || holdsOwningDatabaseRole(roles, owner)
            || holdsExactly(holdersUnder(roles), objectType, GrantNames.of(catalog, objectType, objectName),
                privilege);
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
        if (restricted()) {
            return callerGranted(null, objectType, objectName)
                && ("SYSTEM".equals(sessionContext.getCurrentUser()) || holdAny(callerRights(), objectType,
                    objectName, owner));
        }
        return holdAny(askedRoles(), objectType, objectName, owner);
    }

    /** The roles the caller of a restricted body holds its own privileges through: the session's, with PUBLIC. */
    private Set<String> callerRights() {
        final Set<String> roles = getEffectiveRoles();
        roles.add("PUBLIC");
        return roles;
    }

    /**
     * Whether the rights a statement runs with reach an object, which is what lists it in INFORMATION_SCHEMA:
     * they own it, or hold whatever privilege on it, and use its database and schema (see usesContainers). Inside a
     * stored procedure that runs with owner's rights
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
        final Set<String> roles = reachingRoles();
        return holdAny(roles, objectType, objectName, owner) && usesContainers(roles, objectType, objectName);
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
        return holdAny(roles, objectType, objectName, owner) && usesContainers(roles, objectType, objectName)
            || managesGrants(roles);
    }

    /**
     * Whether the roles use an object's containers, which reaching it takes beside a privilege on the object itself:
     * USAGE on the database and the schema of a schema's object, on the database of a schema (documented: "To access
     * a specific object within a schema, a role must have USAGE on the database and schema"). Owning a container, ALL
     * and OWNERSHIP grants count as USAGE.
     */
    private boolean usesContainers(final Set<String> roles, final String objectType, final String objectName) {
        final String[] containers = GrantNames.containers(objectType, objectName);
        if (containers.length > 0 && !uses(roles, "DATABASE", containers[0])) {
            return false;
        }
        return containers.length < 2 || uses(roles, "SCHEMA", containers[0] + "." + containers[1]);
    }

    /**
     * The roles {@link #reaches} asks: the session's, or inside a stored procedure that runs with owner's rights the
     * owner's alone, with PUBLIC beside them.
     */
    private Set<String> reachingRoles() {
        final String executableOwner = sessionContext.getExecutableOwner();
        if (executableOwner == null || restricted()) {
            return askedRoles();
        }
        final Set<String> roles = rolesUnder(catalog.resolveRoleName(executableOwner));
        roles.add("PUBLIC");
        return roles;
    }

    /**
     * Whether a RESTRICTED CALLER procedure's body runs on this thread. Restricted caller's rights let a body use only
     * those of its caller's privileges a caller grant names ("every privilege required by the executable must be
     * specified in one or more caller grants", documented): a privilege on an object, USAGE on its database and schema
     * included, is held when a caller grant the procedure's owner holds names it and the caller holds it (see
     * {@link #callerGranted} and {@link #callerHolds}). No other privilege is held - none on the account, none to
     * create - PUBLIC's and the SYSTEM user's included.
     */
    private boolean restricted() {
        return sessionContext.isRestrictedCaller();
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

    /**
     * Whether the roles hold any privilege on an object: one of them owns it, or holds a database role that owns it,
     * or whatever grant on it reaches them (see {@link #holdsPrivilege}).
     */
    private boolean holdAny(final Set<String> roles, final String objectType, final String objectName,
                            final String owner) {
        if (roles.contains(catalog.resolveRoleName(owner)) || holdsOwningDatabaseRole(roles, owner)) {
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
     * The roles a privilege is asked of: the session's rights (see {@link #sessionRights}), PUBLIC, and inside a
     * view's query the view owner's too.
     */
    private Set<String> askedRoles() {
        final Set<String> roles = sessionRights();
        if (!restricted()) {
            roles.add("PUBLIC");
        }
        final String rightsOwner = ownerRights.get();
        if (rightsOwner != null) {
            roles.addAll(rolesUnder(rightsOwner));
        }
        return roles;
    }

    /**
     * The roles the session's rights come from: the session's own (see {@link #getEffectiveRoles}), or inside a
     * stored procedure that runs with owner's rights the owner's alone, with PUBLIC: such a procedure runs with its
     * owner's privileges and none of its caller's (documented). Inside a RESTRICTED CALLER body there are none (see
     * {@link #restricted}).
     */
    private Set<String> sessionRights() {
        if (restricted()) {
            return new HashSet<>();
        }
        final String executableOwner = sessionContext.getExecutableOwner();
        if (executableOwner == null) {
            return getEffectiveRoles();
        }
        final Set<String> roles = rolesUnder(catalog.resolveRoleName(executableOwner));
        roles.add("PUBLIC");
        return roles;
    }

    /**
     * Whether the session holds a privilege on the account: one of its roles (see {@link #sessionRights}) was granted
     * it on the account, or is a system role that holds it from the start - ACCOUNTADMIN every privilege on the
     * account, SECURITYADMIN MANAGE GRANTS, USERADMIN, and so SECURITYADMIN above it, CREATE USER and CREATE ROLE
     * (documented). The SYSTEM user holds every one, as does anything while security checks are off.
     *
     * @param privilege the privilege the statement needs
     * @return whether the statement may go on
     */
    public boolean holdsAccountPrivilege(final Privilege privilege) {
        if (!sessionContext.isSecurityEnabled()) {
            return true;
        }
        if (restricted()) {
            return false;
        }
        if ("SYSTEM".equals(sessionContext.getCurrentUser())) {
            return true;
        }
        final Set<String> roles = sessionRights();
        roles.add("PUBLIC");
        if (roles.contains("ACCOUNTADMIN")) {
            return true;
        }
        if ((privilege == Privilege.CREATE_USER || privilege == Privilege.CREATE_ROLE) && roles.contains("USERADMIN")) {
            return true;
        }
        if (privilege == Privilege.MANAGE_GRANTS) {
            return managesGrants(roles);
        }
        return holds(holdersUnder(roles), "ACCOUNT", "ACCOUNT", privilege);
    }

    /**
     * Whether the session may act as a role's or a user's owner, as a DROP or a replacement of either asks (see
     * {@link #actsAsOwner}). The SYSTEM user, the engine's own, may for every one, as it holds every account
     * privilege (see {@link #holdsAccountPrivilege}), and so may anything while security checks are off.
     *
     * @param owner the role or user's owner role; null when none is recorded, which passes
     */
    public boolean mayActAsPrincipalOwner(final String owner) {
        if (!sessionContext.isSecurityEnabled()) {
            return true;
        }
        if (restricted()) {
            return false;
        }
        return "SYSTEM".equals(sessionContext.getCurrentUser()) || actsAsOwner(owner);
    }

    /**
     * Whether the session may alter a user: it is the session's own user, or one of the session's roles owns the user
     * or holds MODIFY or OWNERSHIP on it (documented). The SYSTEM user may alter every user, as may anything while
     * security checks are off. A user the session alters itself may change only some of what it has; the caller
     * judges that.
     *
     * @param userName the user's name as the catalog keeps it
     * @param owner    the role that owns the user, or null when none is recorded
     * @return whether one of the session's roles may alter the user
     */
    public boolean mayModifyUser(final String userName, final String owner) {
        if (!sessionContext.isSecurityEnabled()) {
            return true;
        }
        if (restricted()) {
            return false;
        }
        if ("SYSTEM".equals(sessionContext.getCurrentUser())) {
            return true;
        }
        final Set<String> roles = sessionRights();
        roles.add("PUBLIC");
        if (owner != null
                && (roles.contains(catalog.resolveRoleName(owner)) || holdsOwningDatabaseRole(roles, owner))) {
            return true;
        }
        final List<Role> holders = holdersUnder(roles);
        final String recorded = GrantNames.of(catalog, "USER", userName);
        return holdsExactly(holders, "USER", recorded, Privilege.MODIFY);
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
        if (restricted()) {
            return false;
        }
        final String ownerRole = catalog.resolveRoleName(owner);
        final String rightsOwner = ownerRights.get();
        final String acting = rightsOwner != null ? rightsOwner : sessionContext.getCreatingRole();
        if (acting == null || !catalog.roleExists(ownerRole)) {
            return true;
        }
        final Set<String> roles = rolesUnder(catalog.resolveRoleName(acting));
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
        return owner == null || !sessionContext.isSecurityEnabled()
            || askedRoles().contains(catalog.resolveRoleName(owner));
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
        if (restricted()) {
            // A restricted body uses a container by a caller grant of USAGE on it, its caller holding that USAGE.
            if (!usesByCallerGrant("DATABASE", databaseName)) {
                return "DATABASE";
            }
            return usesByCallerGrant("SCHEMA", databaseName + "." + schemaName) ? null : "SCHEMA";
        }
        final Set<String> roles = askedRoles();
        if (!uses(roles, "DATABASE", databaseName)) {
            return "DATABASE";
        }
        return uses(roles, "SCHEMA", databaseName + "." + schemaName) ? null : "SCHEMA";
    }

    /** Whether a restricted body uses a container: a caller grant names USAGE on it, and its caller may use it. */
    private boolean usesByCallerGrant(final String containerType, final String containerName) {
        return callerGranted(Privilege.USAGE.name(), containerType, containerName)
            && ("SYSTEM".equals(sessionContext.getCurrentUser()) || uses(callerRights(), containerType, containerName));
    }

    private boolean uses(final Set<String> roles, final String containerType, final String containerName) {
        final String owner = catalog.getObjectOwnerRole(containerType, containerName);
        if (owner == null || roles.contains(catalog.resolveRoleName(owner))) {
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
        if (ownerRole != null && catalog.roleExists(catalog.resolveRoleName(ownerRole))) {
            ownerRights.set(catalog.resolveRoleName(ownerRole));
        }
        sessionContext.enterBodyOwner(ownerRole);
        return previous;
    }

    /**
     * Returns to the rights in force before {@link #enterOwnerRights}.
     *
     * @param previous what {@link #enterOwnerRights} answered
     */
    public void leaveOwnerRights(final String previous) {
        sessionContext.leaveBodyOwner();
        if (previous == null) {
            ownerRights.remove();
        } else {
            ownerRights.set(previous);
        }
    }

    /** A role and every role granted to it, however many levels down. */
    private Set<String> rolesUnder(final String roleName) {
        return closureOf(Collections.singleton(roleName));
    }

    /** Roles and every role granted to one of them, however many levels down. */
    private Set<String> closureOf(final Collection<String> starting) {
        final Set<String> roles = new HashSet<>();
        final List<String> pending = new ArrayList<>(starting);
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
     * The roles the session acts with, and every role granted to one of them however many levels down: the primary
     * role, the secondary roles in use, and PUBLIC. A role the user was granted but the session never activated
     * authorizes nothing, so with no secondary roles in use the primary role decides alone (documented). Secondary
     * roles ALL stand for every role granted to the session's user as the statement runs: one granted after
     * USE SECONDARY ROLES ALL is in use, and one revoked is not (documented).
     */
    private Set<String> getEffectiveRoles() {
        final Set<String> activated = new HashSet<>(sessionContext.getActiveRoles());
        if ("ALL".equals(sessionContext.getSecondaryRolesValue())) {
            final String executing = sessionContext.getExecutingUser();
            if (executing != null && catalog.hasUser(executing)) {
                activated.addAll(catalog.getUser(executing).getGrantedRoles());
            }
        }
        return closureOf(activated);
    }

    /**
     * The roles enabled in the session, as INFORMATION_SCHEMA.ENABLED_ROLES lists them: every role in use and every
     * role granted to one of them, however many levels down, and PUBLIC, which is always enabled (documented).
     *
     * @return the roles' names
     */
    public Set<String> enabledRoles() {
        final Set<String> roles = getEffectiveRoles();
        roles.add("PUBLIC");
        return roles;
    }

    /**
     * Get the session context
     */
    public SessionContext getSessionContext() {
        return sessionContext;
    }
}
