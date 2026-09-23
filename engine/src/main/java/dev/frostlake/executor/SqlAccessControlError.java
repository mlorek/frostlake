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
     * How the second sentence names a kind: the kind a view or a schema policy is granted on, words spaced. A
     * network policy, an account object, keeps its own two words.
     */
    private static String grantedOnKind(final String kind) {
        if ("VIEW".equals(kind)) {
            return "TABLE";
        }
        if (kind.endsWith("_POLICY") && !"NETWORK_POLICY".equals(kind)) {
            return "POLICY";
        }
        return kind.replace('_', ' ');
    }
}
