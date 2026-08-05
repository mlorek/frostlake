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

/**
 * The optional field modifier of {@code CAST(expr AS <structured type> [RENAME FIELDS | ADD FIELDS])}.
 *
 * <p>Live-verified: {@code RENAME FIELDS} maps the source's fields POSITIONALLY onto the
 * target's field names (counts must match), {@code ADD FIELDS} matches BY NAME and fills the fields
 * only the target declares with NULL (the target must list every source field). Both require a
 * STRUCTURED source and a STRUCTURED target.
 */
public enum CastFieldsModifier {

    /** No modifier — a plain {@code CAST(expr AS type)}. */
    NONE(""),

    /** {@code RENAME FIELDS} — positional re-labelling of the source's fields. */
    RENAME("RENAME FIELDS"),

    /** {@code ADD FIELDS} — by-name carry-over, new fields default to NULL. */
    ADD("ADD FIELDS");

    private final String sql;

    CastFieldsModifier(final String sql) {
        this.sql = sql;
    }

    /** The modifier as written in SQL, e.g. {@code RENAME FIELDS}. */
    public String getSql() {
        return sql;
    }
}
