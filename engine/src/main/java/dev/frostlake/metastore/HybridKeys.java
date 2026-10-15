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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Table;

/**
 * The keys a hybrid table holds to (documented, and the primary-key rule live-verified): it must declare a PRIMARY
 * KEY, and its PRIMARY KEY, UNIQUE and FOREIGN KEY constraints are enforced row by row, each refusal naming the
 * index the constraint builds — {@code SYS_INDEX_<table>_UNIQUE_<column>}, {@code
 * SYS_INDEX_<table>_FOREIGN_KEY_<columns>_<parent>_<parent columns>}.
 */
public final class HybridKeys {

    private HybridKeys() {
    }

    /** Refuse a hybrid table declared without a PRIMARY KEY: {@code A hybrid table must contain primary key.} */
    public static void requirePrimaryKey(final Table table) {
        if (table.isHybrid() && table.getPrimaryKeys().isEmpty()) {
            throw new RuntimeException("A hybrid table must contain primary key.");
        }
    }

    /** The refusal of a row whose primary key a row of the table already holds. */
    public static String primaryKeyExists() {
        return "Primary key already exists";
    }

    /** The refusal of a row whose UNIQUE column repeats a value the table holds. */
    public static String uniqueViolated(final Table table, final String column) {
        return "Duplicate key value violates unique constraint \"SYS_INDEX_" + table.getName() + "_UNIQUE_" + column
            + "\"";
    }

    /** The refusal of a row whose foreign key no parent row holds, a NULL one included. */
    public static String foreignKeyViolated(final Table table, final ForeignKeyConstraint key) {
        return "Foreign key constraint \"SYS_INDEX_" + table.getName() + "_FOREIGN_KEY_"
            + String.join("_", key.getColumnNames()) + "_" + QualifiedName.parse(key.getReferencedTable()).last()
            + "_" + String.join("_", key.getReferencedColumns()) + "\" was violated.";
    }
}
