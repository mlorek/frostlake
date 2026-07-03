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

import dev.frostlake.BuildInfo;
import dev.frostlake.ExecutionResult;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.utils.InfoCmp;

/**
 * Interactive console client for Frostlake SQL Engine
 * Provides a REPL interface for executing SQL commands with command history support
 */
public class ConsoleClient {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleClient.class);
    private static final String HISTORY_FILE = System.getProperty("user.home") + "/.frostlake_history";

    private final DatabaseEngine engine;
    private final Terminal terminal;
    private final LineReader reader;
    private boolean running;
    private final Map<String, String> variables;
    private OutputFormat outputFormat;
    private PrintWriter spoolWriter;
    private String spoolFile;

    public ConsoleClient() {
        this.engine = new DatabaseEngine();
        this.running = true;
        this.variables = new HashMap<String, String>();
        this.outputFormat = OutputFormat.TABLE;
        this.spoolWriter = null;
        this.spoolFile = null;

        try {
            // Create terminal
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            // Create history
            Path historyPath = Paths.get(HISTORY_FILE);
            DefaultHistory history = new DefaultHistory();

            // Create SQL completer
            SQLCompleter completer = new SQLCompleter(engine);

            // Build line reader with history support and tab completion
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(history)
                    .completer(completer)
                    .variable(LineReader.HISTORY_FILE, historyPath)
                    .option(LineReader.Option.CASE_INSENSITIVE, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)  // Disable ! history expansion so !cmd works
                    .build();

            // Load history from file
            try {
                history.attach(reader);
                history.load();
            } catch (final IOException e) {
                // History file doesn't exist yet, that's OK
                logger.debug("No history file found, starting with empty history");
            }

        } catch (final IOException e) {
            throw new RuntimeException("Failed to initialize terminal: " + e.getMessage(), e);
        }
    }

    public static void main(final String[] args) {
        ConsoleClient client = new ConsoleClient();

        // Parse arguments: -f <file> or --file <file> loads and executes a SQL file on startup
        String startupFile = null;
        for (int i = 0; i < args.length; i++) {
            if (("-f".equals(args[i]) || "--file".equals(args[i])) && i + 1 < args.length) {
                startupFile = args[i + 1];
                i++;
            } else if (args[i].startsWith("--file=")) {
                startupFile = args[i].substring("--file=".length());
            }
        }

        if (startupFile != null) {
            client.handleSourceCommand("!source " + startupFile);
        }

        client.run();
    }

    public void run() {
        printWelcome();

        while (running) {
            try {
                String sql = readCommand();
                if (sql == null || sql.trim().isEmpty()) {
                    continue;
                }

                // Check for meta-commands (SnowSQL uses ! prefix, also support \ for compatibility)
                if (sql.startsWith("!") || sql.startsWith("\\")) {
                    handleMetaCommand(sql);
                    continue;
                }

                // Substitute variables before execution
                sql = substituteVariables(sql);

                // Execute SQL
                executeSQL(sql);

            } catch (final Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        shutdown();
    }

    private void printWelcome() {
        final int width = 60;
        final String border = "═".repeat(width);
        terminal.writer().println("╔" + border + "╗");
        printBoxLine("     Frostlake SQL Engine - Interactive Console", width);
        printBoxLine("     Version " + BuildInfo.version(), width);
        terminal.writer().println("╚" + border + "╝");
        terminal.writer().println();
        terminal.writer().println("Type SQL commands followed by semicolon (;) to execute.");
        terminal.writer().println("Type !help for help, !quit to quit.");
        terminal.writer().println("Use UP/DOWN arrows to navigate command history.");
        terminal.writer().println();
        terminal.writer().flush();
    }

    /** Print one bordered banner row: {@code content} left-aligned, padded (or truncated) to {@code width}. */
    private void printBoxLine(final String content, final int width) {
        final StringBuilder sb = new StringBuilder(content);
        while (sb.length() < width) {
            sb.append(' ');
        }
        terminal.writer().println("║" + (sb.length() > width ? sb.substring(0, width) : sb.toString()) + "║");
    }

    private String readCommand() {
        StringBuilder command = new StringBuilder();
        String line;

        while (true) {
            // Print prompt
            String prompt;
            if (command.length() == 0) {
                String dbContext = getContextPrompt();
                prompt = dbContext + "> ";
            } else {
                prompt = "... ";
            }

            try {
                line = reader.readLine(prompt);
            } catch (final UserInterruptException e) {
                // Ctrl+C pressed
                if (command.length() > 0) {
                    // Cancel current multi-line command
                    terminal.writer().println("^C");
                    return "";
                } else {
                    // Exit on Ctrl+C at empty prompt
                    running = false;
                    return null;
                }
            } catch (final EndOfFileException e) {
                // Ctrl+D pressed
                running = false;
                return null;
            }

            if (line == null) {
                return null;
            }

            // Check for meta-commands on any line (SnowSQL uses ! prefix)
            // This allows !quit to work even if the user is mid-way through typing a command
            if (line.trim().startsWith("!") || line.trim().startsWith("\\")) {
                return line.trim();
            }

            command.append(line).append(" ");

            // Check if command is complete (ends with semicolon)
            if (line.trim().endsWith(";")) {
                return command.toString().trim();
            }
        }
    }

    private String getContextPrompt() {
        String database = engine.getCatalog().getCurrentDatabase();
        String schema = engine.getCatalog().getCurrentSchema();

        if (database != null && schema != null) {
            return database + "." + schema;
        } else if (database != null) {
            return database;
        } else {
            return "frostlake";
        }
    }

    private void handleMetaCommand(final String command) {
        String cmd = command.toLowerCase().trim();

        switch (cmd) {
            // Exit commands (SnowSQL: !quit, !exit)
            case "!quit":
            case "!exit":
            case "\\q":
            case "\\quit":
            case "\\exit":
                terminal.writer().println("Goodbye!");
                terminal.writer().flush();
                running = false;
                break;

            // Help commands (SnowSQL: !help)
            case "!help":
            case "\\h":
            case "\\help":
            case "\\?":
                printHelp();
                break;

            // Abort/Cancel command (SnowSQL: !abort)
            case "!abort":
                terminal.writer().println("Command aborted.");
                terminal.writer().flush();
                break;

            // Clear screen
            case "!clear":
            case "\\c":
            case "\\clear":
                clearScreen();
                break;

            // History command
            case "!history":
            case "\\history":
                printHistory();
                break;

            // Show databases (SnowSQL: !databases or SHOW DATABASES)
            case "!databases":
            case "\\db":
            case "\\databases":
                executeSQL("SHOW DATABASES;");
                break;

            // Show tables (SnowSQL: !tables or SHOW TABLES)
            case "!tables":
            case "\\dt":
            case "\\tables":
                executeSQL("SHOW TABLES;");
                break;

            // Show views
            case "!views":
            case "\\dv":
            case "\\views":
                executeSQL("SHOW VIEWS;");
                break;

            // Show functions
            case "!functions":
            case "\\df":
            case "\\functions":
                executeSQL("SHOW FUNCTIONS;");
                break;

            // Show procedures
            case "!procedures":
            case "\\dp":
            case "\\procedures":
                executeSQL("SHOW PROCEDURES;");
                break;

            // List databases (INFORMATION_SCHEMA query)
            case "!list":
            case "\\l":
            case "\\list":
                executeSQL("SELECT * FROM INFORMATION_SCHEMA.DATABASES;");
                break;

            default:
                // Check for parameterized commands
                if (cmd.startsWith("!set ") || cmd.startsWith("\\set ")) {
                    handleSetCommand(command);
                } else if (cmd.startsWith("!print ") || cmd.startsWith("\\print ")) {
                    handlePrintCommand(command);
                } else if (cmd.startsWith("!define ") || cmd.startsWith("\\define ")) {
                    handleDefineCommand(command);
                } else if (cmd.startsWith("!source ") || cmd.startsWith("\\source ") || cmd.startsWith("\\i ")) {
                    handleSourceCommand(command);
                } else if (cmd.startsWith("!spool ") || cmd.startsWith("\\spool ") || cmd.startsWith("\\o ")) {
                    handleSpoolCommand(command);
                } else if (cmd.startsWith("!system ") || cmd.startsWith("\\system ") || cmd.startsWith("\\! ")) {
                    handleSystemCommand(command);
                } else if (cmd.equals("!set") || cmd.equals("\\set")) {
                    handleSetCommand(command);
                } else if (cmd.equals("!print") || cmd.equals("\\print")) {
                    handlePrintCommand(command);
                } else if (cmd.equals("!options") || cmd.equals("\\options")) {
                    handleOptionsCommand();
                } else if (cmd.equals("!spool") || cmd.equals("\\spool") || cmd.equals("\\o")) {
                    handleSpoolCommand(command);
                } else {
                    terminal.writer().println("Unknown command: " + command);
                    terminal.writer().println("Type !help for help.");
                    terminal.writer().flush();
                }
        }
    }

    private void printHelp() {
        terminal.writer().println();
        terminal.writer().println("Commands (SnowSQL-style with ! prefix):");
        terminal.writer().println("  !quit, !exit           Exit the console");
        terminal.writer().println("  !help                  Show this help");
        terminal.writer().println("  !abort                 Abort current command");
        terminal.writer().println("  !clear                 Clear screen");
        terminal.writer().println("  !history               Show command history");
        terminal.writer().println();
        terminal.writer().println("Variables and settings:");
        terminal.writer().println("  !set [NAME VALUE]      Set variable or show all variables");
        terminal.writer().println("  !define NAME VALUE     Define substitution variable");
        terminal.writer().println("  !print [VAR]           Print variable value or all variables");
        terminal.writer().println("  !options               Display connection options");
        terminal.writer().println();
        terminal.writer().println("File operations:");
        terminal.writer().println("  !source FILE           Execute SQL from file");
        terminal.writer().println("  !spool [FILE]          Write output to file (or stop spooling)");
        terminal.writer().println("  !system COMMAND        Execute system shell command");
        terminal.writer().println();
        terminal.writer().println("Quick queries:");
        terminal.writer().println("  !databases             Show all databases (SHOW DATABASES)");
        terminal.writer().println("  !tables                Show tables in current database (SHOW TABLES)");
        terminal.writer().println("  !views                 Show views in current database (SHOW VIEWS)");
        terminal.writer().println("  !functions             Show functions in current database (SHOW FUNCTIONS)");
        terminal.writer().println("  !procedures            Show procedures in current database (SHOW PROCEDURES)");
        terminal.writer().println("  !list                  List all databases (INFORMATION_SCHEMA)");
        terminal.writer().println();
        terminal.writer().println("Compatibility:");
        terminal.writer().println("  Commands also work with \\ prefix (psql-style)");
        terminal.writer().println("  Example: \\quit, \\help, \\databases");
        terminal.writer().println();
        terminal.writer().println("Navigation:");
        terminal.writer().println("  UP/DOWN arrows         Navigate command history");
        terminal.writer().println("  Ctrl+C                 Cancel current command / Exit");
        terminal.writer().println("  Ctrl+D                 Exit");
        terminal.writer().println();
        terminal.writer().println("SQL commands:");
        terminal.writer().println("  Enter any SQL statement followed by semicolon (;)");
        terminal.writer().println("  Multi-line statements are supported");
        terminal.writer().println("  Use &VARIABLE syntax for variable substitution");
        terminal.writer().println();
        terminal.writer().println("Examples:");
        terminal.writer().println("  CREATE DATABASE mydb;");
        terminal.writer().println("  USE DATABASE mydb;");
        terminal.writer().println("  CREATE TABLE users (final id INTEGER, final name VARCHAR);");
        terminal.writer().println("  SELECT * FROM users;");
        terminal.writer().println("  !define DB_NAME testdb");
        terminal.writer().println("  USE DATABASE &DB_NAME;");
        terminal.writer().println("  !set output_format csv");
        terminal.writer().println("  !spool output.txt");
        terminal.writer().println("  !databases             # Show all databases");
        terminal.writer().println("  !quit                  # Exit console");
        terminal.writer().println();
        terminal.writer().flush();
    }

    private void clearScreen() {
        // Use JLine's terminal clear
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
        printWelcome();
    }

    private void printHistory() {
        History history = reader.getHistory();
        terminal.writer().println();
        terminal.writer().println("Command History:");

        int index = 1;
        for (final History.Entry entry : history) {
            terminal.writer().printf("%3d: %s%n", index++, entry.line());
        }

        terminal.writer().println();
        terminal.writer().println("Total: " + history.size() + " commands");
        terminal.writer().println("History file: " + HISTORY_FILE);
        terminal.writer().println();
        terminal.writer().flush();
    }

    private void executeSQL(final String sql) {
        // Safety guard: meta-commands should never reach here, but handle them if they do
        String trimmedCheck = sql.trim();
        if (trimmedCheck.startsWith("!") || trimmedCheck.startsWith("\\")) {
            handleMetaCommand(trimmedCheck);
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Remove trailing semicolon if present
            String trimmedSql = sql.trim();
            if (trimmedSql.endsWith(";")) {
                trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1).trim();
            }

            ExecutionResult result = engine.execute(trimmedSql);
            long endTime = System.currentTimeMillis();

            if (!result.isSuccess()) {
                String errorMsg = "ERROR: " + result.getErrorMessage();
                terminal.writer().println(errorMsg);
                terminal.writer().println();
                terminal.writer().flush();
                if (spoolWriter != null) {
                    spoolWriter.println(errorMsg);
                    spoolWriter.println();
                    spoolWriter.flush();
                }
                return;
            }

            List<ResultSet> results = result.getResultSets();
            if (results == null || results.isEmpty()) {
                String okMsg = "OK (" + (endTime - startTime) + " ms)";
                terminal.writer().println(okMsg);
                if (spoolWriter != null) {
                    spoolWriter.println(okMsg);
                }
            } else {
                for (final ResultSet rs : results) {
                    if (rs != null) {
                        printResultSet(rs);
                        String rowMsg = rs.getRowCount() + " row(s) returned (" + (endTime - startTime) + " ms)";
                        terminal.writer().println(rowMsg);
                        if (spoolWriter != null) {
                            spoolWriter.println(rowMsg);
                        }
                    }
                }
            }

            // Print query ID if available
            if (result.getQueryId() != null) {
                String queryIdMsg = "query_id: " + result.getQueryId();
                terminal.writer().println(queryIdMsg);
                if (spoolWriter != null) {
                    spoolWriter.println(queryIdMsg);
                }
            }

            terminal.writer().println();
            terminal.writer().flush();
            if (spoolWriter != null) {
                spoolWriter.println();
                spoolWriter.flush();
            }

        } catch (final Exception e) {
            String errorMsg = "ERROR: " + e.getMessage();
            terminal.writer().println(errorMsg);
            terminal.writer().println();
            terminal.writer().flush();
            if (spoolWriter != null) {
                spoolWriter.println(errorMsg);
                spoolWriter.println();
                spoolWriter.flush();
            }
        }
    }

    private void printResultSet(final ResultSet rs) {
        if (rs.getRowCount() == 0) {
            String noRowsMsg = "(No rows)";
            terminal.writer().println(noRowsMsg);
            if (spoolWriter != null) {
                spoolWriter.println(noRowsMsg);
            }
            return;
        }

        switch (outputFormat) {
            case CSV:
                printResultSetCSV(rs);
                break;
            case JSON:
                printResultSetJSON(rs);
                break;
            case XML:
                printResultSetXML(rs);
                break;
            case HTML:
                printResultSetHTML(rs);
                break;
            default:
                printResultSetTable(rs);
                break;
        }
    }

    private void printResultSetTable(final ResultSet rs) {
        List<ResultSetColumn> columns = rs.getColumns();
        List<Row> rows = rs.getRows();

        // Calculate column widths
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).getName().length();
        }

        for (final Row row : rows) {
            for (int i = 0; i < row.getValues().size(); i++) {
                Object value = row.getValue(i);
                String strValue = formatValue(value);
                widths[i] = Math.max(widths[i], strValue.length());
            }
        }

        // Print header separator
        printSeparator(widths);

        // Print column headers
        terminal.writer().print("│");
        if (spoolWriter != null) spoolWriter.print("│");
        for (int i = 0; i < columns.size(); i++) {
            String cell = " " + padRight(columns.get(i).getName(), widths[i]) + " │";
            terminal.writer().print(cell);
            if (spoolWriter != null) spoolWriter.print(cell);
        }
        terminal.writer().println();
        if (spoolWriter != null) spoolWriter.println();

        // Print separator
        printSeparator(widths);

        // Print rows
        for (final Row row : rows) {
            terminal.writer().print("│");
            if (spoolWriter != null) spoolWriter.print("│");
            for (int i = 0; i < row.getValues().size(); i++) {
                Object value = row.getValue(i);
                String strValue = formatValue(value);
                String cell = " " + padRight(strValue, widths[i]) + " │";
                terminal.writer().print(cell);
                if (spoolWriter != null) spoolWriter.print(cell);
            }
            terminal.writer().println();
            if (spoolWriter != null) spoolWriter.println();
        }

        // Print bottom separator
        printSeparator(widths);
    }

    private void printResultSetCSV(final ResultSet rs) {
        List<ResultSetColumn> columns = rs.getColumns();
        List<Row> rows = rs.getRows();

        // Print header
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) header.append(",");
            header.append(escapeCsv(columns.get(i).getName()));
        }
        terminal.writer().println(header.toString());
        if (spoolWriter != null) spoolWriter.println(header.toString());

        // Print rows
        for (final Row row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) line.append(",");
                Object value = row.getValue(i);
                line.append(escapeCsv(formatValue(value)));
            }
            terminal.writer().println(line.toString());
            if (spoolWriter != null) spoolWriter.println(line.toString());
        }
    }

    private void printResultSetJSON(final ResultSet rs) {
        List<ResultSetColumn> columns = rs.getColumns();
        List<Row> rows = rs.getRows();

        terminal.writer().println("[");
        if (spoolWriter != null) spoolWriter.println("[");

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row row = rows.get(rowIdx);
            StringBuilder line = new StringBuilder("  {");

            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) line.append(", ");
                line.append("\"").append(escapeJson(columns.get(i).getName())).append("\": ");
                Object value = row.getValue(i);
                if (value == null) {
                    line.append("null");
                } else if (value instanceof Number) {
                    line.append(value.toString());
                } else if (value instanceof Boolean) {
                    line.append(value.toString());
                } else {
                    line.append("\"").append(escapeJson(value.toString())).append("\"");
                }
            }
            line.append("}");
            if (rowIdx < rows.size() - 1) {
                line.append(",");
            }

            terminal.writer().println(line.toString());
            if (spoolWriter != null) spoolWriter.println(line.toString());
        }

        terminal.writer().println("]");
        if (spoolWriter != null) spoolWriter.println("]");
    }

    private void printResultSetXML(final ResultSet rs) {
        List<ResultSetColumn> columns = rs.getColumns();
        List<Row> rows = rs.getRows();

        terminal.writer().println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        terminal.writer().println("<resultset>");
        if (spoolWriter != null) {
            spoolWriter.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            spoolWriter.println("<resultset>");
        }

        for (final Row row : rows) {
            terminal.writer().println("  <row>");
            if (spoolWriter != null) spoolWriter.println("  <row>");

            for (int i = 0; i < columns.size(); i++) {
                String columnName = columns.get(i).getName();
                Object value = row.getValue(i);
                String line = "    <" + columnName + ">" + escapeXml(formatValue(value)) + "</" + columnName + ">";
                terminal.writer().println(line);
                if (spoolWriter != null) spoolWriter.println(line);
            }

            terminal.writer().println("  </row>");
            if (spoolWriter != null) spoolWriter.println("  </row>");
        }

        terminal.writer().println("</resultset>");
        if (spoolWriter != null) spoolWriter.println("</resultset>");
    }

    private void printResultSetHTML(final ResultSet rs) {
        List<ResultSetColumn> columns = rs.getColumns();
        List<Row> rows = rs.getRows();

        terminal.writer().println("<table border=\"1\">");
        terminal.writer().println("  <thead>");
        terminal.writer().println("    <tr>");
        if (spoolWriter != null) {
            spoolWriter.println("<table border=\"1\">");
            spoolWriter.println("  <thead>");
            spoolWriter.println("    <tr>");
        }

        for (final ResultSetColumn column : columns) {
            String line = "      <th>" + escapeHtml(column.getName()) + "</th>";
            terminal.writer().println(line);
            if (spoolWriter != null) spoolWriter.println(line);
        }

        terminal.writer().println("    </tr>");
        terminal.writer().println("  </thead>");
        terminal.writer().println("  <tbody>");
        if (spoolWriter != null) {
            spoolWriter.println("    </tr>");
            spoolWriter.println("  </thead>");
            spoolWriter.println("  <tbody>");
        }

        for (final Row row : rows) {
            terminal.writer().println("    <tr>");
            if (spoolWriter != null) spoolWriter.println("    <tr>");

            for (int i = 0; i < row.getValues().size(); i++) {
                Object value = row.getValue(i);
                String line = "      <td>" + escapeHtml(formatValue(value)) + "</td>";
                terminal.writer().println(line);
                if (spoolWriter != null) spoolWriter.println(line);
            }

            terminal.writer().println("    </tr>");
            if (spoolWriter != null) spoolWriter.println("    </tr>");
        }

        terminal.writer().println("  </tbody>");
        terminal.writer().println("</table>");
        if (spoolWriter != null) {
            spoolWriter.println("  </tbody>");
            spoolWriter.println("</table>");
        }
    }

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

    private String escapeHtml(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }

    private void printSeparator(final int[] widths) {
        terminal.writer().print("┼");
        if (spoolWriter != null) spoolWriter.print("┼");
        for (final int width : widths) {
            String sep = "─" + "─".repeat(width) + "─┼";
            terminal.writer().print(sep);
            if (spoolWriter != null) spoolWriter.print(sep);
        }
        terminal.writer().println();
        if (spoolWriter != null) spoolWriter.println();
    }

    private String formatValue(final Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof Instant) {
            return value.toString();
        } else {
            return value.toString();
        }
    }

    private String padRight(final String s, final int n) {
        if (s.length() >= n) {
            return s;
        }
        return s + " ".repeat(n - s.length());
    }

    private String substituteVariables(final String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        String result = sql;
        for (final Map.Entry<String, String> entry : variables.entrySet()) {
            String varName = "&" + entry.getKey();
            result = result.replace(varName, entry.getValue());
        }
        return result;
    }

    private void handleSetCommand(final String command) {
        String[] parts = command.trim().split("\\s+", 3);

        if (parts.length == 1) {
            // Show all settings
            terminal.writer().println();
            terminal.writer().println("Current settings:");
            terminal.writer().println("  output_format = " + outputFormat.displayName());
            if (spoolFile != null) {
                terminal.writer().println("  spool_file = " + spoolFile);
            }
            terminal.writer().println();
            terminal.writer().println("Variables:");
            if (variables.isEmpty()) {
                terminal.writer().println("  (none)");
            } else {
                for (final Map.Entry<String, String> entry : variables.entrySet()) {
                    terminal.writer().println("  " + entry.getKey() + " = " + entry.getValue());
                }
            }
            terminal.writer().println();
            terminal.writer().flush();
        } else if (parts.length == 3) {
            // Set a variable
            String name = parts[1];
            String value = parts[2];

            // Handle special settings
            if (name.equalsIgnoreCase("output_format")) {
                final OutputFormat parsed = OutputFormat.fromString(value);
                if (parsed != null) {
                    outputFormat = parsed;
                    terminal.writer().println("Output format set to: " + outputFormat.displayName());
                } else {
                    terminal.writer().println("ERROR: Invalid output format. Valid values: table, csv, json, xml, html");
                }
            } else {
                variables.put(name, value);
                terminal.writer().println("Variable '" + name + "' set to: " + value);
            }
            terminal.writer().flush();
        } else {
            terminal.writer().println("Usage: !set [NAME VALUE]");
            terminal.writer().flush();
        }
    }

    private void handleDefineCommand(final String command) {
        String[] parts = command.trim().split("\\s+", 3);

        if (parts.length == 3) {
            String name = parts[1];
            String value = parts[2];
            variables.put(name, value);
            terminal.writer().println("Variable '" + name + "' defined: " + value);
            terminal.writer().flush();
        } else {
            terminal.writer().println("Usage: !define NAME VALUE");
            terminal.writer().flush();
        }
    }

    private void handlePrintCommand(final String command) {
        String[] parts = command.trim().split("\\s+", 2);

        if (parts.length == 1) {
            // Print all variables
            terminal.writer().println();
            terminal.writer().println("Variables:");
            if (variables.isEmpty()) {
                terminal.writer().println("  (none defined)");
            } else {
                for (final Map.Entry<String, String> entry : variables.entrySet()) {
                    terminal.writer().println("  " + entry.getKey() + " = " + entry.getValue());
                }
            }
            terminal.writer().println();
            terminal.writer().flush();
        } else {
            // Print specific variable
            String varName = parts[1];
            if (variables.containsKey(varName)) {
                terminal.writer().println(varName + " = " + variables.get(varName));
            } else {
                terminal.writer().println("Variable '" + varName + "' not defined");
            }
            terminal.writer().flush();
        }
    }

    private void handleOptionsCommand() {
        terminal.writer().println();
        terminal.writer().println("Connection Options:");
        terminal.writer().println("  Database: " + (engine.getCatalog().getCurrentDatabase() != null ?
                                                   engine.getCatalog().getCurrentDatabase() : "(none)"));
        terminal.writer().println("  Schema: " + (engine.getCatalog().getCurrentSchema() != null ?
                                                 engine.getCatalog().getCurrentSchema() : "(none)"));
        terminal.writer().println();
        terminal.writer().println("Display Options:");
        terminal.writer().println("  output_format: " + outputFormat.displayName());
        terminal.writer().println();
        terminal.writer().println("File Options:");
        if (spoolFile != null) {
            terminal.writer().println("  spool: ON (writing to " + spoolFile + ")");
        } else {
            terminal.writer().println("  spool: OFF");
        }
        terminal.writer().println();
        terminal.writer().flush();
    }

    private void handleSourceCommand(final String command) {
        String[] parts = command.trim().split("\\s+", 2);

        if (parts.length < 2) {
            terminal.writer().println("Usage: !source FILE");
            terminal.writer().flush();
            return;
        }

        String fileName = parts[1];

        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(fileName));
            terminal.writer().println("Executing SQL from file: " + fileName);
            terminal.writer().println();

            StringBuilder sqlBuffer = new StringBuilder();
            String line;
            int lineCount = 0;
            boolean inDollarQuote = false; // inside $$ ... $$

            while ((line = fileReader.readLine()) != null) {
                lineCount++;

                // Skip blank lines and single-line comments only when not inside a block
                if (!inDollarQuote) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#")) {
                        continue;
                    }

                    // Handle meta-commands
                    if (trimmed.startsWith("!") || trimmed.startsWith("\\")) {
                        if (sqlBuffer.length() > 0) {
                            String sql = sqlBuffer.toString().trim();
                            if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);
                            terminal.writer().println("> " + sql);
                            executeSQL(substituteVariables(sql));
                            sqlBuffer.setLength(0);
                        }
                        terminal.writer().println("> " + trimmed);
                        handleMetaCommand(trimmed);
                        continue;
                    }
                }

                sqlBuffer.append(line).append("\n");

                // Track $$ quoting — toggle on each occurrence
                String bufStr = sqlBuffer.toString();
                int dollarCount = 0;
                for (int i = 0; i < bufStr.length() - 1; i++) {
                    if (bufStr.charAt(i) == '$' && bufStr.charAt(i + 1) == '$') {
                        dollarCount++;
                        i++; // skip second $
                    }
                }
                inDollarQuote = (dollarCount % 2 != 0);

                // Statement is complete when a ; appears at top level (outside $$ blocks)
                if (!inDollarQuote) {
                    String trimmedLine = line.trim();
                    if (trimmedLine.endsWith(";")) {
                        String sql = sqlBuffer.toString().trim();
                        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);
                        terminal.writer().println("> " + sql.replace("\n", " "));
                        executeSQL(substituteVariables(sql));
                        sqlBuffer.setLength(0);
                    }
                }
            }

            // Execute any remaining SQL
            if (sqlBuffer.length() > 0) {
                String sql = sqlBuffer.toString().trim();
                if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);
                terminal.writer().println("> " + sql.replace("\n", " "));
                executeSQL(substituteVariables(sql));
            }

            fileReader.close();
            terminal.writer().println();
            terminal.writer().println("Finished executing " + lineCount + " lines from " + fileName);
            terminal.writer().println();
            terminal.writer().flush();

        } catch (final IOException e) {
            terminal.writer().println("ERROR: Failed to read file: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private void handleSpoolCommand(final String command) {
        String[] parts = command.trim().split("\\s+", 2);

        if (parts.length == 1) {
            // Stop spooling
            if (spoolWriter != null) {
                spoolWriter.close();
                spoolWriter = null;
                terminal.writer().println("Spooling stopped. Output written to: " + spoolFile);
                spoolFile = null;
            } else {
                terminal.writer().println("Spooling is not active");
            }
            terminal.writer().flush();
        } else {
            // Start spooling
            String fileName = parts[1];

            // Stop existing spool if active
            if (spoolWriter != null) {
                spoolWriter.close();
            }

            try {
                spoolFile = fileName;
                spoolWriter = new PrintWriter(new BufferedWriter(new FileWriter(spoolFile, false)));
                terminal.writer().println("Spooling to file: " + spoolFile);
                terminal.writer().flush();
            } catch (final IOException e) {
                terminal.writer().println("ERROR: Failed to open spool file: " + e.getMessage());
                spoolWriter = null;
                spoolFile = null;
                terminal.writer().flush();
            }
        }
    }

    private void handleSystemCommand(final String command) {
        String[] parts = command.trim().split("\\s+", 2);

        if (parts.length < 2) {
            terminal.writer().println("Usage: !system COMMAND");
            terminal.writer().flush();
            return;
        }

        String shellCommand = parts[1];

        try {
            Process process = Runtime.getRuntime().exec(shellCommand);

            // Read command output
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            terminal.writer().println();

            String line;
            while ((line = stdInput.readLine()) != null) {
                terminal.writer().println(line);
            }

            while ((line = stdError.readLine()) != null) {
                terminal.writer().println(line);
            }

            int exitCode = process.waitFor();
            terminal.writer().println();
            terminal.writer().println("Command completed with exit code: " + exitCode);
            terminal.writer().println();
            terminal.writer().flush();

        } catch (final IOException e) {
            terminal.writer().println("ERROR: Failed to execute system command: " + e.getMessage());
            terminal.writer().flush();
        } catch (final InterruptedException e) {
            terminal.writer().println("ERROR: Command execution interrupted: " + e.getMessage());
            terminal.writer().flush();
        }
    }

    private void shutdown() {
        // Close spool file if open
        if (spoolWriter != null) {
            spoolWriter.close();
            spoolWriter = null;
        }

        try {
            // Save history before shutdown
            reader.getHistory().save();
        } catch (final IOException e) {
            logger.warn("Failed to save history: " + e.getMessage());
        }

        try {
            terminal.close();
        } catch (final IOException e) {
            // Ignore
        }

        engine.shutdown();
    }
}
