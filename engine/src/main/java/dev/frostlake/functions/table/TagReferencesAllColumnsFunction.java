/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS(object_name, object_domain) TABLE FUNCTION — the tags reaching
 * every column of one table-like object, as
 * {@code SELECT … FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('db.schema.t', 'TABLE'))}.
 *
 * <p>It answers {@link TagReferencesFunction}'s columns, one row per column and tag: a tag set on the column
 * itself ({@code LEVEL} {@code COLUMN}, {@code APPLY_METHOD} {@code MANUAL}) and each tag the column inherits
 * from its table, schema and database. The rows of one tag stay together, its columns in their own order.
 *
 * <p>The domain is {@code TABLE} for every table-like object: a view, a materialized view and an external table
 * are refused as {@link TagReferencesFunction} refuses them, any other domain the account knows as not supported —
 * a dynamic table's own among them — and an unknown one as unknown (live-verified).
 */
public class TagReferencesAllColumnsFunction extends TableFunction {

    private static final String NAME = "TAG_REFERENCES_ALL_COLUMNS";

    /** How many parts a table's full name has. */
    private static final int TABLE_NAME_PARTS = 3;

    private final Catalog catalog;
    private final TagReferencesFunction references;

    public TagReferencesAllColumnsFunction(final Catalog catalog) {
        super(NAME);
        this.catalog = catalog;
        this.references = new TagReferencesFunction(catalog);
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute, where both positional arguments are in hand.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        if (namedArgs == null || namedArgs.isEmpty()) {
            // A call without an argument misses the first one.
            TagReferencesFunction.requireArguments(NAME, new ArrayList<Object>());
        }
        throw new RuntimeException(NAME + " requires an object name and an object domain");
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        TagReferencesFunction.requireArguments(NAME, positionalArgs);
        if (!"TABLE".equals(TagReferencesFunction.domainOf(positionalArgs.get(1).toString()))) {
            throw new RuntimeException("Unsupported feature 'Only table-like objects are currently supported'.");
        }
        final String[] written = SqlIdentifiers.canonicalTextParts(positionalArgs.get(0).toString());
        final String objectName = QualifiedName.join(written);
        if (written.length > TABLE_NAME_PARTS) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final String[] name = references.tableName(written, objectName);
        final Schema schema = references.tableSchema(name, objectName);
        final Taggable table = references.requireTableLike(schema, name[2], written, objectName);
        final List<Row> rows = new ArrayList<Row>();
        for (final TableColumn column : columnsOf(table)) {
            references.addLineage(rows, new Taggable[] {column, table, schema, catalog.getDatabase(name[0])},
                new String[] {"COLUMN", "TABLE", "SCHEMA", "DATABASE"}, name[0], name[1], name[2], "COLUMN",
                column.getName());
        }
        // The rows of one tag stay together; the sort is stable, so its columns keep their order.
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row a, final Row b) {
                return tagOf(a).compareTo(tagOf(b));
            }
        });
        return new ResultSet(TagReferencesFunction.columns(), rows);
    }

    private static String tagOf(final Row row) {
        return row.getValue(0) + "." + row.getValue(1) + "." + row.getValue(2);
    }

    /**
     * The object's columns: a table's own, and a view's or a materialized view's as its query resolved them, which
     * carry the tags its column list or ALTER VIEW sets on them.
     */
    private static List<TableColumn> columnsOf(final Taggable object) {
        final List<TableColumn> columns = new ArrayList<TableColumn>();
        if (object instanceof Table) {
            columns.addAll(((Table) object).getColumns());
        } else if (object instanceof View) {
            if (((View) object).hasResolvedColumns()) {
                columns.addAll(((View) object).getResolvedColumns());
            }
        } else if (object instanceof MaterializedView && ((MaterializedView) object).hasResolvedColumns()) {
            columns.addAll(((MaterializedView) object).getResolvedColumns());
        }
        return columns;
    }
}
