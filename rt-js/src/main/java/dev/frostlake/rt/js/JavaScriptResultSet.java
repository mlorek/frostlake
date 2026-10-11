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

package dev.frostlake.rt.js;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.graalvm.polyglot.HostAccess;

/**
 * The rows behind a JavaScript procedure's result set. The handler never holds this object: the script-side
 * {@code ResultSet} does (see {@code JavaScriptHandler}), and calls only the members exported here. A column's
 * value crosses as the led JSON text a handler's semi-structured argument crosses as (see
 * {@link JavaScriptArgumentJson}), so the handler reads a number, a string, a boolean, a {@code Date}, a
 * {@code Uint8Array}, an array, an object or {@code null}, as on an account (live-verified).
 */
public class JavaScriptResultSet {

    private final ResultSet resultSet;
    private final JavaScriptApiFailures failures;
    private int currentRow = -1;

    JavaScriptResultSet(final ResultSet resultSet, final JavaScriptApiFailures failures) {
        this.resultSet = resultSet;
        this.failures = failures;
    }

    @HostAccess.Export
    public boolean next() {
        if (currentRow < resultSet.getRowCount()) {
            currentRow++;
        }
        return currentRow < resultSet.getRowCount();
    }

    /**
     * The current row's value in a column (live-verified): the column is a number, counted from 1, or a name
     * exactly as the result names it. Before the first {@code next()} there is no row to read, and a column the
     * result does not have is refused; past the last row every column reads null.
     *
     * @param column  the column as the handler gave it: a number or a text, or null for anything else
     * @param spelled the column as the handler's language prints it, for the refusal
     * @return the value's led JSON text, or a led failure
     */
    @HostAccess.Export
    public String columnValue(final Object column, final String spelled) {
        if (currentRow < 0) {
            return failures.misuse("ResultSet is empty or not prepared (call next() first).");
        }
        final int index = indexOf(column);
        if (index < 0) {
            return failures.misuse("Given column name/index does not exist: " + spelled);
        }
        if (currentRow >= resultSet.getRowCount()) {
            return JavaScriptArgumentJson.ofColumn(null);
        }
        final Row row = resultSet.getRows().get(currentRow);
        return JavaScriptArgumentJson.ofColumn(index < row.size() ? row.getValue(index) : null);
    }

    /** The zero-based column a number or a name stands for, or -1. */
    private int indexOf(final Object column) {
        final int count = resultSet.getColumns().size();
        if (column instanceof Number) {
            final double number = ((Number) column).doubleValue();
            final int index = (int) number;
            return index == number && index >= 1 && index <= count ? index - 1 : -1;
        }
        if (column instanceof String) {
            for (int i = 0; i < count; i++) {
                if (resultSet.getColumns().get(i).getName().equals(column)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * @param columnIndex the column, counted from 1
     * @return its name, or null for no such column
     */
    @HostAccess.Export
    public String columnName(final int columnIndex) {
        return columnIndex >= 1 && columnIndex <= resultSet.getColumns().size()
            ? resultSet.getColumns().get(columnIndex - 1).getName() : null;
    }

    @HostAccess.Export
    public int getRowCount() {
        return resultSet.getRowCount();
    }

    @HostAccess.Export
    public int getColumnCount() {
        return resultSet.getColumnCount();
    }
}
