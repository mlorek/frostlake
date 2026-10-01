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

import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.ColumnLengths;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TemporalText;
import dev.frostlake.values.VariantJsonFormat;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import dev.frostlake.values.XmlVariants;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Serializable result set data
 */
public class ResultSetData {
    private List<ColumnData> columns;
    // Read off the wire token by token rather than through a tree, so a FLOAT's negative zero keeps
    // its sign: the mapper's BigDecimal reading of every fraction, which keeps a NUMBER exact, has no
    // negative zero to keep.
    @JsonDeserialize(using = WireRowsDeserializer.class)
    private List<List<Object>> rows;
    private int rowCount;
    // The affected-row count when this result is a DML statement's count grid, -1 for any other result.
    // Always sent, so a client can tell a server that marks its results (a number) from one that
    // predates the field (absent), and never has to recognise DML by the grid's column names.
    private Long updateCount;
    // What a JDBC client reports as the statement's update count — the rows a DML statement affected, 0 for
    // DDL and every other statement that answers no rows — and -1 when the statement answers rows to read.
    // Always sent, like updateCount; absent means a server that predates the field.
    private Long jdbcUpdateCount;

    /** The wire name of a day-time interval column: the family name Snowflake's JDBC driver reports. */
    public static final String DAY_TIME = "INTERVAL_DAY_TIME";

    /** The wire name of a year-month interval column. */
    public static final String YEAR_MONTH = "INTERVAL_YEAR_MONTH";

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
            // An interval column crosses under the family name the account's driver reports, no precision,
            // and the scale that driver uses to say which fields the interval spans.
            final IntervalQualifier qualifier = intervalQualifier(col.getDataType());
            if (qualifier != null) {
                colData.setDataType(qualifier.isDayTime() ? DAY_TIME : YEAR_MONTH);
                colData.setPrecision(0);
                colData.setScale(qualifier.driverScale());
            }
            // The APPROXIMATE family carries no pair: Snowflake's driver answers 0 for a FLOAT
            // column's precision and scale, and its metadata surfaces leave both cells empty.
            if (col.getDataType() instanceof NumericType
                && !NumericType.isApproximate(col.getDataType())) {
                final NumericType numeric = (NumericType) col.getDataType();
                colData.setPrecision(numeric.getPrecision());
                colData.setScale(numeric.getScale());
            }
            // A time of day or a timestamp carries its fractional-second precision as the scale, with
            // precision 0, as the account's result metadata does: TIMESTAMP_NTZ(3) sends 0 and 3, a bare
            // TIMESTAMP_LTZ 0 and 9. A DATE carries neither.
            if (col.getDataType() instanceof DateTimeType
                && !"DATE".equalsIgnoreCase(col.getDataType().getName())) {
                colData.setPrecision(0);
                colData.setScale(((DateTimeType) col.getDataType()).getPrecision());
            }
            // The field means "KNOWN to accept NULL", and is ALWAYS sent: a NOT NULL column, an
            // expression and a literal all send false, which is what the driver reports as
            // columnNoNulls. Absent therefore means only one thing — a server predating the field.
            colData.setNullable(Boolean.valueOf(col.isNullabilityKnown() && col.isNullable()));
            // A text or binary column's length, as the account's driver reports it; every other family
            // leaves the field out.
            colData.setLength(ColumnLengths.of(col.getDataType()));
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
                final DataType declared = rs.getColumns().get(i).getDataType();
                // A VECTOR crosses as its text, the way live's REST answers it: serialized as an object
                // it would arrive as its element type alone. A semi-structured cell crosses as its JSON
                // text with every DOUBLE in the account's fifteen-decimal form, and so does a DOUBLE read
                // out of a VARIANT, which the engine holds unwrapped and would send as a bare JSON number.
                // A STRING read out of one crosses JSON-quoted, as live's driver hands it back.
                final String unwrappedDouble = VariantJsonText.unwrappedDoubleText(cell, declared);
                rowData.add(cell instanceof BinaryValue ? ((BinaryValue) cell).toHex()
                    : cell instanceof VariantValue ? variantText((VariantValue) cell)
                    : cell instanceof VectorValue ? cell.toString()
                    : cell instanceof String && VariantJsonText.isSemiStructured(declared)
                        ? VariantJsonText.unwrappedStringText((String) cell)
                    : SharedFunctionHelpers.isNativeTemporal(cell) && VariantJsonText.isSemiStructured(declared)
                        ? VariantJsonText.unwrappedStringText(SharedFunctionHelpers.variantTemporalText(cell))
                    : unwrappedDouble != null ? unwrappedDouble
                    : IntervalCells.isInterval(cell) ? IntervalCells.wireText(cell)
                    : TemporalText.wireValue(cell, declared));
            }
            data.getRows().add(rowData);
        }

        data.setRowCount(rs.getRowCount());
        data.setUpdateCount(Long.valueOf(rs.getUpdateCount() != null ? rs.getUpdateCount().longValue() : -1L));
        data.setJdbcUpdateCount(Long.valueOf(rs.getJdbcUpdateCount() != null
            ? rs.getJdbcUpdateCount().longValue() : -1L));
        return data;
    }

    /** The fields an interval column's declared type spans, or null for any other column. */
    private static IntervalQualifier intervalQualifier(final DataType declared) {
        if (declared instanceof IntervalDayTimeType) {
            return ((IntervalDayTimeType) declared).getQualifier();
        }
        return declared instanceof IntervalYearMonthType ? ((IntervalYearMonthType) declared).getQualifier() : null;
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

    public Long getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(final Long updateCount) {
        this.updateCount = updateCount;
    }

    /**
     * A variant cell's text for the wire: an XML element as its XML, everything else as its JSON.
     *
     * <p>A cell that IS an XML element crosses as its markup — {@code <test>22</test>} — which is what
     * the account answers for a bare {@code PARSE_XML} and for a {@code GET} that reaches one, and what
     * the in-process paths already render. Nested inside an ARRAY or an OBJECT it stays the object
     * model instead, and the JSON render below produces that unchanged: the rule reads the CELL, never
     * the values inside a container.</p>
     */
    private static String variantText(final VariantValue value) {
        final JsonNode node = value.node();
        if (XmlVariants.isXmlElement(node)) {
            return XmlVariants.compactXml(node);
        }
        return VariantJsonFormat.render(node, VariantJsonText.clientTextOf(value));
    }

    public Long getJdbcUpdateCount() {
        return jdbcUpdateCount;
    }

    public void setJdbcUpdateCount(final Long jdbcUpdateCount) {
        this.jdbcUpdateCount = jdbcUpdateCount;
    }
}
