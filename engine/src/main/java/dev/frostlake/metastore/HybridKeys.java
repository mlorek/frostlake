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

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;

import java.util.ArrayList;
import java.util.List;

/**
 * The keys a hybrid table holds to (documented, and the primary-key rule live-verified): it must declare a PRIMARY
 * KEY, and its PRIMARY KEY, UNIQUE and FOREIGN KEY constraints are enforced row by row, each refusal naming the
 * index the constraint builds — {@code SYS_INDEX_<table>_UNIQUE_<column>}, {@code
 * SYS_INDEX_<table>_FOREIGN_KEY_<columns>_<parent>_<parent columns>}.
 */
public final class HybridKeys {

    private HybridKeys() {
    }

    /**
     * Refuse a hybrid table with a DECFLOAT column, ahead of its keys: {@code This feature is not supported for hybrid
     * tables: 'DECFLOAT(38)'.} (live-verified).
     */
    public static void requireSupportedTypes(final Table table) {
        if (!table.isHybrid()) {
            return;
        }
        for (final TableColumn column : table.getColumns()) {
            if (NumericType.isDecfloat(column.getDataType())) {
                throw new RuntimeException("This feature is not supported for hybrid tables: '"
                    + SqlTypeNames.canonical(column.getDataType()) + "'.");
            }
        }
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

    /**
     * The refusal of a row whose UNIQUE key repeats one the table holds, the index named by the key's columns in
     * turn as a foreign key's is.
     */
    public static String uniqueViolated(final Table table, final UniqueConstraint key) {
        return uniqueViolated(table, String.join("_", key.getColumnNames()));
    }

    /**
     * The UNIQUE keys a hybrid table holds rows to: every UNIQUE constraint, a key over several columns held as one,
     * but not one over primary-key columns alone, which the primary key holds.
     */
    public static List<UniqueConstraint> uniqueKeys(final Table table) {
        final List<UniqueConstraint> keys = new ArrayList<>();
        for (final UniqueConstraint key : table.getUniqueConstraints()) {
            if (!table.getPrimaryKeys().containsAll(key.getColumnNames())) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * A row's values under a key's columns, compared as values are, or null when one of them is NULL: such a row
     * repeats no key and names no parent.
     */
    public static List<Object> keyOf(final Table table, final List<String> columns, final Row row) {
        final List<Object> values = new ArrayList<>();
        for (final String column : columns) {
            final int slot = table.getColumnIndex(column);
            final Object value = slot >= 0 && slot < row.getValues().size() ? row.getValue(slot) : null;
            if (value == null) {
                return null;
            }
            values.add(ValueComparisons.canonicalGroupKeyValue(value));
        }
        return values;
    }

    /** The refusal of a row whose foreign key no parent row holds, a NULL one included. */
    public static String foreignKeyViolated(final Table table, final ForeignKeyConstraint key) {
        return "Foreign key constraint \"SYS_INDEX_" + table.getName() + "_FOREIGN_KEY_"
            + String.join("_", key.getColumnNames()) + "_" + QualifiedName.parse(key.getReferencedTable()).last()
            + "_" + String.join("_", key.getReferencedColumns()) + "\" was violated.";
    }
}
