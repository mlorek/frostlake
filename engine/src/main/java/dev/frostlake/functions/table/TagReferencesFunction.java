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

package dev.frostlake.functions.table;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The INFORMATION_SCHEMA.TAG_REFERENCES(object_name, object_domain) TABLE FUNCTION — the tags
 * attached to one object, as
 * {@code SELECT … FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('db.schema.t', 'TABLE'))}.
 *
 * <p>Both arguments are required. The object name may be bare or qualified, and resolves against
 * the session's current database and schema when it is not. A real account takes TABLE for every
 * table-like object — passing VIEW is refused with "Please use object type TABLE for all kinds of
 * table-like objects" — and takes COLUMN with a four-part name whose last part is the column.
 */
public class TagReferencesFunction extends TableFunction {

    /** Live's column order. */
    private static final String[] COLUMN_NAMES = {
        "TAG_DATABASE", "TAG_SCHEMA", "TAG_NAME", "TAG_VALUE", "LEVEL", "OBJECT_DATABASE",
        "OBJECT_SCHEMA", "OBJECT_NAME", "DOMAIN", "COLUMN_NAME", "APPLY_METHOD"
    };

    /** How the tag came to be attached; the engine has no tag propagation, so always manual. */
    private static final String APPLY_METHOD = "MANUAL";

    private final Catalog catalog;

    public TagReferencesFunction(final Catalog catalog) {
        super("TAG_REFERENCES");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute, where both positional arguments are in hand.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        throw new RuntimeException("TAG_REFERENCES requires an object name and an object domain");
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        if (positionalArgs == null || positionalArgs.size() != 2
            || positionalArgs.get(0) == null || positionalArgs.get(1) == null) {
            throw new RuntimeException("TAG_REFERENCES requires an object name and an object domain");
        }
        final String objectName = positionalArgs.get(0).toString();
        final String domain = positionalArgs.get(1).toString().toUpperCase();
        final List<Row> rows = new ArrayList<Row>();
        if ("COLUMN".equals(domain)) {
            addColumnTags(objectName, rows);
        } else if ("TABLE".equals(domain)) {
            addTableTags(objectName, rows);
        } else if ("SCHEMA".equals(domain)) {
            addSchemaTags(objectName, rows);
        } else if ("DATABASE".equals(domain)) {
            addDatabaseTags(objectName, rows);
        } else if ("WAREHOUSE".equals(domain)) {
            addWarehouseTags(objectName, rows);
        } else if ("VIEW".equals(domain) || "MATERIALIZED VIEW".equals(domain)) {
            throw new RuntimeException("Invalid value " + domain + " for argument OBJECT_TYPE. "
                + "Please use object type TABLE for all kinds of table-like objects.");
        } else {
            throw new RuntimeException("Unknown domain: " + domain + ".");
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        return new ResultSet(columns, rows);
    }

    /** Tags on a table or a view — live reports both under the TABLE domain. */
    private void addTableTags(final String objectName, final List<Row> rows) {
        final QualifiedName name = qualify(QualifiedName.parse(objectName), 3);
        final String databaseName = name.part(0);
        final String schemaName = name.part(1);
        final String simpleName = name.last();
        final Schema schema = resolveSchema(databaseName, schemaName, objectName);
        if (schema.hasTable(simpleName)) {
            addRows(rows, schema.getTable(simpleName).getTagValues(),
                databaseName, schemaName, simpleName, "TABLE", null);
            return;
        }
        if (!schema.hasView(simpleName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", objectName));
        }
        addRows(rows, schema.getView(simpleName).getTagValues(),
            databaseName, schemaName, simpleName, "TABLE", null);
    }

    /** Tags on a schema, named as {@code db.schema} or bare. Live leaves OBJECT_SCHEMA null here. */
    private void addSchemaTags(final String objectName, final List<Row> rows) {
        final QualifiedName name = qualify(QualifiedName.parse(objectName), 2);
        final String databaseName = name.part(0);
        final String simpleName = name.last();
        if (databaseName == null || catalog.getDatabase(databaseName) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", objectName));
        }
        final Schema schema = catalog.getDatabase(databaseName).getSchema(simpleName);
        addRows(rows, schema.getTagValues(), databaseName, null, simpleName, "SCHEMA", null);
    }

    /** Tags on a database. Live leaves both OBJECT_DATABASE and OBJECT_SCHEMA null here. */
    private void addDatabaseTags(final String objectName, final List<Row> rows) {
        final String simpleName = QualifiedName.parse(objectName).last();
        if (catalog.getDatabase(simpleName) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Database", objectName));
        }
        addRows(rows, catalog.getDatabase(simpleName).getTagValues(),
            null, null, simpleName, "DATABASE", null);
    }

    /** Tags on a warehouse, which lives at the account level like a database. */
    private void addWarehouseTags(final String objectName, final List<Row> rows) {
        final String simpleName = QualifiedName.parse(objectName).last();
        final Warehouse warehouse = catalog.getWarehouse(simpleName);
        if (warehouse == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse", objectName));
        }
        addRows(rows, warehouse.getTagValues(), null, null, simpleName, "WAREHOUSE", null);
    }

    /** Tags on one column, named as {@code db.schema.table.column}. */
    private void addColumnTags(final String objectName, final List<Row> rows) {
        final QualifiedName name = qualify(QualifiedName.parse(objectName), 4);
        final String databaseName = name.part(0);
        final String schemaName = name.part(1);
        final String tableName = name.part(2);
        final String columnName = name.last();
        final Schema schema = resolveSchema(databaseName, schemaName, objectName);
        if (!schema.hasTable(tableName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", objectName));
        }
        for (final TableColumn column : schema.getTable(tableName).getColumns()) {
            if (columnName.equalsIgnoreCase(column.getName())) {
                addRows(rows, column.getTagValues(), databaseName, schemaName, tableName,
                    "COLUMN", column.getName());
                return;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Column", columnName));
    }

    /**
     * Fill in the session's database and schema for a name written with fewer than {@code parts}
     * parts, so that TAG_REFERENCES('t', 'TABLE') resolves the way a real account resolves it.
     */
    private QualifiedName qualify(final QualifiedName name, final int parts) {
        if (name.size() >= parts) {
            return name;
        }
        final List<String> filled = new ArrayList<String>();
        if (parts == 2) {
            filled.add(catalog.getCurrentDatabase());
        } else if (name.size() == parts - 2) {
            filled.add(catalog.getCurrentDatabase());
            filled.add(catalog.getCurrentSchema());
        } else if (name.size() == parts - 1) {
            filled.add(catalog.getCurrentDatabase());
        }
        for (int i = 0; i < name.size(); i++) {
            filled.add(name.part(i));
        }
        return QualifiedName.parse(String.join(".", filled));
    }

    private Schema resolveSchema(final String databaseName, final String schemaName,
                                 final String objectName) {
        if (databaseName == null || schemaName == null
            || catalog.getDatabase(databaseName) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", objectName));
        }
        return catalog.getDatabase(databaseName).getSchema(schemaName);
    }

    /** One row per tag attached at this level, in live's column order. */
    private void addRows(final List<Row> rows, final Map<String, String> tags,
                         final String databaseName, final String schemaName, final String objectName,
                         final String level, final String columnName) {
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
            final QualifiedName tagName = qualify(QualifiedName.parse(tag.getKey()), 3);
            rows.add(new Row(Arrays.asList(
                tagName.part(0), tagName.part(1), tagName.last(), tag.getValue(),
                level, databaseName, schemaName, objectName, level, columnName, APPLY_METHOD)));
        }
    }
}
