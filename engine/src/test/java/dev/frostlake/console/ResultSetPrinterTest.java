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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console's {@link ResultSetPrinter} against forward-only result sets from the direct JDBC
 * transport — SHOW output and multi-branch SELECTs render without any cursor rewinding, and the
 * printer reports the rendered row count.
 */
public class ResultSetPrinterTest extends BaseJdbcTest {

    private String render(final ResultSet rs, final OutputFormat format, final int expectedRows)
            throws SQLException {
        final StringWriter buffer = new StringWriter();
        final PrintWriter out = new PrintWriter(buffer);
        final int rows = new ResultSetPrinter(out, null).print(rs, format);
        rs.close();
        assertEquals(expectedRows, rows);
        final String text = buffer.toString();
        assertFalse(text.contains("Error reading result set"), text);
        return text;
    }

    @Test
    public void testShowTablesRendersAsTable() throws SQLException {
        statement.execute("CREATE TABLE printer_t1 (id INTEGER)");
        statement.execute("CREATE TABLE printer_t2 (id INTEGER)");

        final ResultSet rs = statement.executeQuery("SHOW TABLES LIKE 'PRINTER_%'");
        final String text = render(rs, OutputFormat.TABLE, 2);
        assertTrue(text.contains("PRINTER_T1"), text);
        assertTrue(text.contains("PRINTER_T2"), text);
        assertTrue(text.contains("| name"), text);
    }

    @Test
    public void testMultiBranchSelectRendersAllRows() throws SQLException {
        final ResultSet rs = statement.executeQuery(
            "SELECT 'a' AS v UNION ALL SELECT 'b' UNION ALL SELECT 'c'");
        final String text = render(rs, OutputFormat.TABLE, 3);
        assertTrue(text.contains("| a"), text);
        assertTrue(text.contains("| c"), text);
    }

    @Test
    public void testEmptyResultPrintsNoRows() throws SQLException {
        statement.execute("CREATE TABLE printer_empty (id INTEGER)");
        final ResultSet rs = statement.executeQuery("SELECT id FROM printer_empty");
        final String text = render(rs, OutputFormat.TABLE, 0);
        assertTrue(text.contains("(No rows)"), text);
    }

    @Test
    public void testCsvFormatRendersEveryRow() throws SQLException {
        final ResultSet rs = statement.executeQuery("SELECT 1 AS n UNION ALL SELECT 2");
        final String text = render(rs, OutputFormat.CSV, 2);
        assertTrue(text.startsWith("N"), text);
        assertTrue(text.contains("1"), text);
        assertTrue(text.contains("2"), text);
    }

    @Test
    public void testJsonFormatRendersEveryRow() throws SQLException {
        final ResultSet rs = statement.executeQuery("SELECT 1 AS n UNION ALL SELECT 2");
        final String text = render(rs, OutputFormat.JSON, 2);
        assertTrue(text.contains("\"N\": 1"), text);
        assertTrue(text.contains("\"N\": 2"), text);
        assertTrue(text.trim().endsWith("]"), text);
    }
}
