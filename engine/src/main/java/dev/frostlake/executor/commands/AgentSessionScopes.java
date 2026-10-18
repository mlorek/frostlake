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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.SecurityPropertySpecs;

/**
 * The Restricted Session Scope a session policy's AGENT_RESTRICTED_SESSION_SCOPE names, judged as the account judges
 * it once the statement's other values are (all live-verified):
 *
 * <pre>
 *   'SNOWFLAKE$DATA_READ', snowflake$data_read     a predefined scope, in any case, kept as written
 *   'db.schema.name'                                a fully qualified name: no such scope is modelled, so
 *                                                   Restricted Session Scope 'db.schema.name' does not exist or not
 *                                                   authorized.
 *   'x', nosuch_word, 'schema.name', ' SNOWFLAKE$DATA_READ'
 *                                                   'x' is not a valid RESTRICTED_SESSION_SCOPE value. Expected …
 * </pre>
 *
 * A word is named as it resolves, upper-cased unless it is quoted.
 */
final class AgentSessionScopes {

    private AgentSessionScopes() {
    }

    /**
     * Refuse a scope that names no scope there is.
     *
     * @param written the scope as written, a word as it resolves
     */
    static void require(final String written) {
        for (final String predefined : SecurityPropertySpecs.PREDEFINED_SESSION_SCOPES) {
            if (predefined.equalsIgnoreCase(written)) {
                return;
            }
        }
        if (isQualifiedName(written)) {
            throw new RuntimeException(SqlCompilationError.of("Restricted Session Scope '" + written
                + "' does not exist or not authorized."));
        }
        throw new RuntimeException(SqlCompilationError.PREFIX + " '" + written
            + "' is not a valid RESTRICTED_SESSION_SCOPE value. Expected inline YAML, a fully-qualified name"
            + " (db.schema.name), or one of the predefined names: "
            + String.join(", ", SecurityPropertySpecs.PREDEFINED_SESSION_SCOPES) + ".");
    }

    /** Whether a text is a name of three parts, each a plain word or a quoted one. */
    private static boolean isQualifiedName(final String text) {
        int parts = 0;
        int at = 0;
        while (at <= text.length()) {
            final int end = partEnd(text, at);
            if (end < 0) {
                return false;
            }
            parts++;
            if (end == text.length()) {
                return parts == 3;
            }
            if (text.charAt(end) != '.') {
                return false;
            }
            at = end + 1;
        }
        return false;
    }

    /** The index after the name part that starts at {@code at}, or -1 when no part starts there. */
    private static int partEnd(final String text, final int at) {
        if (at >= text.length()) {
            return -1;
        }
        if (text.charAt(at) == '"') {
            int i = at + 1;
            while (i < text.length()) {
                if (text.charAt(i) == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        i += 2;
                        continue;
                    }
                    return i == at + 1 ? -1 : i + 1;
                }
                i++;
            }
            return -1;
        }
        final char first = text.charAt(at);
        if (!Character.isLetter(first) && first != '_') {
            return -1;
        }
        int i = at + 1;
        while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_'
                || text.charAt(i) == '$')) {
            i++;
        }
        return i;
    }
}
