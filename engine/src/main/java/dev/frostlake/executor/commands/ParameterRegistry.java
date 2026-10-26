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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The parameter names a real account knows, per scope — captured from the account's own
 * {@code SHOW PARAMETERS} listings (resources under {@code dev/frostlake/parameters/}) — and the
 * refusals an unknown name earns (live-verified): a session name answers
 * {@code invalid parameter '<name>'} for SET and UNSET alike, the name exactly as written
 * ({@code nosuch} stays lower-case), an object property answers
 * {@code invalid property '<name>' for '<KIND>'}, the name as written too ({@code foo}, {@code Foo}, and a
 * quoted one with its quotes). The registry is the ACCOUNT's list, not the engine's implemented subset, so a
 * real parameter the engine merely ignores is never refused. A few parameters a table takes no value of are
 * refused at a table's level by name (see {@link #isTableUnsettable}).
 */
final class ParameterRegistry {

    private static final Set<String> SESSION = load("session");
    private static final Set<String> TABLE = load("table");
    private static final Set<String> DATABASE = load("database");
    private static final Set<String> SCHEMA = load("schema");
    private static final Set<String> TABLE_UNSETTABLE = load("table_unsettable");
    private static final Set<String> ALERT = load("alert");
    private static final Set<String> ALERT_UNSETTABLE = load("alert_unsettable");

    /**
     * Every name the parameter list of some level holds, the account's aside: a database's, a warehouse's or a
     * session's parameter set on an object of another level is refused for want of access, as an account parameter
     * is (live-verified), and a user's or a task's is read the same way.
     */
    private static final Set<String> SOME_LEVEL = someLevel();

    static {
        // ALTER TABLE SET also takes PROPERTIES that are not SHOW PARAMETERS entries — the staged
        // COPY defaults are the ones the generic-key path can reach.
        TABLE.add("STAGE_COPY_OPTIONS");
        TABLE.add("STAGE_FILE_FORMAT");
        // Table PROPERTIES the account's ALTER TABLE … SET takes that are likewise not parameters
        // (live-verified: both SET and UNSET are accepted).
        TABLE.add("ENABLE_SCHEMA_EVOLUTION");
        TABLE.add("ERROR_LOGGING");
        TABLE.add("ROW_TIMESTAMP");
    }

    private ParameterRegistry() {
    }

    static boolean isSessionParameter(final String canonicalName) {
        return SESSION.contains(canonicalName);
    }

    static boolean isTableParameter(final String canonicalName) {
        return TABLE.contains(canonicalName);
    }

    /**
     * Whether a parameter is one a table refuses at its own level rather than for want of access: the task, alert,
     * logging and pipe ones and a few defaults, {@code cannot set parameter 'LOG_LEVEL' at 'TABLE'} (live-verified).
     *
     * @param canonicalName the parameter's name, canonical
     * @return whether a table refuses it so
     */
    static boolean isTableUnsettable(final String canonicalName) {
        return TABLE_UNSETTABLE.contains(canonicalName);
    }

    /** Whether ALTER ALERT … SET takes a parameter: a session parameter, or an alert's own (live-verified). */
    static boolean isAlertParameter(final String canonicalName) {
        return ALERT.contains(canonicalName);
    }

    /**
     * Whether a level takes no value of a parameter at all: AUTOCOMMIT_API_SUPPORTED, kept for the account's own use,
     * refuses at a session, a task and an alert whatever a SET gives it, TRUE and FALSE included, {@code invalid value
     * [true] for parameter 'AUTOCOMMIT_API_SUPPORTED'}, while UNSET is taken (live-verified).
     *
     * @param level         SESSION, TASK or ALERT
     * @param canonicalName the parameter's name, canonical
     * @return whether the level refuses every value of it
     */
    static boolean takesNoValue(final String level, final String canonicalName) {
        return "AUTOCOMMIT_API_SUPPORTED".equals(canonicalName)
            && ("SESSION".equals(level) || "TASK".equals(level) || "ALERT".equals(level));
    }

    /**
     * Whether an object of a kind refuses a parameter at its own level, {@code cannot set parameter 'X' at 'TABLE'},
     * rather than for want of access (live-verified for tables and alerts).
     *
     * @param kind          the object's kind, as the refusal names it
     * @param canonicalName the parameter's name, canonical
     * @return whether the object refuses it so
     */
    static boolean isUnsettable(final String kind, final String canonicalName) {
        return "TABLE".equals(kind) ? TABLE_UNSETTABLE.contains(canonicalName)
            : "ALERT".equals(kind) && ALERT_UNSETTABLE.contains(canonicalName);
    }

    /**
     * Whether a name is a parameter in the list of some level (see {@link #SOME_LEVEL}).
     *
     * @param canonicalName the name, canonical
     * @return whether some level's list holds it
     */
    static boolean isSomeLevelParameter(final String canonicalName) {
        return SOME_LEVEL.contains(canonicalName);
    }

    static boolean isDatabaseParameter(final String canonicalName) {
        return DATABASE.contains(canonicalName);
    }

    static boolean isSchemaParameter(final String canonicalName) {
        return SCHEMA.contains(canonicalName);
    }

    static RuntimeException invalidSessionParameter(final String spelledName) {
        return new RuntimeException(SqlCompilationError.of("invalid parameter '" + spelledName + "'"));
    }

    static RuntimeException invalidProperty(final String spelledName, final String objectKind) {
        return new RuntimeException(SqlCompilationError.of(
            "invalid property '" + spelledName + "' for '" + objectKind + "'"));
    }

    /** The name as the refusal spells it: as written, a quoted name with its quotes (live-verified). */
    static String spell(final String rawTokenText) {
        return rawTokenText;
    }

    /** The lookup key: a quoted name's exact content, an unquoted name upper-folded. */
    static String canonical(final String rawTokenText) {
        if (rawTokenText.length() >= 2 && rawTokenText.startsWith("\"") && rawTokenText.endsWith("\"")) {
            return rawTokenText.substring(1, rawTokenText.length() - 1).replace("\"\"", "\"");
        }
        return rawTokenText.toUpperCase(Locale.ROOT);
    }

    private static Set<String> someLevel() {
        final Set<String> names = new HashSet<String>();
        for (final String scope : new String[] {"session", "database", "schema", "warehouse", "user", "task"}) {
            names.addAll(load(scope));
        }
        return names;
    }

    private static Set<String> load(final String scope) {
        final Set<String> names = new HashSet<String>();
        try (InputStream in = ParameterRegistry.class.getResourceAsStream(
                "/dev/frostlake/parameters/" + scope + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("missing parameter list: " + scope);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        names.add(trimmed);
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load parameter list: " + scope, e);
        }
        return names;
    }
}
