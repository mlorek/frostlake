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

package dev.frostlake.jdbc;

import java.util.Locale;

/** The temporal family of a result column, which decides how the driver's temporal getters read its cells. */
enum TemporalColumnKind {

    /** A DATE. */
    DATE,

    /** A TIME. */
    TIME,

    /** A TIMESTAMP_NTZ: a wall clock of no zone. */
    NTZ,

    /** A TIMESTAMP_LTZ: a moment, shown in the session's zone. */
    LTZ,

    /** A TIMESTAMP_TZ: a moment with an offset of its own. */
    TZ;

    /**
     * The family a column's type name names.
     *
     * @param typeName the column's type name, as the result's metadata names it — {@code TIMESTAMPLTZ} or {@code
     *                 TIMESTAMP_LTZ} alike
     * @return the family, or null for a column that is not temporal
     */
    static TemporalColumnKind of(final String typeName) {
        if (typeName == null) {
            return null;
        }
        final String name = typeName.toUpperCase(Locale.ROOT).replace("_", "");
        if ("DATE".equals(name)) {
            return DATE;
        }
        if ("TIME".equals(name)) {
            return TIME;
        }
        if (name.startsWith("TIMESTAMPLTZ")) {
            return LTZ;
        }
        if (name.startsWith("TIMESTAMPTZ")) {
            return TZ;
        }
        return name.startsWith("TIMESTAMP") || name.startsWith("DATETIME") ? NTZ : null;
    }
}
