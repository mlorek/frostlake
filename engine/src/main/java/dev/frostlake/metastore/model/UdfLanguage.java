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

package dev.frostlake.metastore.model;

/**
 * The implementation language (runtime) of a UDF or stored procedure, as declared by {@code LANGUAGE …}. {@code SQL}
 * is the default (inline SQL / Snowflake Scripting); the others select an embedded runtime. {@link #name} is the
 * canonical string used in SHOW output, the information schema, and persistence snapshots.
 */
public enum UdfLanguage {
    SQL, JAVASCRIPT, JAVA, PYTHON, SCALA;

    /**
     * Parse a {@code LANGUAGE} value case-insensitively. A null or unrecognized value maps to {@link #SQL} — the
     * grammar only ever produces the five constants, so this default is a defensive fallback, not a normal path.
     */
    public static UdfLanguage fromString(final String value) {
        if (value == null) {
            return SQL;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException e) {
            return SQL;
        }
    }
}
