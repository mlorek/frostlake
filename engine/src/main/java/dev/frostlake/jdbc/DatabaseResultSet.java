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

package dev.frostlake.jdbc;

import dev.frostlake.http.ColumnData;
import dev.frostlake.http.ResultSetData;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ClientValueText;
import dev.frostlake.values.TemporalText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * The HTTP transport's result set: the wire's JSON cells, read through {@link AbstractFrostlakeResultSet}. The wire
 * carries each cell in its JSON form — a temporal value or a BINARY as text, a number as whatever JSON number it
 * spells — so reading one back as text or as an object goes by the type the column's metadata names.
 */
public class DatabaseResultSet extends AbstractFrostlakeResultSet {

    private final ResultSetData data;

    public DatabaseResultSet(final Statement statement, final ResultSetData data) {
        super(statement);
        this.data = data;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new DatabaseResultSetMetaData(data.getColumns());
    }

    @Override
    protected int columnCount() {
        return data.getColumns().size();
    }

    @Override
    protected String columnLabel(final int column) {
        return data.getColumns().get(column - 1).getName();
    }

    @Override
    protected int rowCount() {
        return data.getRowCount();
    }

    /**
     * A wire cell. JSON has no NaN or infinity, so the wire spells a FLOAT's as a string: it is the double it names,
     * which getObject answers and getString spells NaN, Infinity and -Infinity, as the account's driver does
     * (live-verified).
     */
    @Override
    protected Object cell(final int row, final int column) {
        final Object cell = data.getRows().get(row - 1).get(column - 1);
        if (cell instanceof String && isApproximateColumn(column)) {
            final Double nonFinite = nonFiniteDouble((String) cell);
            if (nonFinite != null) {
                return nonFinite;
            }
        }
        return cell;
    }

    @Override
    protected IntervalKind intervalKind(final int column, final Object cell) {
        if (column < 1 || column > columnCount()) {
            return null;
        }
        final ColumnData described = data.getColumns().get(column - 1);
        return IntervalColumnReads.kindOf(described.getDataType(), described.scaleOrZero());
    }

    /** An interval cell crosses the wire as the decimal digits of its count. */
    @Override
    protected BigInteger intervalNumber(final Object cell) {
        return new BigInteger(cell.toString());
    }

    @Override
    protected String text(final int column, final Object value) {
        if (value instanceof Boolean && isBooleanColumn(column)) {
            // A BOOLEAN cell crosses the wire as the JSON boolean; the driver's text is upper-case.
            // A boolean inside a VARIANT column keeps that column's lower-case JSON text.
            return ClientValueText.booleanText(((Boolean) value).booleanValue());
        }
        if (value instanceof Number && isApproximateColumn(column)) {
            // A FLOAT's text is Java's own of its double, as the in-process driver spells it.
            return ClientValueText.approximateText(((Number) value).doubleValue());
        }
        if (value instanceof BigDecimal) {
            // The digits in place, never BigDecimal's scientific 1E-8 / 0E-20 — see ClientValueText.
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof String && isTimeOrTimestampColumn(column)) {
            // The wire carries a temporal cell's whole fraction of a second; getString is the display
            // text, three digits for a timestamp and none for a TIME.
            return TemporalText.displayOfWire((String) value, typeName(column));
        }
        return value.toString();
    }

    @Override
    protected Object object(final int column, final Object value) {
        if (value instanceof String && typeName(column) != null
                && typeName(column).toUpperCase(Locale.ROOT).contains("BINARY")) {
            // BINARY crosses the JSON wire as hex text; getObject restores the JDBC byte[] form.
            return JdbcMarshaling.toBytes(value);
        }
        if (value instanceof String && "DECFLOAT".equalsIgnoreCase(typeName(column))) {
            // A DECFLOAT crosses the wire as its text; the client reads the BigDecimal of it, as the account's does.
            return DecimalText.parse((String) value);
        }
        if (value instanceof Number && !(value instanceof Double) && isApproximateColumn(column)) {
            // The wire parses every JSON fraction as a BigDecimal to keep NUMBER exact; a FLOAT column's
            // cell is the double it spells, which is what getObject promises for the type.
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String && isTemporalColumn(column)) {
            // A temporal cell crosses the JSON wire as text and comes back as the java.sql class the
            // column's metadata NAMES — the class the account's driver answers with, and the one a client
            // reading getColumnClassName is entitled to cast to.
            final String type = typeName(column).toUpperCase(Locale.ROOT);
            if (type.startsWith("DATE")) {
                return JdbcMarshaling.toDate(value);
            }
            if (type.startsWith("TIME") && !type.startsWith("TIMESTAMP")) {
                return JdbcMarshaling.toTime(value);
            }
            return JdbcMarshaling.toTimestamp(value);
        }
        if (value instanceof Number && !(value instanceof BigDecimal) && "DECIMAL".equalsIgnoreCase(typeName(column))) {
            // A file transfer's size is the client's own DECIMAL, read as the BigDecimal its metadata names.
            return DecimalText.parse(value.toString());
        }
        if (value instanceof Number && !(value instanceof BigDecimal)
                && !(value instanceof Double) && isWholeNumberColumn(column)) {
            // The wire's JSON integers parse to whatever fits them — an Integer for a small one, a
            // BigInteger past a long — while a NUMBER of scale 0 reads as a Long, and as a BigDecimal
            // only once its value no longer fits one (live-verified, metadata naming Long throughout).
            final BigInteger whole = new BigInteger(value.toString());
            return whole.bitLength() < Long.SIZE ? (Object) Long.valueOf(whole.longValue())
                : (Object) new BigDecimal(whole);
        }
        return value;
    }

    /** The type name the wire metadata gives a column, from 1. */
    private String typeName(final int column) {
        return data.getColumns().get(column - 1).getDataType();
    }

    /** Whether a column is declared TIME or a TIMESTAMP. */
    private boolean isTimeOrTimestampColumn(final int column) {
        final String type = typeName(column);
        return type != null && ("TIME".equalsIgnoreCase(type) || type.toUpperCase(Locale.ROOT).startsWith("TIMESTAMP"));
    }

    /** Whether a column is declared DATE, TIME or a TIMESTAMP flavour. */
    private boolean isTemporalColumn(final int column) {
        final String type = typeName(column);
        return type != null && (type.toUpperCase(Locale.ROOT).startsWith("DATE")
            || type.toUpperCase(Locale.ROOT).startsWith("TIME"));
    }

    /** Whether a column is a NUMBER of scale 0, whose cells read as whole numbers. */
    private boolean isWholeNumberColumn(final int column) {
        final ColumnData described = data.getColumns().get(column - 1);
        return described.getDataType() != null
            && described.getDataType().toUpperCase(Locale.ROOT).startsWith("NUMBER") && described.scaleOrZero() == 0;
    }

    /** Whether a column is declared BOOLEAN. */
    private boolean isBooleanColumn(final int column) {
        return "BOOLEAN".equalsIgnoreCase(typeName(column));
    }

    /** Whether a column is declared FLOAT or one of its aliases. */
    private boolean isApproximateColumn(final int column) {
        return NumericType.isApproximateName(typeName(column));
    }

    /** The double a wire word for a non-finite FLOAT names, or null for any other text. */
    private static Double nonFiniteDouble(final String word) {
        switch (word) {
            case "NaN":
                return Double.valueOf(Double.NaN);
            case "Infinity":
            case "inf":
                return Double.valueOf(Double.POSITIVE_INFINITY);
            case "-Infinity":
            case "-inf":
                return Double.valueOf(Double.NEGATIVE_INFINITY);
            default:
                return null;
        }
    }
}
