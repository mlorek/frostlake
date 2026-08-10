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

package dev.frostlake.console;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a JDBC {@link ResultSet} for the console clients without ever rewinding it: result
 * sets are forward-only in live Snowflake's driver (and in Frostlake's direct transport), so the
 * printer reads each row exactly once and reports how many it rendered — the caller must not
 * touch the cursor again. Output goes to the console writer and, when spooling, to the spool
 * writer as well.
 */
final class ResultSetPrinter {

    private final PrintWriter out;
    private final PrintWriter spool;

    ResultSetPrinter(final PrintWriter out, final PrintWriter spool) {
        this.out = out;
        this.spool = spool;
    }

    /**
     * Renders the whole result set in the requested format and returns the number of rows
     * rendered. A read error is reported on the writers instead of thrown, so the console keeps
     * its prompt.
     */
    int print(final ResultSet rs, final OutputFormat format) {
        try {
            final ResultSetMetaData metaData = rs.getMetaData();
            final int columnCount = metaData.getColumnCount();

            if (!rs.next()) {
                println("(No rows)");
                flush();
                return 0;
            }

            final int rows;
            switch (format) {
                case CSV:
                    rows = printCSV(rs, metaData, columnCount);
                    break;
                case JSON:
                    rows = printJSON(rs, metaData, columnCount);
                    break;
                default:
                    rows = printTable(rs, metaData, columnCount);
            }
            flush();
            return rows;
        } catch (final SQLException e) {
            println("Error reading result set: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            flush();
            return 0;
        }
    }

    /** The cursor is already on the first row; renders it and every following row. */
    private int printTable(final ResultSet rs, final ResultSetMetaData metaData, final int columnCount)
            throws SQLException {
        final List<String[]> rows = new ArrayList<String[]>();
        final String[] headers = new String[columnCount];
        final int[] columnWidths = new int[columnCount];

        for (int i = 0; i < columnCount; i++) {
            headers[i] = metaData.getColumnName(i + 1);
            columnWidths[i] = headers[i].length();
        }

        do {
            final String[] row = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                final Object value = rs.getObject(i + 1);
                row[i] = (value == null) ? "NULL" : value.toString();
                columnWidths[i] = Math.max(columnWidths[i], row[i].length());
            }
            rows.add(row);
        } while (rs.next());

        final StringBuilder separator = new StringBuilder("+");
        for (final int width : columnWidths) {
            separator.append("-".repeat(width + 2)).append("+");
        }
        final String sepLine = separator.toString();

        println(sepLine);
        final StringBuilder headerLine = new StringBuilder("|");
        for (int i = 0; i < columnCount; i++) {
            headerLine.append(" ").append(String.format("%-" + columnWidths[i] + "s", headers[i])).append(" |");
        }
        println(headerLine.toString());
        println(sepLine);

        for (final String[] row : rows) {
            final StringBuilder rowLine = new StringBuilder("|");
            for (int i = 0; i < columnCount; i++) {
                rowLine.append(" ").append(String.format("%-" + columnWidths[i] + "s", row[i])).append(" |");
            }
            println(rowLine.toString());
        }
        println(sepLine);
        return rows.size();
    }

    /** The cursor is already on the first row; renders it and every following row. */
    private int printCSV(final ResultSet rs, final ResultSetMetaData metaData, final int columnCount)
            throws SQLException {
        final StringBuilder headerLine = new StringBuilder();
        for (int i = 0; i < columnCount; i++) {
            if (i > 0) {
                headerLine.append(",");
            }
            headerLine.append(metaData.getColumnName(i + 1));
        }
        println(headerLine.toString());

        int count = 0;
        do {
            final StringBuilder rowLine = new StringBuilder();
            for (int i = 0; i < columnCount; i++) {
                if (i > 0) {
                    rowLine.append(",");
                }
                final Object value = rs.getObject(i + 1);
                String strValue = (value == null) ? "" : value.toString();
                if (strValue.contains(",") || strValue.contains("\"") || strValue.contains("\n")) {
                    strValue = "\"" + strValue.replace("\"", "\"\"") + "\"";
                }
                rowLine.append(strValue);
            }
            println(rowLine.toString());
            count++;
        } while (rs.next());
        return count;
    }

    /** The cursor is already on the first row; renders it and every following row. */
    private int printJSON(final ResultSet rs, final ResultSetMetaData metaData, final int columnCount)
            throws SQLException {
        println("[");
        int count = 0;
        do {
            if (count > 0) {
                println(",");
            }
            final StringBuilder rowLine = new StringBuilder("  {");
            for (int i = 0; i < columnCount; i++) {
                if (i > 0) {
                    rowLine.append(", ");
                }
                rowLine.append("\"").append(metaData.getColumnName(i + 1)).append("\": ");
                final Object value = rs.getObject(i + 1);
                if (value == null) {
                    rowLine.append("null");
                } else if (value instanceof Number) {
                    rowLine.append(value);
                } else {
                    rowLine.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                }
            }
            rowLine.append("}");
            print(rowLine.toString());
            count++;
        } while (rs.next());
        println("");
        println("]");
        return count;
    }

    private void print(final String line) {
        out.print(line);
        if (spool != null) {
            spool.print(line);
        }
    }

    private void println(final String line) {
        out.println(line);
        if (spool != null) {
            spool.println(line);
        }
    }

    private void flush() {
        out.flush();
        if (spool != null) {
            spool.flush();
        }
    }
}
