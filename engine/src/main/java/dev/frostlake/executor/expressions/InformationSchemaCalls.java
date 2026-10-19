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
package dev.frostlake.executor.expressions;

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.SystemFunctionNames;

import java.util.List;
import java.util.Locale;

/**
 * A built-in function named under INFORMATION_SCHEMA — {@code INFORMATION_SCHEMA.UPPER('a')}, under any database's,
 * {@code SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$TYPEOF(1)} — is the built-in itself: it runs, a CALL of it too, and its
 * refusals name it bare, {@code too many arguments for function [UPPER('a', 'b')]}; a database written before the
 * schema is looked up, {@code Object 'NOSUCH_DB' does not exist or not authorized.} A name no built-in has is no
 * user's function there: {@code Unknown function INFORMATION_SCHEMA.NOSUCH.}, listed with the bare unknown names
 * (all live-verified).
 */
public final class InformationSchemaCalls {

    private static final String SCHEMA = "INFORMATION_SCHEMA";

    private InformationSchemaCalls() {
    }

    /**
     * The built-in a call's name reaches under INFORMATION_SCHEMA.
     *
     * @param parts the name's canonical parts, as written
     * @return the built-in's name, its last part, or null where the name is not one under INFORMATION_SCHEMA
     */
    public static String builtIn(final List<String> parts) {
        if (!isUnder(parts)) {
            return null;
        }
        final String name = parts.get(parts.size() - 1);
        final String upper = name.toUpperCase(Locale.ROOT);
        return SystemFunctionNames.contains(upper) || FunctionRegistry.isBuiltInName(upper) ? name : null;
    }

    /**
     * Whether a routine's name is written under INFORMATION_SCHEMA, with or without a database.
     *
     * @param parts the name's canonical parts, or null
     * @return whether the schema part is INFORMATION_SCHEMA
     */
    public static boolean isUnder(final List<String> parts) {
        return parts != null && (parts.size() == 2 || parts.size() == 3) && SCHEMA.equals(parts.get(parts.size() - 2));
    }

    /**
     * The database a name under INFORMATION_SCHEMA is written with.
     *
     * @param parts the name's canonical parts
     * @return the database's canonical name, or null for a name of two parts
     */
    public static String database(final List<String> parts) {
        return parts != null && parts.size() == 3 ? parts.get(0) : null;
    }
}
