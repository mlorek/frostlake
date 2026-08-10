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
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.utils.InfoCmp;

/**
 * JDBC-based interactive console client for Frostlake SQL Engine
 * Connects via JDBC to HTTP server instead of using direct API
 */
public class JdbcConsoleClient {
    private static final Logger logger = LoggerFactory.getLogger(JdbcConsoleClient.class);
    private static final String HISTORY_FILE = System.getProperty("user.home") + "/.engine_jdbc_history";
    private static final String DEFAULT_URL = "jdbc:frostlake://localhost:8080";

    private Connection connection;
    private final Terminal terminal;
    private final LineReader reader;
    private boolean running;
    private final Map<String, String> variables;
    private OutputFormat outputFormat;
    private PrintWriter spoolWriter;
    private String spoolFile;
    private final String jdbcUrl;

    public JdbcConsoleClient(final String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        this.running = true;
        this.variables = new HashMap<String, String>();
        this.outputFormat = OutputFormat.TABLE;
        this.spoolWriter = null;
        this.spoolFile = null;

        try {
            // Load JDBC driver
            Class.forName("dev.frostlake.jdbc.DatabaseDriver");

            // Create terminal
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            // Create history
            final Path historyPath = Paths.get(HISTORY_FILE);
            final DefaultHistory history = new DefaultHistory();

            // Build line reader with history support
            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(history)
                    .variable(LineReader.HISTORY_FILE, historyPath)
                    .option(LineReader.Option.CASE_INSENSITIVE, true)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)  // Disable ! history expansion so !cmd works
                    .build();

            // Load history from file
            try {
                history.attach(reader);
                history.load();
            } catch (final IOException e) {
                logger.debug("No history file found, starting with empty history");
            }

            // Connect to database
            connect();

        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("JDBC driver not found: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to initialize terminal: " + e.getMessage(), e);
        }
    }

    private void connect() {
        try {
            final Properties props = new Properties();
            this.connection = DriverManager.getConnection(jdbcUrl, props);
            terminal.writer().println("Connected to: " + jdbcUrl);
            terminal.writer().flush();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    public static void main(final String[] args) {
        String url = DEFAULT_URL;
        if (args.length > 0) {
            url = args[0];
        }
        final JdbcConsoleClient client = new JdbcConsoleClient(url);
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

                if (sql.startsWith("!") || sql.startsWith("\\")) {
                    handleMetaCommand(sql);
                    continue;
                }

                sql = substituteVariables(sql);
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
        printBoxLine("     SQL Engine - JDBC Console", width);
        printBoxLine("     Version " + BuildInfo.version(), width);
        terminal.writer().println("╚" + border + "╝");
        terminal.writer().println();
        terminal.writer().println("Connected via JDBC to: " + jdbcUrl);
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
        final StringBuilder command = new StringBuilder();
        String line;

        while (true) {
            final String prompt;
            if (command.length() == 0) {
                final String dbContext = getContextPrompt();
                prompt = dbContext + "> ";
            } else {
                prompt = "... ";
            }

            try {
                line = reader.readLine(prompt);
            } catch (final UserInterruptException e) {
                if (command.length() > 0) {
                    terminal.writer().println("^C");
                    return "";
                } else {
                    running = false;
                    return null;
                }
            } catch (final EndOfFileException e) {
                running = false;
                return null;
            }

            if (line == null) {
                return null;
            }

            if (command.length() == 0 && (line.trim().startsWith("!") || line.trim().startsWith("\\"))) {
                return line.trim();
            }

            command.append(line).append(" ");

            if (line.trim().endsWith(";")) {
                return command.toString().trim();
            }
        }
    }

    private String getContextPrompt() {
        try {
            final String catalog = connection.getCatalog();
            final String schema = connection.getSchema();

            if (catalog != null && schema != null) {
                return catalog + "." + schema;
            } else if (catalog != null) {
                return catalog;
            } else {
                return "frostlake";
            }
        } catch (final SQLException e) {
            return "frostlake";
        }
    }

    private String substituteVariables(final String sql) {
        String result = sql;
        for (final Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("&" + entry.getKey(), entry.getValue());
        }
        return result;
    }

    private void handleMetaCommand(final String command) {
        final String cmd = command.toLowerCase().trim();

        if (cmd.equals("!quit") || cmd.equals("!exit") || cmd.equals("\\q") || cmd.equals("\\quit")) {
            running = false;
            terminal.writer().println("Goodbye!");
            terminal.writer().flush();
        } else if (cmd.equals("!help") || cmd.equals("\\?") || cmd.equals("\\h")) {
            printHelp();
        } else if (cmd.equals("!clear") || cmd.equals("\\clear")) {
            clearScreen();
        } else if (cmd.equals("!history") || cmd.equals("\\history")) {
            printHistory();
        } else if (cmd.equals("!databases") || cmd.equals("\\databases") || cmd.equals("\\l")) {
            executeSQL("SHOW DATABASES;");
        } else if (cmd.equals("!tables") || cmd.equals("\\dt") || cmd.equals("\\tables")) {
            executeSQL("SHOW TABLES;");
        } else if (cmd.equals("!views") || cmd.equals("\\dv") || cmd.equals("\\views")) {
            executeSQL("SHOW VIEWS;");
        } else if (cmd.startsWith("!set ") || cmd.startsWith("\\set ")) {
            handleSetCommand(command);
        } else if (cmd.startsWith("!define ") || cmd.startsWith("\\define ")) {
            handleDefineCommand(command);
        } else if (cmd.startsWith("!source ") || cmd.startsWith("\\source ") || cmd.startsWith("\\i ")) {
            handleSourceCommand(command);
        } else if (cmd.startsWith("!spool ") || cmd.startsWith("\\spool ") || cmd.startsWith("\\o ")) {
            handleSpoolCommand(command);
        } else if (cmd.equals("!set") || cmd.equals("\\set")) {
            handleSetCommand(command);
        } else if (cmd.equals("!spool") || cmd.equals("\\spool") || cmd.equals("\\o")) {
            handleSpoolCommand(command);
        } else {
            terminal.writer().println("Unknown command: " + command);
            terminal.writer().println("Type !help for help.");
            terminal.writer().flush();
        }
    }

    private void printHelp() {
        terminal.writer().println();
        terminal.writer().println("JDBC Console Commands:");
        terminal.writer().println("  !quit, !exit           Exit the console");
        terminal.writer().println("  !help                  Show this help");
        terminal.writer().println("  !clear                 Clear screen");
        terminal.writer().println("  !history               Show command history");
        terminal.writer().println();
        terminal.writer().println("Variables and settings:");
        terminal.writer().println("  !set [NAME VALUE]      Set variable or show all variables");
        terminal.writer().println("  !define NAME VALUE     Define substitution variable");
        terminal.writer().println();
        terminal.writer().println("File operations:");
        terminal.writer().println("  !source FILE           Execute SQL from file");
        terminal.writer().println("  !spool [FILE]          Write output to file (or stop spooling)");
        terminal.writer().println();
        terminal.writer().println("Quick queries:");
        terminal.writer().println("  !databases             Show all databases");
        terminal.writer().println("  !tables                Show tables in current database");
        terminal.writer().println("  !views                 Show views in current database");
        terminal.writer().println();
        terminal.writer().println("Navigation:");
        terminal.writer().println("  UP/DOWN arrows         Navigate command history");
        terminal.writer().println("  Ctrl+C                 Cancel current command / Exit");
        terminal.writer().println("  Ctrl+D                 Exit");
        terminal.writer().println();
        terminal.writer().flush();
    }

    private void clearScreen() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
        printWelcome();
    }

    private void printHistory() {
        final History history = reader.getHistory();
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

    private void handleSetCommand(final String command) {
        final String[] parts = command.split("\\s+", 3);

        if (parts.length == 1) {
            if (variables.isEmpty()) {
                terminal.writer().println("No variables defined.");
            } else {
                terminal.writer().println("Variables:");
                for (final Map.Entry<String, String> entry : variables.entrySet()) {
                    terminal.writer().println("  " + entry.getKey() + " = " + entry.getValue());
                }
            }
            terminal.writer().println();
            terminal.writer().flush();
        } else if (parts.length == 3) {
            final String varName = parts[1];
            final String varValue = parts[2];
            if (varName.equalsIgnoreCase("output_format")) {
                final OutputFormat parsed = OutputFormat.fromString(varValue);
                if (parsed != null) {
                    outputFormat = parsed;
                    terminal.writer().println("Output format set to: " + outputFormat.displayName());
                } else {
                    terminal.writer().println("ERROR: Invalid output format. Valid values: table, csv, json, xml, html");
                }
            } else {
                variables.put(varName, varValue);
                terminal.writer().println("Variable set: " + varName + " = " + varValue);
            }
            terminal.writer().println();
            terminal.writer().flush();
        }
    }

    private void handleDefineCommand(final String command) {
        final String[] parts = command.split("\\s+", 3);
        if (parts.length == 3) {
            final String varName = parts[1];
            final String varValue = parts[2];
            variables.put(varName, varValue);
            terminal.writer().println("Variable defined: " + varName + " = " + varValue);
            terminal.writer().println();
            terminal.writer().flush();
        }
    }

    private void handleSourceCommand(final String command) {
        final String[] parts = command.split("\\s+", 2);
        if (parts.length < 2) {
            terminal.writer().println("Usage: !source <filename>");
            terminal.writer().println();
            terminal.writer().flush();
            return;
        }

        final String filename = parts[1];
        try {
            final BufferedReader fileReader = new BufferedReader(new FileReader(filename));
            StringBuilder sqlBuffer = new StringBuilder();
            String line;

            while ((line = fileReader.readLine()) != null) {
                if (line.trim().startsWith("--") || line.trim().isEmpty()) {
                    continue;
                }
                sqlBuffer.append(line).append(" ");
                if (line.trim().endsWith(";")) {
                    final String sql = sqlBuffer.toString().trim();
                    terminal.writer().println("> " + sql);
                    terminal.writer().flush();
                    executeSQL(sql);
                    sqlBuffer = new StringBuilder();
                }
            }

            fileReader.close();
            terminal.writer().println("Source file executed: " + filename);
            terminal.writer().println();
            terminal.writer().flush();

        } catch (final IOException e) {
            terminal.writer().println("Error reading file: " + e.getMessage());
            terminal.writer().println();
            terminal.writer().flush();
        }
    }

    private void handleSpoolCommand(final String command) {
        final String[] parts = command.split("\\s+", 2);

        if (parts.length == 1) {
            if (spoolWriter != null) {
                spoolWriter.close();
                spoolWriter = null;
                terminal.writer().println("Spooling stopped. Output closed: " + spoolFile);
                spoolFile = null;
            } else {
                terminal.writer().println("Not currently spooling.");
            }
            terminal.writer().println();
            terminal.writer().flush();
            return;
        }

        final String filename = parts[1];
        try {
            if (spoolWriter != null) {
                spoolWriter.close();
            }
            spoolWriter = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
            spoolFile = filename;
            terminal.writer().println("Spooling to file: " + filename);
            terminal.writer().println();
            terminal.writer().flush();
        } catch (final IOException e) {
            terminal.writer().println("Error opening spool file: " + e.getMessage());
            terminal.writer().println();
            terminal.writer().flush();
        }
    }

    private void executeSQL(final String sql) {
        final long startTime = System.currentTimeMillis();

        try {
            String trimmedSql = sql.trim();
            if (trimmedSql.endsWith(";")) {
                trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1).trim();
            }

            final Statement stmt = connection.createStatement();
            final boolean hasResultSet = stmt.execute(trimmedSql);
            final long endTime = System.currentTimeMillis();

            if (hasResultSet) {
                final ResultSet rs = stmt.getResultSet();
                // The printer never rewinds and reports how many rows it rendered — result sets
                // are forward-only on live's driver and on the direct transport.
                final int rowCount = new ResultSetPrinter(terminal.writer(), spoolWriter).print(rs, outputFormat);
                final String rowMsg = rowCount + " row(s) returned (" + (endTime - startTime) + " ms)";
                terminal.writer().println(rowMsg);
                if (spoolWriter != null) {
                    spoolWriter.println(rowMsg);
                }
                rs.close();
            } else {
                final int updateCount = stmt.getUpdateCount();
                final String okMsg = "OK, " + updateCount + " row(s) affected (" + (endTime - startTime) + " ms)";
                terminal.writer().println(okMsg);
                if (spoolWriter != null) {
                    spoolWriter.println(okMsg);
                }
            }

            terminal.writer().println();
            terminal.writer().flush();
            if (spoolWriter != null) {
                spoolWriter.println();
                spoolWriter.flush();
            }

            stmt.close();

        } catch (final SQLException e) {
            final String errorMsg = "ERROR: " + e.getMessage();
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

    private void shutdown() {
        try {
            if (spoolWriter != null) {
                spoolWriter.close();
            }

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            if (reader.getHistory() instanceof DefaultHistory) {
                try {
                    ((DefaultHistory) reader.getHistory()).save();
                } catch (final IOException e) {
                    logger.warn("Failed to save history: " + e.getMessage());
                }
            }

            terminal.close();
        } catch (final SQLException | IOException e) {
            logger.error("Error during shutdown: " + e.getMessage());
        }
    }
}
