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

import dev.frostlake.metastore.StatementSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages the current user session context including active user and role
 */
public class SessionContext {

    /**
     * The SQL text of the statement the session on this thread is executing. It lives with that session's
     * settings, not on this object, which every session of one engine shares: two sessions each read their
     * own statement, however their statements interleave.
     */
    public String getCurrentStatement() {
        return settings().getCurrentStatement();
    }

    public void setCurrentStatement(final String currentStatement) {
        settings().setCurrentStatement(currentStatement);
    }

    /**
     * The id of the last transaction the session on this thread committed or rolled back — its own, never the
     * last one any session ended. Kept with the session's settings for the same reason as the statement.
     */
    public String getLastTransactionId() {
        return settings().getLastTransactionId();
    }

    public void setLastTransactionId(final String lastTransactionId) {
        settings().setLastTransactionId(lastTransactionId);
    }

    private String currentUser;
    private String displayUser;
    /**
     * The roles of a statement run outside any front-end's session. A bound session takes these until it sets its
     * own (USE ROLE, USE SECONDARY ROLES), which are then its alone: a role one session switches to never becomes
     * another's.
     */
    private SessionRoles ownRoles = new SessionRoles();
    private boolean securityEnabled;
    /**
     * The settings of a statement run outside any front-end's session: the ALTER SESSION parameters and the
     * session VARIABLES SET defined, in the order SET created them. The two are stored apart: a variable and
     * a parameter may carry the same name without either seeing the other, so {@code SET timezone = 'abc'}
     * leaves the TIMEZONE parameter — and the session's clock — untouched, and an ALTER SESSION parameter
     * never becomes readable as {@code $name}.
     */
    private final SessionSettings ownSettings = new SessionSettings();
    /** The settings of the session whose request runs on this thread, when a front-end bound one. */
    private final ThreadLocal<SessionSettings> boundSettings = new ThreadLocal<SessionSettings>();
    /** The owner of the owner's-rights procedure running on this thread (see {@link #getExecutableOwner}). */
    private final ThreadLocal<String> executableOwner = new ThreadLocal<String>();
    /** Where the session's user and account keep their parameters; null when nothing is wired. */
    private InheritedParameters inheritedParameters;

    public SessionContext() {
        this.securityEnabled = true;
        // By default, start as a system admin
        this.currentUser = "SYSTEM";
        this.displayUser = "SYSTEM";
    }

    /** The roles this thread's statements read: the bound session's own, else the context's. */
    private SessionRoles roles() {
        final SessionSettings bound = boundSettings.get();
        return bound != null && bound.getRoles() != null ? bound.getRoles() : ownRoles;
    }

    /** The roles this thread's statements change: a bound session gets its own, starting from the context's. */
    private SessionRoles ownedRoles() {
        final SessionSettings bound = boundSettings.get();
        if (bound == null) {
            return ownRoles;
        }
        if (bound.getRoles() == null) {
            bound.setRoles(new SessionRoles(ownRoles));
        }
        return bound.getRoles();
    }

    /**
     * The id of the session whose statement runs on this thread: the bound session's when a front-end bound
     * one (an HTTP session, a JDBC connection), the engine's own otherwise. Each session has its own, and it
     * holds for the session's life.
     *
     * @return the session's number, as CURRENT_SESSION() answers it
     */
    public String getSessionId() {
        return String.valueOf(getSessionNumber());
    }

    /**
     * The same id as a number, the way SHOW TRANSACTIONS, SHOW LOCKS and SHOW VARIABLES print it.
     *
     * @return the session's number
     */
    public long getSessionNumber() {
        return settings().getSessionNumber();
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(final String currentUser) {
        this.currentUser = currentUser != null ? currentUser.toUpperCase(Locale.ROOT) : null;
    }

    /** Display name returned by CURRENT_USER() — may differ from the internal security user. */
    public String getDisplayUser() {
        return displayUser != null ? displayUser : currentUser;
    }

    public void setDisplayUser(final String displayUser) {
        this.displayUser = displayUser != null ? displayUser.toUpperCase(Locale.ROOT) : null;
    }

    public String getCurrentRole() {
        return roles().getCurrentRole();
    }

    public void setCurrentRole(final String currentRole) {
        final SessionRoles roles = ownedRoles();
        roles.setCurrentRole(currentRole != null ? currentRole.toUpperCase(Locale.ROOT) : null);
        if (currentRole != null) {
            roles.activeRoles().add(currentRole.toUpperCase(Locale.ROOT));
        }
    }

    public Set<String> getActiveRoles() {
        return new HashSet<>(roles().activeRoles());
    }

    /**
     * The role that owns the stored procedure running with owner's rights on this thread, or null where none
     * runs: the role its statements create objects as, and for, in place of the session's primary role.
     */
    public String getExecutableOwner() {
        return executableOwner.get();
    }

    /**
     * Enter or leave an owner's-rights procedure on this thread.
     *
     * @param owner the procedure's owner, or null to leave; a caller puts back what {@link #getExecutableOwner}
     *              answered before the call
     */
    public void setExecutableOwner(final String owner) {
        if (owner == null) {
            executableOwner.remove();
        } else {
            executableOwner.set(owner.toUpperCase(Locale.ROOT));
        }
    }

    /** The role a statement creates as: an owner's-rights procedure's owner while one runs, else the primary role. */
    public String getCreatingRole() {
        final String owner = executableOwner.get();
        return owner != null ? owner : roles().getCurrentRole();
    }

    public void addActiveRole(final String role) {
        if (role != null) {
            ownedRoles().activeRoles().add(role.toUpperCase(Locale.ROOT));
        }
    }

    public void clearActiveRoles() {
        final Set<String> active = ownedRoles().activeRoles();
        active.clear();
        // Always keep PUBLIC role
        active.add("PUBLIC");
    }

    /**
     * Whether the session asked for secondary roles with USE SECONDARY ROLES, which a privilege refusal says:
     * {@code Your primary role R or one of your secondary roles must have …}.
     */
    public boolean isSecondaryRolesInUse() {
        return roles().isSecondaryRolesInUse();
    }

    /** Records whether the session asked for secondary roles. */
    public void setSecondaryRolesInUse(final boolean secondaryRolesInUse) {
        ownedRoles().setSecondaryRolesInUse(secondaryRolesInUse);
    }

    /**
     * What USE SECONDARY ROLES last asked for, as CURRENT_SECONDARY_ROLES() answers its {@code value}.
     *
     * @return {@code ALL}, the roles named joined by commas, or the empty text for none
     */
    public String getSecondaryRolesValue() {
        return roles().getSecondaryRolesValue();
    }

    /**
     * Records what USE SECONDARY ROLES asked for; anything but the empty text puts secondary roles in use.
     *
     * @param value {@code ALL}, the roles named joined by commas, or the empty text for none
     */
    public void setSecondaryRoles(final String value) {
        final SessionRoles roles = ownedRoles();
        roles.setSecondaryRolesValue(value);
        roles.setSecondaryRolesInUse(!value.isEmpty());
    }

    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    public void setSecurityEnabled(final boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public void reset() {
        currentUser = "SYSTEM";
        displayUser = "SYSTEM";
        ownRoles = new SessionRoles();
        final SessionSettings bound = boundSettings.get();
        if (bound != null) {
            bound.setRoles(null);
        }
        securityEnabled = true;
        settings().reset();
    }

    /**
     * Run this thread's statements under {@code settings}, one session's parameters and variables, until
     * {@link #unbindSettings}. A front-end serving several sessions on one engine binds each request's own,
     * so an ALTER SESSION or a SET in one session never reaches another.
     *
     * @param settings the session's settings
     */
    public void bindSettings(final SessionSettings settings) {
        boundSettings.set(settings);
        // What the thread's statements create from here on is the bound session's.
        StatementSession.bind(settings == null ? null : Long.valueOf(settings.getSessionNumber()));
    }

    /** Back to the context's own settings on this thread. */
    public void unbindSettings() {
        boundSettings.remove();
        StatementSession.unbind();
    }

    /**
     * The settings bound to this thread, so work that runs as a session of its own can bind its own and put these
     * back afterwards.
     *
     * @return the bound settings, or null when the thread uses the context's own
     */
    public SessionSettings getBoundSettings() {
        return boundSettings.get();
    }

    /** The settings this thread's statements read and write. */
    private SessionSettings settings() {
        final SessionSettings bound = boundSettings.get();
        return bound != null ? bound : ownSettings;
    }

    public void setSessionParameter(final String name, final Object value) {
        if (name != null) {
            settings().parameters().put(name.toUpperCase(Locale.ROOT), value);
        }
    }

    /**
     * A parameter's value for this session: what ALTER SESSION set, else what the session's user carries, else
     * what the account sets; null when none of them sets it and the parameter is at its default.
     */
    public Object getSessionParameter(final String name) {
        if (name == null) {
            return null;
        }
        final String key = name.toUpperCase(Locale.ROOT);
        final Object own = settings().parameters().get(key);
        if (own != null || inheritedParameters == null) {
            return own;
        }
        return inheritedParameters.value(getDisplayUser(), key);
    }

    /** Wire where this session's user and account keep the parameters set on them. */
    public void setInheritedParameters(final InheritedParameters inheritedParameters) {
        this.inheritedParameters = inheritedParameters;
    }

    public void unsetSessionParameter(final String name) {
        if (name != null) {
            settings().parameters().remove(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Define, or redefine, the session variable SET names. */
    public void setSessionVariable(final String name, final Object value) {
        if (name != null) {
            settings().variables().put(name.toUpperCase(Locale.ROOT), value);
        }
    }

    /** A session variable's value, or null when SET never defined one of that name. */
    public Object getSessionVariable(final String name) {
        if (name == null) {
            return null;
        }
        return settings().variables().get(name.toUpperCase(Locale.ROOT));
    }

    /** Forget a session variable UNSET removed. Removing a name no SET defined is silently accepted. */
    public void unsetSessionVariable(final String name) {
        if (name != null) {
            settings().variables().remove(name.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Whether SET defined a session variable of this name. A session PARAMETER is not one: live refuses
     * {@code $TIMEZONE} and {@code $QUERY_TAG} unless a SET made a variable of that name.
     */
    public boolean isSessionVariable(final String name) {
        return name != null && settings().variables().containsKey(name.toUpperCase(Locale.ROOT));
    }

    public Map<String, Object> getAllSessionParameters() {
        return new HashMap<>(settings().parameters());
    }

    /** Every session variable SET defined, in creation order — what SHOW VARIABLES lists. */
    public Map<String, Object> getAllSessionVariables() {
        return new LinkedHashMap<>(settings().variables());
    }
}
