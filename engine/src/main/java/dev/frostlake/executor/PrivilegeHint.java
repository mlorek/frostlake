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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The sentence live ends a missing-object refusal with: the privilege the statement's role would need to see the
 * object.
 *
 * <pre>
 * Object 'T' does not exist or not authorized.
 *     Your primary role R must have at least one privilege granted on TABLE T.
 * Schema 'D.S' does not exist or not authorized.
 *     Your primary role R must have USAGE or any other privilege granted on SCHEMA D.S.
 * User 'U' does not exist or not authorized.
 *     Your primary role R must have USAGE granted on ACCOUNT A.
 * </pre>
 *
 * <p>Each hint follows its first sentence on the same line, after one space. The object is named exactly as the
 * first sentence names it: qualified when that is qualified, bare when that is bare, its quotes kept. What is asked
 * for follows the first sentence's kind noun (live-verified kind by kind):
 * <ul>
 *   <li>by default "at least one privilege" on the noun upper-cased — STAGE, STREAM, TASK, FUNCTION, WAREHOUSE,
 *       FILE FORMAT, NETWORK POLICY, CORTEX SEARCH SERVICE, …;</li>
 *   <li>every table-like relation (Object, Table, View, Dynamic table, Materialized view, External table, Event
 *       table, Iceberg table) asks on TABLE, every policy but a network policy on POLICY, a database role on ROLE,
 *       an image, artifact or git repository on STAGE, and an external volume on VOLUME;</li>
 *   <li>a database, an application and an application package ask for "USAGE or any other privilege" on
 *       DATABASE, and a schema for the same on SCHEMA;</li>
 *   <li>a user names no object at all, asking for USAGE on the ACCOUNT by its locator, and a network rule asks
 *       for MONITOR there — a GRANT, a REVOKE or a SHOW GRANTS ON one for MONITOR, then RESOLVE ALL;</li>
 *   <li>a column is no securable object and carries no hint, and neither does any other name that is none — a
 *       star's qualifier, a constraint, a stage path — whose refusal is worded without asking here at all.</li>
 * </ul>
 *
 * <p>Where several privileges would each do, every one is its own sentence with the subject repeated. The subject
 * is "Your primary role R", or inside a procedure running with owner's rights "This executable runs with owner's
 * rights. The owner role R", where R is the session's primary role either way, not the procedure's owner (see
 * {@link SessionRole}). Live has a longer primary form when secondary roles are in force ("Your primary role R or
 * one of your secondary roles must have …"); the engine models no secondary roles
 * — {@code CURRENT_SECONDARY_ROLES()} answers none — so it speaks the short form.
 */
public final class PrivilegeHint {

    /** The kinds a privilege on TABLE covers. */
    private static final Set<String> TABLE_KINDS = new HashSet<String>(Arrays.asList("Object", "Table", "View",
        "Dynamic table", "Materialized view", "External table", "Event table", "Iceberg table"));

    /** The kinds that live in a stage, and are granted on as one. */
    private static final Set<String> STAGE_KINDS = new HashSet<String>(Arrays.asList("Image repository",
        "Artifact Repository", "Git repository"));

    /** The kinds that are databases underneath, and ask for USAGE on one. */
    private static final Set<String> DATABASE_KINDS = new HashSet<String>(Arrays.asList("Database", "Application",
        "Application package"));

    /**
     * The account privileges any one of which would let a role DESCRIBE a network rule, in the order live lists
     * them. A statement that grants on a missing rule asks for {@link #NETWORK_RULE_GRANTS}, and every other
     * statement over one for MONITOR alone.
     */
    private static final List<String> NETWORK_RULE_READERS = Collections.unmodifiableList(Arrays.asList(
        "APPLY AGGREGATION POLICY", "APPLY DATA MOVEMENT POLICY", "APPLY PRIVACY POLICY", "APPLY JOIN POLICY",
        "APPLY MASKING POLICY", "APPLY MULTI PARTY APPROVAL POLICY", "APPLY FEATURE POLICY",
        "APPLY PROJECTION POLICY", "APPLY ROW ACCESS POLICY", "APPLY STORAGE LIFECYCLE POLICY",
        "APPLY TOKENIZATION POLICY", "MONITOR", "RESOLVE ALL"));

    /**
     * The account privileges a GRANT, a REVOKE or a SHOW GRANTS ON over a missing network rule asks for, in the order
     * live lists them.
     */
    private static final List<String> NETWORK_RULE_GRANTS = Collections.unmodifiableList(Arrays.asList(
        "MONITOR", "RESOLVE ALL"));

    private PrivilegeHint() {
    }

    /**
     * The hint for the running statement's refusal, or the empty string when there is none to give — a kind that
     * carries no hint, or no statement pinned a role.
     *
     * @param kind the refusal's kind noun, as its first sentence spells it
     * @param spelled the object's name, as its first sentence spells it between the quotes
     * @return the hint with its leading space, or ""
     */
    public static String of(final String kind, final String spelled) {
        final SessionRole pinned = SessionRole.pinned();
        return pinned == null ? "" : sentence(kind, spelled, pinned.role(), pinned.ownersRights(), pinned.account());
    }

    /**
     * The hint a DESCRIBE NETWORK RULE refusal ends with for the running statement: one sentence per account
     * privilege in {@link #NETWORK_RULE_READERS}.
     *
     * @return the hint with its leading space, or "" when no statement pinned a role
     */
    public static String toDescribeNetworkRule() {
        return onAccount(NETWORK_RULE_READERS);
    }

    /**
     * The hint a GRANT, a REVOKE or a SHOW GRANTS ON refusal over a missing network rule ends with for the running
     * statement: one sentence per account privilege in {@link #NETWORK_RULE_GRANTS}.
     *
     * @return the hint with its leading space, or "" when no statement pinned a role
     */
    public static String toGrantOnNetworkRule() {
        return onAccount(NETWORK_RULE_GRANTS);
    }

    /** One sentence per account privilege, in order, addressed to the running statement's role; "" with none pinned. */
    private static String onAccount(final List<String> privileges) {
        final SessionRole pinned = SessionRole.pinned();
        if (pinned == null || pinned.role() == null || pinned.account() == null) {
            return "";
        }
        final StringBuilder hint = new StringBuilder();
        for (final String privilege : privileges) {
            hint.append(' ').append(subject(pinned.role(), pinned.ownersRights())).append(" must have ")
                .append(privilege).append(" granted on ACCOUNT ").append(pinned.account()).append('.');
        }
        return hint.toString();
    }

    /**
     * The hint for a refusal of one kind, addressed to the given role — {@link #of} with its subject spelled out.
     *
     * @param kind the refusal's kind noun, as its first sentence spells it
     * @param spelled the object's name, as its first sentence spells it between the quotes
     * @param role the role addressed; null gives no hint
     * @param ownersRights whether the refusal comes from inside a procedure running with owner's rights
     * @param account the account locator, as {@code CURRENT_ACCOUNT()} answers it
     * @return the hint with its leading space, or "" when the kind carries none
     */
    private static String sentence(final String kind, final String spelled, final String role,
                                   final boolean ownersRights, final String account) {
        final String wanted = privilege(kind, spelled, account);
        if (wanted == null || role == null) {
            return "";
        }
        return " " + subject(role, ownersRights) + " must have " + wanted + ".";
    }

    private static String subject(final String role, final boolean ownersRights) {
        return ownersRights ? "This executable runs with owner's rights. The owner role " + role
            : "Your primary role " + role;
    }

    /** What the role must have, with what it would be granted on; null when the kind carries no hint. */
    private static String privilege(final String kind, final String spelled, final String account) {
        if ("Column".equals(kind)) {
            return null;
        }
        if ("User".equals(kind)) {
            return account == null ? null : "USAGE granted on ACCOUNT " + account;
        }
        if ("Network rule".equals(kind)) {
            return account == null ? null : "MONITOR granted on ACCOUNT " + account;
        }
        if ("Schema".equals(kind)) {
            return "USAGE or any other privilege granted on SCHEMA " + spelled;
        }
        if (DATABASE_KINDS.contains(kind)) {
            return "USAGE or any other privilege granted on DATABASE " + spelled;
        }
        return "at least one privilege granted on " + securable(kind) + " " + spelled;
    }

    /** The securable kind a privilege on an object of this kind is granted on. */
    private static String securable(final String kind) {
        if (TABLE_KINDS.contains(kind)) {
            return "TABLE";
        }
        if (STAGE_KINDS.contains(kind)) {
            return "STAGE";
        }
        if ("Database role".equals(kind)) {
            return "ROLE";
        }
        if ("External volume".equals(kind)) {
            return "VOLUME";
        }
        if ("Policy".equals(kind) || (kind.endsWith(" policy") && !"Network policy".equals(kind))) {
            return "POLICY";
        }
        return kind.toUpperCase(Locale.ROOT);
    }
}
