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
 * A foreign-key referential action ({@code ON DELETE} / {@code ON UPDATE}). Informational only — the engine
 * records but does not enforce these. {@link #getSqlText} is the canonical DDL spelling (with spaces, e.g.
 * {@code "NO ACTION"}, not the enum's underscores).
 */
public enum ReferentialAction {
    NO_ACTION("NO ACTION"),
    RESTRICT("RESTRICT"),
    CASCADE("CASCADE"),
    SET_NULL("SET NULL"),
    SET_DEFAULT("SET DEFAULT");

    private final String sqlText;

    ReferentialAction(final String sqlText) {
        this.sqlText = sqlText;
    }

    /** The canonical SQL spelling used in DDL and SHOW output (e.g. {@code "SET NULL"}). */
    public String getSqlText() {
        return sqlText;
    }

    /**
     * Parse a referential-action value case-insensitively; null/unrecognized → null (i.e. no action specified).
     * Spaces and underscores are ignored so both spellings are accepted — the CREATE path yields {@code "SET NULL"}
     * while the ALTER path's {@code referentialOption().getText()} yields {@code "SETNULL"}; both normalize here.
     */
    public static ReferentialAction fromString(final String value) {
        if (value != null) {
            switch (value.trim().toUpperCase().replace(" ", "").replace("_", "")) {
                case "NOACTION": return NO_ACTION;
                case "RESTRICT": return RESTRICT;
                case "CASCADE": return CASCADE;
                case "SETNULL": return SET_NULL;
                case "SETDEFAULT": return SET_DEFAULT;
                default: return null;
            }
        }
        return null;
    }
}
