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

package dev.frostlake.storage;

import dev.frostlake.values.RelationStatistics;

import java.util.ArrayList;
import java.util.List;

public class ResultSet {

    private final List<ResultSetColumn> columns;
    private final List<Row> rows;
    private int currentRow;
    // The affected-row count when this result is a DML statement's count grid, else null. The grid alone
    // cannot say so: a query may name its columns the same way ("number of rows inserted"), and a
    // RESULT_SCAN over a DML result is a query — only the statement that built the grid knows.
    private Long updateCount;
    // The statistics this result still carries of the catalog table beneath it, when it is a projection
    // of one filtered at most by a WHERE; null otherwise. See RelationStatistics.
    private RelationStatistics relationStatistics;

    public ResultSet(final List<ResultSetColumn> columns, final List<Row> rows) {
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>(rows);
        this.currentRow = -1;
    }

    public ResultSet(final List<ResultSetColumn> columns) {
        this(columns, new ArrayList<>());
    }

    public List<ResultSetColumn> getColumns() {
        return columns;
    }

    public List<Row> getRows() {
        return rows;
    }

    public void addRow(final Row row) {
        rows.add(row);
    }

    public boolean next() {
        currentRow++;
        return currentRow < rows.size();
    }

    public Object getValue(final int columnIndex) {
        if (currentRow < 0 || currentRow >= rows.size()) {
            throw new RuntimeException("No current row");
        }
        return rows.get(currentRow).getValue(columnIndex);
    }

    public Object getValue(final String columnName) {
        final int index = getColumnIndex(columnName);
        return getValue(index);
    }

    public int getColumnIndex(final String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new RuntimeException("Column not found: " + columnName);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return columns.size();
    }

    public void reset() {
        currentRow = -1;
    }

    /** The affected-row count when this result is a DML statement's count grid, else null. */
    public Long getUpdateCount() {
        return updateCount;
    }

    /**
     * Mark this result as a DML statement's count grid reporting {@code count} affected rows.
     *
     * @param count the affected-row total
     * @return this result
     */
    public ResultSet markUpdateCount(final long count) {
        this.updateCount = Long.valueOf(count);
        return this;
    }

    /** The statistics this result carries of the catalog table beneath it, or null. */
    public RelationStatistics getRelationStatistics() {
        return relationStatistics;
    }

    public void setRelationStatistics(final RelationStatistics relationStatistics) {
        this.relationStatistics = relationStatistics;
    }
}
