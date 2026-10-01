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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private String currentRole;
    private String displayUser;
    private final Set<String> activeRoles;
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

    public SessionContext() {
        this.activeRoles = new HashSet<>();
        this.securityEnabled = true;
        // By default, start as a system admin
        this.currentUser = "SYSTEM";
        this.displayUser = "SYSTEM";
        this.currentRole = "SYSADMIN";
        this.activeRoles.add("SYSADMIN");
        this.activeRoles.add("PUBLIC");
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
        this.currentUser = currentUser != null ? currentUser.toUpperCase() : null;
    }

    /** Display name returned by CURRENT_USER() — may differ from the internal security user. */
    public String getDisplayUser() {
        return displayUser != null ? displayUser : currentUser;
    }

    public void setDisplayUser(final String displayUser) {
        this.displayUser = displayUser != null ? displayUser.toUpperCase() : null;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(final String currentRole) {
        this.currentRole = currentRole != null ? currentRole.toUpperCase() : null;
        if (currentRole != null) {
            activeRoles.add(currentRole.toUpperCase());
        }
    }

    public Set<String> getActiveRoles() {
        return new HashSet<>(activeRoles);
    }

    public void addActiveRole(final String role) {
        if (role != null) {
            activeRoles.add(role.toUpperCase());
        }
    }

    public void clearActiveRoles() {
        activeRoles.clear();
        // Always keep PUBLIC role
        activeRoles.add("PUBLIC");
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
        currentRole = "SYSADMIN";
        activeRoles.clear();
        activeRoles.add("SYSADMIN");
        activeRoles.add("PUBLIC");
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
    }

    /** Back to the context's own settings on this thread. */
    public void unbindSettings() {
        boundSettings.remove();
    }

    /** The settings this thread's statements read and write. */
    private SessionSettings settings() {
        final SessionSettings bound = boundSettings.get();
        return bound != null ? bound : ownSettings;
    }

    public void setSessionParameter(final String name, final Object value) {
        if (name != null) {
            settings().parameters().put(name.toUpperCase(), value);
        }
    }

    public Object getSessionParameter(final String name) {
        if (name == null) {
            return null;
        }
        return settings().parameters().get(name.toUpperCase());
    }

    public void unsetSessionParameter(final String name) {
        if (name != null) {
            settings().parameters().remove(name.toUpperCase());
        }
    }

    /** Define, or redefine, the session variable SET names. */
    public void setSessionVariable(final String name, final Object value) {
        if (name != null) {
            settings().variables().put(name.toUpperCase(), value);
        }
    }

    /** A session variable's value, or null when SET never defined one of that name. */
    public Object getSessionVariable(final String name) {
        if (name == null) {
            return null;
        }
        return settings().variables().get(name.toUpperCase());
    }

    /** Forget a session variable UNSET removed. Removing a name no SET defined is silently accepted. */
    public void unsetSessionVariable(final String name) {
        if (name != null) {
            settings().variables().remove(name.toUpperCase());
        }
    }

    /**
     * Whether SET defined a session variable of this name. A session PARAMETER is not one: live refuses
     * {@code $TIMEZONE} and {@code $QUERY_TAG} unless a SET made a variable of that name.
     */
    public boolean isSessionVariable(final String name) {
        return name != null && settings().variables().containsKey(name.toUpperCase());
    }

    public Map<String, Object> getAllSessionParameters() {
        return new HashMap<>(settings().parameters());
    }

    /** Every session variable SET defined, in creation order — what SHOW VARIABLES lists. */
    public Map<String, Object> getAllSessionVariables() {
        return new LinkedHashMap<>(settings().variables());
    }
}
