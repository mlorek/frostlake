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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * The collation a string column takes when it names none: the DEFAULT_DDL_COLLATION of its table, else of its
 * schema, else of its database (documented), set on the column when the column is created - by CREATE TABLE's
 * column list or by ADD COLUMN - so a later change of the default leaves it as it is. A column written COLLATE ''
 * takes it as well, an empty default names no collation, and the columns a CREATE TABLE ... AS SELECT derives take
 * none (live-verified).
 */
final class DefaultDdlCollation {

    private static final String PARAMETER = "DEFAULT_DDL_COLLATION";

    private DefaultDdlCollation() {
    }

    /**
     * Gives each new string column without a collation of its own the default its table inherits.
     *
     * @param table   the table the columns are in
     * @param columns the new columns
     * @param schema  the table's schema
     * @param catalog the catalog holding the schema's database
     */
    static void apply(final Table table, final List<TableColumn> columns, final Schema schema,
                      final Catalog catalog) {
        final String collation = of(table, schema, catalog);
        if (collation == null || collation.isEmpty()) {
            return;
        }
        for (final TableColumn column : columns) {
            if (column.getDataType() instanceof StringType
                    && (column.getCollation() == null || column.getCollation().isEmpty())) {
                column.setCollation(collation);
            }
        }
    }

    /** The nearest DEFAULT_DDL_COLLATION set: the table's, the schema's or the database's; null where none is. */
    private static String of(final Table table, final Schema schema, final Catalog catalog) {
        if (table.getDefaultDdlCollation() != null) {
            return table.getDefaultDdlCollation();
        }
        if (schema == null) {
            return null;
        }
        if (schema.getParameters().isSet(PARAMETER)) {
            return schema.getParameters().get(PARAMETER);
        }
        final Database database =
            schema.getDatabaseName() == null ? null : catalog.findDatabase(schema.getDatabaseName());
        return database != null && database.getParameters().isSet(PARAMETER) ? database.getParameters().get(PARAMETER)
            : null;
    }
}
