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

package dev.frostlake;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * An expected missing-object refusal, completed with the privilege hint live ends it with.
 *
 * <p>A "does not exist or not authorized." sentence about a securable object is followed by one naming the privilege
 * the statement's role would need on it, and that role differs by side: the embedded session runs as the engine's
 * default role, a live one as the account's. An expectation spelled with a fixed role could hold on one side only,
 * so a test keeps writing the first sentence and this builds the hint from it — its kind noun and its quoted name —
 * for the role the session reports.
 *
 * <p>What each kind asks for is written out here from live's answers, kind by kind, and deliberately not taken from
 * the engine's own table, so an expectation built here still catches the engine asking for the wrong privilege or
 * on the wrong kind of object.
 */
public final class MissingObjectHint {

    private static final String MARKER = "' does not exist or not authorized.";

    /** The kinds whose hint asks for any privilege on a TABLE. */
    private static final Set<String> ON_TABLE = new HashSet<String>(Arrays.asList("Object", "Table", "View",
        "Dynamic table", "Materialized view", "External table", "Event table", "Iceberg table"));

    /** The kinds whose hint asks for any privilege on a POLICY. */
    private static final Set<String> ON_POLICY = new HashSet<String>(Arrays.asList("Policy", "Masking policy",
        "Row access policy", "Aggregation policy", "Projection policy", "Password policy", "Join policy",
        "Session policy", "Packages policy", "Authentication policy", "Privacy policy", "Storage lifecycle policy"));

    /** The kinds whose hint asks for any privilege on a STAGE. */
    private static final Set<String> ON_STAGE = new HashSet<String>(Arrays.asList("Stage", "Image repository",
        "Artifact Repository", "Git repository"));

    /** The kinds whose hint asks for USAGE or any other privilege on a DATABASE. */
    private static final Set<String> ON_DATABASE = new HashSet<String>(Arrays.asList("Database", "Application",
        "Application package"));

    private MissingObjectHint() {
    }

    /**
     * The expected text with the hint after every missing-object sentence in it.
     *
     * @param expected the refusal as a test spells it, in whatever layout its assertion compares
     * @param role the role the hint addresses
     * @param ownersRights whether the refusal comes from inside a procedure running with its owner's rights
     * @param account the account locator, as {@code CURRENT_ACCOUNT()} answers it
     * @return the completed text
     */
    public static String of(final String expected, final String role, final boolean ownersRights,
                            final String account) {
        final StringBuilder completed = new StringBuilder();
        int copied = 0;
        int marker = expected.indexOf(MARKER);
        while (marker >= 0) {
            final int end = marker + MARKER.length();
            // The name sits between the quote after the kind noun and the marker; the kind noun is the run of
            // words before that quote, up to a separator — an escaped one too, as a layout test spells "\n".
            final int open = expected.lastIndexOf(" '", marker);
            int start = open;
            while (start > 0 && (Character.isLetter(expected.charAt(start - 1)) || expected.charAt(start - 1) == ' ')
                    && !(start > 1 && expected.charAt(start - 2) == '\\')) {
                start--;
            }
            final String kind = expected.substring(start, open).trim();
            final String name = expected.substring(open + 2, marker);
            completed.append(expected, copied, end);
            final String wanted = wanted(kind, name, account);
            if (wanted != null) {
                completed.append(ownersRights ? " This executable runs with owner's rights. The owner role "
                    : " Your primary role ").append(role).append(" must have ").append(wanted).append('.');
            }
            copied = end;
            marker = expected.indexOf(MARKER, end);
        }
        return completed.append(expected.substring(copied)).toString();
    }

    /** What a role must have to see a missing object of this kind, or null when the kind carries no hint. */
    private static String wanted(final String kind, final String name, final String account) {
        if ("Column".equals(kind)) {
            return null;
        }
        if ("User".equals(kind)) {
            return "USAGE granted on ACCOUNT " + account;
        }
        if ("Network rule".equals(kind)) {
            return "MONITOR granted on ACCOUNT " + account;
        }
        if ("Schema".equals(kind)) {
            return "USAGE or any other privilege granted on SCHEMA " + name;
        }
        if (ON_DATABASE.contains(kind)) {
            return "USAGE or any other privilege granted on DATABASE " + name;
        }
        final String securable;
        if (ON_TABLE.contains(kind)) {
            securable = "TABLE";
        } else if (ON_POLICY.contains(kind)) {
            securable = "POLICY";
        } else if (ON_STAGE.contains(kind)) {
            securable = "STAGE";
        } else if ("Database role".equals(kind)) {
            securable = "ROLE";
        } else if ("External volume".equals(kind)) {
            securable = "VOLUME";
        } else {
            securable = kind.toUpperCase(Locale.ROOT);
        }
        return "at least one privilege granted on " + securable + " " + name;
    }
}
