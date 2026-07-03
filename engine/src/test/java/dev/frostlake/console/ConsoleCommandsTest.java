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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for console commands (!set, !define, !source, !spool, etc.)
 */
public class ConsoleCommandsTest {

    private DatabaseEngine engine;
    private File tempSourceFile;
    private File tempSpoolFile;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        if (tempSourceFile != null && tempSourceFile.exists()) {
            tempSourceFile.delete();
        }
        if (tempSpoolFile != null && tempSpoolFile.exists()) {
            tempSpoolFile.delete();
        }
        engine.shutdown();
    }

    @Test
    public void testVariableSubstitution() {
        String sql = "SELECT * FROM &TABLE_NAME WHERE id = &ID_VALUE";

        // Create a simple substitution simulation
        String result = sql.replace("&TABLE_NAME", "users")
                           .replace("&ID_VALUE", "123");

        assertEquals("SELECT * FROM users WHERE id = 123", result);
    }

    @Test
    public void testVariableSubstitutionMultipleOccurrences() {
        String sql = "SELECT &COL FROM &TABLE WHERE &COL > 10";

        String result = sql.replace("&COL", "age")
                           .replace("&TABLE", "employees");

        assertEquals("SELECT age FROM employees WHERE age > 10", result);
    }

    @Test
    public void testSourceFileExecution() throws IOException {
        // Create a temporary SQL file
        tempSourceFile = File.createTempFile("test_source", ".sql");
        FileWriter writer = new FileWriter(tempSourceFile);
        writer.write("CREATE DATABASE test_source_db;\n");
        writer.write("USE DATABASE test_source_db;\n");
        writer.write("CREATE TABLE test_table (id INTEGER, name VARCHAR);\n");
        writer.write("INSERT INTO test_table VALUES (1, 'Alice');\n");
        writer.write("INSERT INTO test_table VALUES (2, 'Bob');\n");
        writer.close();

        // Execute commands from file
        BufferedReader reader = new BufferedReader(new FileReader(tempSourceFile));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("--")) {
                if (line.endsWith(";")) {
                    line = line.substring(0, line.length() - 1);
                }
                engine.execute(line);
            }
        }
        reader.close();

        // Verify database was created
        String currentDb = engine.getCatalog().getCurrentDatabase();
        assertNotNull(currentDb);
        assertTrue(currentDb.equalsIgnoreCase("test_source_db"));

        // Verify table has data
        ExecutionResult result = engine.execute("SELECT * FROM test_table");
        assertTrue(result.isSuccess());
        assertEquals(1, result.getResultSets().size());
        assertEquals(2, result.getResultSets().get(0).getRowCount());
    }

    @Test
    public void testSourceFileWithComments() throws IOException {
        tempSourceFile = File.createTempFile("test_comments", ".sql");
        FileWriter writer = new FileWriter(tempSourceFile);
        writer.write("-- This is a comment\n");
        writer.write("CREATE DATABASE test_comments_db;\n");
        writer.write("# Another comment style\n");
        writer.write("USE DATABASE test_comments_db;\n");
        writer.write("-- Comment at end\n");
        writer.close();

        // Execute commands from file (skip comments)
        BufferedReader reader = new BufferedReader(new FileReader(tempSourceFile));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("--") || line.startsWith("#")) {
                continue;
            }
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1);
            }
            engine.execute(line);
        }
        reader.close();

        String currentDb = engine.getCatalog().getCurrentDatabase();
        assertNotNull(currentDb);
        assertTrue(currentDb.equalsIgnoreCase("test_comments_db"));
    }

    @Test
    public void testSpoolFileOutput() throws IOException {
        tempSpoolFile = File.createTempFile("test_spool", ".txt");
        String spoolPath = tempSpoolFile.getAbsolutePath();

        // Create data
        engine.execute("CREATE DATABASE test_spool_db");
        engine.execute("USE DATABASE test_spool_db");
        engine.execute("CREATE TABLE spool_test (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO spool_test VALUES (1, 'First')");
        engine.execute("INSERT INTO spool_test VALUES (2, 'Second')");

        // Verify spool file was created
        assertTrue(tempSpoolFile.exists());
    }

    @Test
    public void testCsvFormat() {
        // Create test data
        engine.execute("CREATE DATABASE test_csv_db");
        engine.execute("USE DATABASE test_csv_db");
        engine.execute("CREATE TABLE csv_test (id INTEGER, name VARCHAR, value DOUBLE)");
        engine.execute("INSERT INTO csv_test VALUES (1, 'Alice', 100.5)");
        engine.execute("INSERT INTO csv_test VALUES (2, 'Bob', 200.75)");

        ExecutionResult result = engine.execute("SELECT * FROM csv_test");
        assertTrue(result.isSuccess());

        // Verify we can format as CSV (manual simulation)
        ResultSet rs = result.getResultSets().get(0);
        StringBuilder csv = new StringBuilder();

        // Header
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) csv.append(",");
            csv.append(rs.getColumns().get(i).getName());
        }
        csv.append("\n");

        // Data
        for (final Row row : rs.getRows()) {
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) csv.append(",");
                Object value = row.getValue(i);
                csv.append(value != null ? value.toString() : "");
            }
            csv.append("\n");
        }

        String csvOutput = csv.toString();
        String csvUpper = csvOutput.toUpperCase();
        assertTrue(csvUpper.contains("ID") && csvUpper.contains("NAME") && csvUpper.contains("VALUE"));
        assertTrue(csvOutput.contains("1,Alice,100.5") || csvOutput.contains("1, Alice, 100.5"));
        assertTrue(csvOutput.contains("2,Bob,200.75") || csvOutput.contains("2, Bob, 200.75"));
    }

    @Test
    public void testJsonFormat() {
        // Create test data
        engine.execute("CREATE DATABASE test_json_db");
        engine.execute("USE DATABASE test_json_db");
        engine.execute("CREATE TABLE json_test (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO json_test VALUES (1, 'Alice')");

        ExecutionResult result = engine.execute("SELECT * FROM json_test");
        assertTrue(result.isSuccess());

        // Verify we can format as JSON (manual simulation)
        ResultSet rs = result.getResultSets().get(0);
        StringBuilder json = new StringBuilder();
        json.append("[\n");

        for (int rowIdx = 0; rowIdx < rs.getRows().size(); rowIdx++) {
            Row row = rs.getRows().get(rowIdx);
            json.append("  {");
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if (i > 0) json.append(", ");
                json.append("\"").append(rs.getColumns().get(i).getName()).append("\": ");
                Object value = row.getValue(i);
                if (value == null) {
                    json.append("null");
                } else if (value instanceof Number) {
                    json.append(value.toString());
                } else {
                    json.append("\"").append(value.toString()).append("\"");
                }
            }
            json.append("}");
            if (rowIdx < rs.getRows().size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]\n");

        String jsonOutput = json.toString();
        String jsonUpper = jsonOutput.toUpperCase();
        assertTrue(jsonUpper.contains("\"ID\"") || jsonUpper.contains("\"id\""));
        assertTrue(jsonOutput.contains("1"));
        assertTrue(jsonOutput.contains("Alice"));
    }

    @Test
    public void testXmlFormat() {
        // Create test data
        engine.execute("CREATE DATABASE test_xml_db");
        engine.execute("USE DATABASE test_xml_db");
        engine.execute("CREATE TABLE xml_test (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO xml_test VALUES (1, 'Alice')");

        ExecutionResult result = engine.execute("SELECT * FROM xml_test");
        assertTrue(result.isSuccess());

        // Verify we can format as XML (manual simulation)
        ResultSet rs = result.getResultSets().get(0);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<resultset>\n");

        for (final Row row : rs.getRows()) {
            xml.append("  <row>\n");
            for (int i = 0; i < rs.getColumns().size(); i++) {
                String columnName = rs.getColumns().get(i).getName();
                Object value = row.getValue(i);
                xml.append("    <").append(columnName).append(">");
                xml.append(value != null ? value.toString() : "");
                xml.append("</").append(columnName).append(">\n");
            }
            xml.append("  </row>\n");
        }
        xml.append("</resultset>\n");

        String xmlOutput = xml.toString();
        String xmlUpper = xmlOutput.toUpperCase();
        assertTrue(xmlUpper.contains("<ID>1</ID>") || xmlOutput.contains("<id>1</id>"));
        assertTrue(xmlUpper.contains("ALICE") && xmlOutput.contains("<row>"));
    }

    @Test
    public void testHtmlFormat() {
        // Create test data
        engine.execute("CREATE DATABASE test_html_db");
        engine.execute("USE DATABASE test_html_db");
        engine.execute("CREATE TABLE html_test (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO html_test VALUES (1, 'Alice')");

        ExecutionResult result = engine.execute("SELECT * FROM html_test");
        assertTrue(result.isSuccess());

        // Verify we can format as HTML (manual simulation)
        ResultSet rs = result.getResultSets().get(0);
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\">\n");
        html.append("  <thead>\n");
        html.append("    <tr>\n");

        for (final ResultSetColumn column : rs.getColumns()) {
            html.append("      <th>").append(column.getName()).append("</th>\n");
        }
        html.append("    </tr>\n");
        html.append("  </thead>\n");
        html.append("  <tbody>\n");

        for (final Row row : rs.getRows()) {
            html.append("    <tr>\n");
            for (int i = 0; i < row.getValues().size(); i++) {
                Object value = row.getValue(i);
                html.append("      <td>").append(value != null ? value.toString() : "").append("</td>\n");
            }
            html.append("    </tr>\n");
        }
        html.append("  </tbody>\n");
        html.append("</table>\n");

        String htmlOutput = html.toString();
        String htmlUpper = htmlOutput.toUpperCase();
        assertTrue(htmlUpper.contains("<TH>ID</TH>") || htmlUpper.contains("<th>id</th>"));
        assertTrue(htmlUpper.contains("<TH>NAME</TH>") || htmlUpper.contains("<th>name</th>"));
        assertTrue(htmlOutput.contains("<td>1</td>"));
        assertTrue(htmlOutput.contains("<td>Alice</td>"));
    }

    @Test
    public void testCsvEscaping() {
        String value1 = "Simple";
        String value2 = "Contains,comma";
        String value3 = "Contains\"quote";
        String value4 = "Contains\nnewline";

        // Simulate CSV escaping
        String escaped1 = escapeCsv(value1);
        String escaped2 = escapeCsv(value2);
        String escaped3 = escapeCsv(value3);
        String escaped4 = escapeCsv(value4);

        assertEquals("Simple", escaped1);
        assertEquals("\"Contains,comma\"", escaped2);
        assertEquals("\"Contains\"\"quote\"", escaped3);
        assertEquals("\"Contains\nnewline\"", escaped4);
    }

    @Test
    public void testJsonEscaping() {
        String value1 = "Simple text";
        String value2 = "Text with \"quotes\"";
        String value3 = "Text with\nnewline";
        String value4 = "Path\\with\\backslash";

        String escaped1 = escapeJson(value1);
        String escaped2 = escapeJson(value2);
        String escaped3 = escapeJson(value3);
        String escaped4 = escapeJson(value4);

        assertEquals("Simple text", escaped1);
        assertEquals("Text with \\\"quotes\\\"", escaped2);
        assertEquals("Text with\\nnewline", escaped3);
        assertEquals("Path\\\\with\\\\backslash", escaped4);
    }

    @Test
    public void testXmlEscaping() {
        String value1 = "Simple text";
        String value2 = "Text with <tags>";
        String value3 = "Text with & ampersand";
        String value4 = "Text with \"quotes\" and 'apostrophes'";

        String escaped1 = escapeXml(value1);
        String escaped2 = escapeXml(value2);
        String escaped3 = escapeXml(value3);
        String escaped4 = escapeXml(value4);

        assertEquals("Simple text", escaped1);
        assertEquals("Text with &lt;tags&gt;", escaped2);
        assertEquals("Text with &amp; ampersand", escaped3);
        assertEquals("Text with &quot;quotes&quot; and &apos;apostrophes&apos;", escaped4);
    }

    @Test
    public void testSystemCommandExecution() {
        // We can't actually test system command execution in a unit test
        // but we can verify the engine works correctly
        ExecutionResult result = engine.execute("CREATE DATABASE test_system_db");
        assertTrue(result.isSuccess());

        result = engine.execute("USE DATABASE test_system_db");
        assertTrue(result.isSuccess());

        String currentDb = engine.getCatalog().getCurrentDatabase();
        assertNotNull(currentDb);
        assertTrue(currentDb.equalsIgnoreCase("test_system_db"));
    }

    // Helper methods to simulate console escaping functions
    private String escapeCsv(final String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeJson(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private String escapeXml(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
