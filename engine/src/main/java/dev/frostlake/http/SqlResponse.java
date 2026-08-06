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

package dev.frostlake.http;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TemporalText;
import dev.frostlake.values.VariantValue;

import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.NumericType;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP response object for SQL execution
 */
public class SqlResponse {
    private boolean success;
    private String sessionId;
    private String errorMessage;
    private List<ResultSetData> resultSets;
    private long executionTimeMs;

    public SqlResponse() {
        this.resultSets = new ArrayList<>();
    }

    public SqlResponse(final boolean success, final String sessionId) {
        this.success = success;
        this.sessionId = sessionId;
        this.resultSets = new ArrayList<>();
    }

    public static SqlResponse success(final String sessionId, final List<ResultSet> resultSets, final long executionTimeMs) {
        SqlResponse response = new SqlResponse(true, sessionId);
        response.setExecutionTimeMs(executionTimeMs);

        for (final ResultSet rs : resultSets) {
            response.addResultSet(ResultSetData.from(rs));
        }

        return response;
    }

    public static SqlResponse error(final String sessionId, final String errorMessage) {
        SqlResponse response = new SqlResponse(false, sessionId);
        response.setErrorMessage(errorMessage);
        return response;
    }

    public void addResultSet(final ResultSetData data) {
        this.resultSets.add(data);
    }

    // Getters and setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ResultSetData> getResultSets() {
        return resultSets;
    }

    public void setResultSets(final List<ResultSetData> resultSets) {
        this.resultSets = resultSets;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(final long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Serializable result set data
     */
    public static class ResultSetData {
        private List<ColumnData> columns;
        private List<List<Object>> rows;
        private int rowCount;

        public ResultSetData() {
            this.columns = new ArrayList<>();
            this.rows = new ArrayList<>();
        }

        public static ResultSetData from(final ResultSet rs) {
            ResultSetData data = new ResultSetData();

            // Copy columns
            for (final ResultSetColumn col : rs.getColumns()) {
                ColumnData colData = new ColumnData();
                colData.setName(col.getName());
                colData.setDataType(col.getDataType().getName());
                if (col.getDataType() instanceof NumericType) {
                    final NumericType numeric = (NumericType) col.getDataType();
                    colData.setPrecision(numeric.getPrecision());
                    colData.setScale(numeric.getScale());
                }
                data.getColumns().add(colData);
            }

            // Copy rows. Engine-internal value objects are mapped to their JSON wire form here:
            // a BINARY cell crosses as its uppercase-hex text (the client re-types via the column
            // metadata), so Jackson never bean-serializes an engine value class. A TEMPORAL cell
            // crosses as the text a real account's driver would print for that type — otherwise
            // Jackson emits its own ISO form and this transport disagrees with the in-process one
            // about the same cell, which is worse than either shape being wrong on its own.
            rs.reset();
            while (rs.next()) {
                List<Object> rowData = new ArrayList<>();
                for (int i = 0; i < rs.getColumnCount(); i++) {
                    final Object cell = rs.getValue(i);
                    rowData.add(cell instanceof BinaryValue ? ((BinaryValue) cell).toHex()
                        : cell instanceof VariantValue ? ((VariantValue) cell).text()
                        : TemporalText.wireValue(cell, rs.getColumns().get(i).getDataType()));
                }
                data.getRows().add(rowData);
            }

            data.setRowCount(rs.getRowCount());
            return data;
        }

        // Getters and setters

        public List<ColumnData> getColumns() {
            return columns;
        }

        public void setColumns(final List<ColumnData> columns) {
            this.columns = columns;
        }

        public List<List<Object>> getRows() {
            return rows;
        }

        public void setRows(final List<List<Object>> rows) {
            this.rows = rows;
        }

        public int getRowCount() {
            return rowCount;
        }

        public void setRowCount(final int rowCount) {
            this.rowCount = rowCount;
        }
    }

    /**
     * Serializable column data
     */
    public static class ColumnData {
        private String name;
        private String dataType;
        private int precision;
        private int scale;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(final String dataType) {
            this.dataType = dataType;
        }

        public int getPrecision() {
            return precision;
        }

        public void setPrecision(final int precision) {
            this.precision = precision;
        }

        public int getScale() {
            return scale;
        }

        public void setScale(final int scale) {
            this.scale = scale;
        }
    }
}
