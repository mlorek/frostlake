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
 * {@code invalid parameter '<NAME>'} for SET and UNSET alike, an object property answers
 * {@code invalid property '<NAME>' for '<KIND>'}. The registry is the ACCOUNT's list, not the
 * engine's implemented subset, so a real parameter the engine merely ignores is never refused;
 * names are spelled as written — unquoted upper-folds, quoted keeps its quotes.
 */
final class ParameterRegistry {

    private static final Set<String> SESSION = load("session");
    private static final Set<String> TABLE = load("table");
    private static final Set<String> DATABASE = load("database");
    private static final Set<String> SCHEMA = load("schema");

    static {
        // ALTER TABLE SET also takes PROPERTIES that are not SHOW PARAMETERS entries — the staged
        // COPY defaults are the ones the generic-key path can reach.
        TABLE.add("STAGE_COPY_OPTIONS");
        TABLE.add("STAGE_FILE_FORMAT");
        // Table PROPERTIES the account's ALTER TABLE … SET takes that are likewise not parameters
        // (live-verified: both SET and UNSET are accepted).
        TABLE.add("ENABLE_SCHEMA_EVOLUTION");
        TABLE.add("ERROR_LOGGING");
    }

    private ParameterRegistry() {
    }

    static boolean isSessionParameter(final String canonicalName) {
        return SESSION.contains(canonicalName);
    }

    static boolean isTableParameter(final String canonicalName) {
        return TABLE.contains(canonicalName);
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

    /** The name as the refusal spells it: quoted verbatim (quotes kept), unquoted upper-folded. */
    static String spell(final String rawTokenText) {
        return rawTokenText.startsWith("\"") ? rawTokenText : rawTokenText.toUpperCase(Locale.ROOT);
    }

    /** The lookup key: a quoted name's exact content, an unquoted name upper-folded. */
    static String canonical(final String rawTokenText) {
        if (rawTokenText.length() >= 2 && rawTokenText.startsWith("\"") && rawTokenText.endsWith("\"")) {
            return rawTokenText.substring(1, rawTokenText.length() - 1).replace("\"\"", "\"");
        }
        return rawTokenText.toUpperCase(Locale.ROOT);
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
