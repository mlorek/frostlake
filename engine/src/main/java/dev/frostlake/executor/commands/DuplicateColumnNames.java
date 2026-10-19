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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The columns a stored relation is created with — a view's, a materialized view's, a CREATE TABLE … AS SELECT's
 * — each need a name of their own: a query that projects one name twice is refused as it is created, with
 * {@code duplicate column name 'A'}, naming the first column that repeats an earlier one. A star that expands to
 * two columns of one name is refused alike, and a column list that renames them apart makes the same query
 * storable. Names match exactly, so {@code "a"} beside {@code A} is no duplicate (all live-verified).
 */
final class DuplicateColumnNames {

    private DuplicateColumnNames() {
    }

    /**
     * Refuse a stored relation's resolved columns when two carry one name.
     *
     * @param columns the columns, in order; null when the shape is not known
     */
    static void rejectAmong(final List<TableColumn> columns) {
        if (columns == null) {
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final TableColumn column : columns) {
            names.add(column.getName());
        }
        reject(names);
    }

    /**
     * Refuse a query's result as the columns of a new table when two carry one name.
     *
     * @param result the query's result; null when it was not produced
     */
    static void rejectIn(final ResultSet result) {
        if (result == null) {
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : result.getColumns()) {
            names.add(column.getName());
        }
        reject(names);
    }

    /**
     * Refuse the first name that repeats an earlier one.
     *
     * @param names the relation's column names, in order; null when its shape is not known
     */
    static void reject(final List<String> names) {
        if (names == null) {
            return;
        }
        final Set<String> seen = new HashSet<>();
        for (final String name : names) {
            if (name != null && !seen.add(name)) {
                throw new RuntimeException(SqlCompilationError.of("duplicate column name '" + name + "'"));
            }
        }
    }
}
