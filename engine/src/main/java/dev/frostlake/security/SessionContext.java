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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
    /** Whether a RESTRICTED CALLER procedure's body runs on this thread, which holds none of the caller's rights. */
    private final ThreadLocal<Boolean> restrictedCaller = new ThreadLocal<Boolean>();
    private final ThreadLocal<String> restrictedOwner = new ThreadLocal<String>();

    /** The owners of the views and functions whose bodies run on this thread, innermost last; "" for none. */
    private final ThreadLocal<List<String>> bodyOwners = new ThreadLocal<List<String>>();
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

    /**
     * The user the session's statements run for, whose granted roles are the ones USE ROLE may activate and the ones
     * secondary roles ALL stand for: the session's user, or for the engine's own session, which runs as SYSTEM, the
     * user CURRENT_USER() names.
     *
     * @return the user's name, upper-cased
     */
    public String getExecutingUser() {
        return "SYSTEM".equals(currentUser) ? getDisplayUser() : currentUser;
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

    /**
     * Makes a role the session's primary one, adding it to the roles the session acts with and taking none away: the
     * embedded API's switch, which activates any role. USE ROLE goes through {@link #useRole}.
     *
     * @param currentRole the role's own name, as the catalog keeps it: a quoted name as written
     */
    public void setCurrentRole(final String currentRole) {
        final SessionRoles roles = ownedRoles();
        roles.setCurrentRole(currentRole);
        if (currentRole != null) {
            roles.activeRoles().add(currentRole);
        }
    }

    /**
     * Makes a role the session's primary one, as USE ROLE does: the primary role it replaces stops acting for the
     * session, unless USE SECONDARY ROLES named it, which keeps it as a secondary role.
     *
     * @param role the role's own name, as the catalog keeps it: a quoted name as written
     */
    public void useRole(final String role) {
        final SessionRoles roles = ownedRoles();
        final String former = roles.getCurrentRole();
        if (former != null && !former.equals(role) && !"PUBLIC".equals(former)
                && !namesSecondaryRole(roles.getSecondaryRolesValue(), former)) {
            roles.activeRoles().remove(former);
        }
        setCurrentRole(role);
    }

    /** Whether what USE SECONDARY ROLES asked for names a role: the roles named, joined by commas. */
    private static boolean namesSecondaryRole(final String asked, final String role) {
        if (asked == null || asked.isEmpty() || "ALL".equals(asked)) {
            return false;
        }
        for (final String named : asked.split(",")) {
            if (named.equals(role)) {
                return true;
            }
        }
        return false;
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
            executableOwner.set(owner);
        }
    }

    /**
     * Whether a procedure that runs with restricted caller's rights runs on this thread: its statements hold only
     * those of the caller's privileges a caller grant its owner holds names (see {@link #getRestrictedOwner}).
     *
     * @return true inside such a body, and inside a caller's-rights body it calls
     */
    public boolean isRestrictedCaller() {
        return Boolean.TRUE.equals(restrictedCaller.get());
    }

    /**
     * Enter or leave a restricted caller's-rights body on this thread; a caller puts back what
     * {@link #isRestrictedCaller} answered before the call.
     *
     * @param restricted whether the body that runs now is restricted
     */
    public void setRestrictedCaller(final boolean restricted) {
        if (restricted) {
            restrictedCaller.set(Boolean.TRUE);
        } else {
            restrictedCaller.remove();
        }
    }

    /**
     * The role that owns the restricted caller's-rights procedure whose body runs on this thread, whose caller grants
     * say which of the caller's privileges the body may use.
     *
     * @return the owner's name, or null outside such a body
     */
    public String getRestrictedOwner() {
        return isRestrictedCaller() ? restrictedOwner.get() : null;
    }

    /**
     * Name the owner of the restricted caller's-rights procedure entered on this thread; a caller puts back what
     * {@link #getRestrictedOwner} answered before the call.
     *
     * @param owner the procedure's owner, or null
     */
    public void setRestrictedOwner(final String owner) {
        if (owner != null) {
            restrictedOwner.set(owner);
        } else {
            restrictedOwner.remove();
        }
    }

    /**
     * Enter the body of a view or a function on this thread, which runs as its owner's: {@link #getInvokerOwner}
     * answers that owner until the matching {@link #leaveBodyOwner}.
     *
     * @param owner the role that owns the view or the function, or null when none is recorded
     */
    public void enterBodyOwner(final String owner) {
        List<String> owners = bodyOwners.get();
        if (owners == null) {
            owners = new ArrayList<>();
            bodyOwners.set(owners);
        }
        owners.add(owner == null ? "" : owner);
    }

    /** Leave the body {@link #enterBodyOwner} entered last on this thread. */
    public void leaveBodyOwner() {
        final List<String> owners = bodyOwners.get();
        if (owners == null || owners.isEmpty()) {
            return;
        }
        owners.remove(owners.size() - 1);
        if (owners.isEmpty()) {
            bodyOwners.remove();
        }
    }

    /**
     * The owner of the innermost object executing on this thread, which INVOKER_ROLE() names: the view or the
     * function whose body runs, else the owner's-rights procedure that runs; null outside them all.
     *
     * @return the owning role's name as the object records it, or null
     */
    public String getInvokerOwner() {
        final List<String> owners = bodyOwners.get();
        if (owners != null) {
            for (int i = owners.size() - 1; i >= 0; i--) {
                if (!owners.get(i).isEmpty()) {
                    return owners.get(i);
                }
            }
        }
        return executableOwner.get();
    }

    /** The role a statement creates as: an owner's-rights procedure's owner while one runs, else the primary role. */
    public String getCreatingRole() {
        final String owner = executableOwner.get();
        return owner != null ? owner : roles().getCurrentRole();
    }

    /**
     * Adds a role to those the session acts with.
     *
     * @param role the role's own name, as the catalog keeps it
     */
    public void addActiveRole(final String role) {
        if (role != null) {
            ownedRoles().activeRoles().add(role);
        }
    }

    /**
     * Takes a role out of the roles the session acts with; PUBLIC stays.
     *
     * @param role the role's own name, as the catalog keeps it
     */
    public void removeActiveRole(final String role) {
        if (role != null && !"PUBLIC".equals(role)) {
            ownedRoles().activeRoles().remove(role);
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

    /**
     * @return whether this thread's session has set CLIENT_RESULT_COLUMN_CASE_INSENSITIVE to TRUE
     */
    public boolean resultColumnsCaseInsensitive() {
        return settings().resultColumnsCaseInsensitive();
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
            settings().decfloatVariables().remove(name.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Define, or redefine, a session variable a SET gave a DECFLOAT: the value is the decimal, and the variable is
     * typed DECFLOAT — {@code SET v = 1.5::DECFLOAT} reads DECFLOAT(38) in SYSTEM$TYPEOF and {@code decfloat} in SHOW
     * VARIABLES (live-verified).
     *
     * @param name  the variable's name
     * @param value its value
     */
    public void setDecfloatSessionVariable(final String name, final Object value) {
        if (name != null) {
            setSessionVariable(name, value);
            settings().decfloatVariables().add(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Whether a SET gave the session variable of this name a DECFLOAT. */
    public boolean isDecfloatSessionVariable(final String name) {
        return name != null && settings().decfloatVariables().contains(name.toUpperCase(Locale.ROOT));
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
            settings().decfloatVariables().remove(name.toUpperCase(Locale.ROOT));
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

    /**
     * Puts back the parameters and variables a session held, as the write-ahead log's replay does before each
     * statement it runs again: every one the log names takes its logged value, one the log could not keep stays as
     * this session holds it, and any other is gone.
     *
     * @param parameters       the parameters the session had set, by upper-case name
     * @param variables        its variables, by upper-case name
     * @param unkeptParameters the parameters whose values the log could not keep
     * @param unkeptVariables  the variables whose values the log could not keep
     */
    public void restoreSessionState(final Map<String, Object> parameters, final Map<String, Object> variables,
                                    final Set<String> unkeptParameters, final Set<String> unkeptVariables) {
        restore(settings().parameters(), parameters, unkeptParameters);
        restore(settings().variables(), variables, unkeptVariables);
        settings().decfloatVariables().retainAll(settings().variables().keySet());
    }

    private static void restore(final Map<String, Object> held, final Map<String, Object> logged,
                                final Set<String> unkept) {
        final Iterator<String> names = held.keySet().iterator();
        while (names.hasNext()) {
            final String name = names.next();
            if (!logged.containsKey(name) && !unkept.contains(name)) {
                names.remove();
            }
        }
        held.putAll(logged);
    }
}
