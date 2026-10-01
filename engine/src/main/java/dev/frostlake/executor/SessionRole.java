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

package dev.frostlake.executor;

import dev.frostlake.security.SessionContext;

/**
 * The role a running statement's refusals address, reachable from the static helpers that word them.
 *
 * <p>A missing-object refusal ends with a sentence naming the role that would need a privilege on the object
 * ({@link PrivilegeHint}), and that sentence is built deep inside {@link SqlCompilationError}, which has no
 * session to ask. So the session is pinned here for the statement, exactly as {@link SessionZone} pins the
 * zone and for the same reason: one engine serves many sessions, so this must not be a process-wide setting.
 *
 * <p>The role is the session's primary role, read from the session when the sentence is built, not when the
 * statement starts, so a {@code USE ROLE} inside a block is heard by the statements after it. Inside a stored
 * procedure that runs with OWNER's rights the sentence is worded for the owner ("This executable runs with
 * owner's rights. The owner role R must have …"), yet R is still the session's primary role, not the role that
 * owns the procedure (live-verified: a SYSADMIN session calling a procedure ACCOUNTADMIN owns is told "The owner
 * role SYSADMIN"). A caller's-rights procedure changes nothing, so one called from inside an owner's-rights
 * procedure keeps the owner's wording.
 */
public final class SessionRole {

    private static final ThreadLocal<SessionRole> PINNED = new ThreadLocal<SessionRole>();

    private final SessionContext session;
    private final String account;
    private final boolean ownersRights;

    private SessionRole(final SessionContext session, final String account, final boolean ownersRights) {
        this.session = session;
        this.account = account;
        this.ownersRights = ownersRights;
    }

    /**
     * What is pinned now, so a caller that re-pins can put it back with {@link #restore}.
     *
     * @return the pinned value, or null when no statement pinned one
     */
    public static SessionRole pinned() {
        return PINNED.get();
    }

    /**
     * Pin a statement's session and the account it runs in, returning what was pinned before.
     *
     * @param session the session whose current role the statement uses; null pins nothing
     * @param account the account locator, as {@code CURRENT_ACCOUNT()} answers it
     * @return the value displaced, for {@link #restore}
     */
    public static SessionRole pin(final SessionContext session, final String account) {
        final SessionRole previous = PINNED.get();
        if (session == null) {
            PINNED.remove();
        } else {
            PINNED.set(new SessionRole(session, account, false));
        }
        return previous;
    }

    /**
     * Enter a stored procedure's body. One that runs with owner's rights has a refusal worded for the owner until
     * the caller restores what {@link #pinned} returned before the call; one that runs with the caller's rights
     * leaves the pin as it is.
     *
     * @param ownersRights whether the procedure runs with its owner's rights
     */
    public static void enterProcedure(final boolean ownersRights) {
        final SessionRole current = PINNED.get();
        if (ownersRights && current != null && !current.ownersRights) {
            PINNED.set(new SessionRole(current.session, current.account, true));
        }
    }

    /**
     * Put back what a pin displaced; a null restores the unpinned state.
     *
     * @param previous what {@link #pin} returned, or what {@link #pinned} read
     */
    public static void restore(final SessionRole previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    /**
     * The role a refusal addresses: the session's primary role, inside an owner's-rights procedure too.
     *
     * @return the role's name, or null when the session has none
     */
    String role() {
        return session.getCurrentRole();
    }

    /** Whether the statement runs inside a procedure with its owner's rights, which words the refusal for it. */
    boolean ownersRights() {
        return ownersRights;
    }

    /** The account locator, as {@code CURRENT_ACCOUNT()} answers it; null when none is configured. */
    String account() {
        return account;
    }
}
