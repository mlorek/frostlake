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

import java.util.Locale;

/**
 * How Snowflake lays out an access-control refusal: {@code "SQL access control error:"}, a
 * newline, then the detail — the same two-line layout {@link SqlCompilationError} documents for
 * compilation errors. Live-verified on a real account: dropping an INFORMATION_SCHEMA view (under
 * DROP VIEW or DROP TABLE alike — the object is found before any kind check) answers
 * {@code SQL access control error:\nInsufficient privileges to operate on view 'TABLES'.}.
 */
public final class SqlAccessControlError {

    /** The prefix, without the separator that follows it. */
    public static final String PREFIX = "SQL access control error:";

    private SqlAccessControlError() {
    }

    /**
     * The refusal of a role the session's user may not activate: one that exists and was granted neither to the
     * user nor to a role the user holds, however far down (live-verified, two spaces after the first sentence
     * included).
     *
     * @param role the role named, in its canonical spelling
     * @return the sentence
     */
    public static String roleNotAssigned(final String role) {
        return PREFIX + "\nRequested role '" + role + "' is not assigned to the executing user.  Specify another"
            + " role to activate.";
    }

    /** The refusal for an operation the session may not perform on an object it can see. */
    public static String insufficientPrivileges(final String objectKind, final String objectName) {
        return PREFIX + "\nInsufficient privileges to operate on " + objectKind
            + " '" + objectName + "'.";
    }

    /**
     * The refusal for replacing, dropping, renaming or swapping an object the session does not own, in the form
     * a session without secondary roles is answered in, which is the only kind this engine models. The
     * first sentence names the kind in lower case with its words joined by underscores ({@code file_format},
     * {@code masking_policy}); the second names the kind OWNERSHIP is granted on, which for a view is TABLE
     * and for a schema's policy is POLICY, then the object in full — a routine with its argument types.
     *
     * @param kind        the object's kind as {@link GrantedObject#getKind()} reports it
     * @param name        the object's own name
     * @param primaryRole the session's primary role
     * @param fullName    the object's name in full
     */
    public static String ownershipRequired(final String kind, final String name, final String primaryRole,
                                           final String fullName) {
        return insufficientPrivileges(kind.toLowerCase(Locale.ROOT), name) + " Your primary role " + primaryRole
            + " must have OWNERSHIP granted on " + grantedOnKind(kind) + " " + fullName + ".";
    }

    /**
     * The refusal for creating an object in a schema or a database where the primary role holds neither the
     * container nor the CREATE privilege for the object's kind: {@code Insufficient privileges to operate on
     * schema 'S'. Your primary role R must have CREATE TABLE granted on SCHEMA D.S.}
     *
     * <p>Inside a stored procedure that runs with owner's rights the role asked is the procedure's owner, and the
     * sentence says so: {@code This executable runs with owner's rights. The owner role R must have …}
     * (live-verified).
     *
     * @param containerKind the container's kind, {@code SCHEMA} or {@code DATABASE}
     * @param name          the container's own name
     * @param role          the role asked: the session's primary role, or the owner of the procedure running
     * @param ownersRights  whether the role is the owner of a procedure running with owner's rights
     * @param privilege     the privilege's words, {@code CREATE FILE FORMAT}
     * @param fullName      the container's name in full
     */
    public static String createPrivilegeRequired(final String containerKind, final String name, final String role,
                                                 final boolean ownersRights, final String privilege,
                                                 final String fullName) {
        return insufficientPrivileges(containerKind.toLowerCase(Locale.ROOT), name)
            + (ownersRights ? " This executable runs with owner's rights. The owner role " : " Your primary role ")
            + role + " must have " + privilege + " granted on " + containerKind + " " + fullName + ".";
    }

    /**
     * The refusal for a statement that needs a privilege the session's roles do not hold on an object another
     * role owns: {@code Insufficient privileges to operate on table 'T'. Your primary role R must have SELECT
     * granted on TABLE D.S.T.} The first sentence names the kind in lower case with its words joined by
     * underscores; the second the kind the privilege is granted on — a view's and a materialized view's is
     * TABLE — and the object in full, a routine with its argument types (live-verified).
     *
     * @param kind        the object's kind as {@link GrantedObject#getKind()} reports it
     * @param name        the object's own name
     * @param primaryRole the session's primary role
     * @param secondary   whether the session asked for secondary roles, which the sentence then names beside it
     * @param privilege   the privilege's words, or null for "at least one privilege"
     * @param fullName    the object's name in full
     * @return the refusal
     */
    public static String privilegeRequired(final String kind, final String name, final String primaryRole,
                                           final boolean secondary, final String privilege,
                                           final String fullName) {
        return insufficientPrivileges(kind.toLowerCase(Locale.ROOT).replace(' ', '_'), name)
            + " Your primary role " + primaryRole + (secondary ? " or one of your secondary roles" : "")
            + " must have "
            + (privilege == null ? "at least one privilege" : privilege) + " granted on "
            + grantedOnKind(kind.replace(' ', '_')) + " " + fullName + ".";
    }

    /**
     * The refusal of the listing of the organization's accounts, which only the organization account answers: what the
     * session may not operate on is the account's own system, named with no kind, and what it lacks is the
     * organization's MANAGE ACCOUNTS on the current account (live-verified, the two spaces before the name included).
     *
     * @param primaryRole the session's primary role
     * @param secondary   whether the session asked for secondary roles
     * @param locator     the current account's locator
     * @return the refusal
     */
    public static String manageAccountsRequired(final String primaryRole, final boolean secondary,
                                                final String locator) {
        return insufficientPrivileges("", "SYSTEM") + " Your primary role " + primaryRole
            + (secondary ? " or one of your secondary roles" : "") + " must have MANAGE ACCOUNTS granted on ACCOUNT "
            + locator + ".";
    }

    /**
     * The refusal for a stream over a table whose change tracking is off, created by a role that may read the
     * table but not alter it: the stream would have to turn change tracking on (live-verified).
     *
     * @param name        the table's own name
     * @param primaryRole the session's primary role
     * @param secondary   whether the session asked for secondary roles
     * @param fullName    the table's name in full
     * @return the refusal
     */
    public static String changeTrackingRequired(final String name, final String primaryRole,
                                                final boolean secondary, final String fullName) {
        return insufficientPrivileges("stream source without CHANGE_TRACKING enabled", name)
            + " Your primary role " + primaryRole + (secondary ? " or one of your secondary roles" : "")
            + " must have MODIFY granted on TABLE " + fullName + ".";
    }

    /**
     * How the second sentence names a kind: the kind a view or a schema policy is granted on, words spaced. A
     * network policy, an account object, keeps its own two words.
     */
    private static String grantedOnKind(final String kind) {
        if ("VIEW".equals(kind) || "MATERIALIZED_VIEW".equals(kind)) {
            return "TABLE";
        }
        if (kind.endsWith("_POLICY") && !"NETWORK_POLICY".equals(kind)) {
            return "POLICY";
        }
        return kind.replace('_', ' ');
    }
}
