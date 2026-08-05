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

import java.util.UUID;

/**
 * Names for constraints declared without an explicit {@code CONSTRAINT <name>} clause. Snowflake names
 * such a constraint {@code SYS_CONSTRAINT_<uuid>} — e.g.
 * {@code SYS_CONSTRAINT_0a022251-e083-4354-a48d-a7cdffba5738} — for PRIMARY KEY, UNIQUE and FOREIGN KEY
 * alike (live-verified through {@code INFORMATION_SCHEMA.TABLE_CONSTRAINTS}). The name carries no
 * information about the table, the columns, or the constraint kind, so callers must never parse it; they
 * only need it to stay stable for the lifetime of the constraint, which is why every generated name is
 * memoized next to the constraint it names (see {@link Table} and {@link ForeignKeyConstraint}).
 */
public final class ConstraintNames {

    /** The prefix Snowflake gives every auto-generated constraint name. */
    public static final String PREFIX = "SYS_CONSTRAINT_";

    private ConstraintNames() {
    }

    /** A fresh auto-generated constraint name. */
    public static String generate() {
        return PREFIX + UUID.randomUUID();
    }
}
