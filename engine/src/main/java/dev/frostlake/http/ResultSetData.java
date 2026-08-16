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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TemporalText;
import dev.frostlake.values.VariantValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable result set data
 */
public class ResultSetData {
    private List<ColumnData> columns;
    private List<List<Object>> rows;
    private int rowCount;

    public ResultSetData() {
        this.columns = new ArrayList<>();
        this.rows = new ArrayList<>();
    }

    public static ResultSetData from(final ResultSet rs) {
        final ResultSetData data = new ResultSetData();

        // Copy columns
        for (final ResultSetColumn col : rs.getColumns()) {
            final ColumnData colData = new ColumnData();
            colData.setName(col.getName());
            colData.setDataType(col.getDataType().getName());
            // The APPROXIMATE family carries no pair: Snowflake's driver answers 0 for a FLOAT
            // column's precision and scale, and its metadata surfaces leave both cells empty.
            if (col.getDataType() instanceof NumericType
                && !NumericType.isApproximate(col.getDataType())) {
                final NumericType numeric = (NumericType) col.getDataType();
                colData.setPrecision(numeric.getPrecision());
                colData.setScale(numeric.getScale());
            }
            // The field means "KNOWN to accept NULL", and is ALWAYS sent: a NOT NULL column, an
            // expression and a literal all send false, which is what the driver reports as
            // columnNoNulls. Absent therefore means only one thing — a server predating the field.
            colData.setNullable(Boolean.valueOf(col.isNullabilityKnown() && col.isNullable()));
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
            final List<Object> rowData = new ArrayList<>();
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
