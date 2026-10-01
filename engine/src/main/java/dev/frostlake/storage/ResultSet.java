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
    // What a JDBC client's getUpdateCount() reports for the statement this result answers, or null when
    // the statement answers rows to read. Set by the statement that ran; see JdbcUpdateCounts.
    private Long jdbcUpdateCount;
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
        // The column spelled exactly so first — two result columns may differ only in case — and only
        // then the first whose name matches ignoring case.
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equals(columnName)) {
                return i;
            }
        }
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

    /**
     * What a JDBC client reports as the update count of the statement this result answers — the count
     * Snowflake's driver hands back from {@code getUpdateCount()} after {@code execute()} answers false —
     * or null when the statement answers rows, which the driver hands back as a result set instead.
     *
     * @return the statement's update count, or null
     */
    public Long getJdbcUpdateCount() {
        return jdbcUpdateCount;
    }

    /**
     * Record the update count a JDBC client reports for the statement this result answers.
     *
     * @param count the statement's update count — the affected rows of DML, 0 for every other statement
     *              that answers no rows — or null when the statement answers rows
     * @return this result
     */
    public ResultSet markJdbcUpdateCount(final Long count) {
        this.jdbcUpdateCount = count;
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
