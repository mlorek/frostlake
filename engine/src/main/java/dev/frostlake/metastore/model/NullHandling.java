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
 * A UDF's NULL-handling behavior: {@link #CALLED_ON_NULL_INPUT} (the default — the body runs even for NULL
 * arguments) or {@link #RETURNS_NULL_ON_NULL_INPUT} (short-circuits to NULL; {@code STRICT} is its synonym).
 * {@link #getSqlText} is the canonical DDL/SHOW spelling (with spaces, not the enum's underscores).
 */
public enum NullHandling {
    CALLED_ON_NULL_INPUT("CALLED ON NULL INPUT"),
    RETURNS_NULL_ON_NULL_INPUT("RETURNS NULL ON NULL INPUT");

    private final String sqlText;

    NullHandling(final String sqlText) {
        this.sqlText = sqlText;
    }

    /** The canonical SQL spelling used in DDL and SHOW output (e.g. {@code "CALLED ON NULL INPUT"}). */
    public String getSqlText() {
        return sqlText;
    }

    /** Parse a clause value case-insensitively ({@code STRICT} is a synonym); null/unrecognized → the default. */
    public static NullHandling fromString(final String value) {
        if (value != null) {
            final String v = value.trim().toUpperCase();
            if (v.equals("RETURNS NULL ON NULL INPUT") || v.equals("STRICT")) {
                return RETURNS_NULL_ON_NULL_INPUT;
            }
        }
        return CALLED_ON_NULL_INPUT;
    }
}
