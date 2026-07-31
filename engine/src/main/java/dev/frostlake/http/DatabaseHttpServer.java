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

import tools.jackson.databind.ObjectMapper;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.config.EngineConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTP server for Frostlake SQL Engine
 * Provides REST API for SQL execution with concurrent multi-user support
 */
public class DatabaseHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHttpServer.class);

    private final HttpServer server;
    private final ConcurrentDatabaseEngine engine;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final int port;
    private final EngineConfig config;

    public DatabaseHttpServer(final int port) throws IOException {
        this(createConfigWithPort(port));
    }

    private static EngineConfig createConfigWithPort(final int port) {
        EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        return config;
    }

    public DatabaseHttpServer(final EngineConfig config) throws IOException {
        this.config = config;
        this.port = config.getHttpPort();
        this.engine = new ConcurrentDatabaseEngine();

        String host = config.getHttpHost();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // Set up handlers
        server.createContext("/api/execute", new ExecuteSqlHandler());
        server.createContext("/api/health", new HealthCheckHandler());
        server.createContext("/api/sessions", new SessionInfoHandler());

        // Use a thread pool for handling requests
        int maxConnections = config.getMaxConnections();
        server.setExecutor(Executors.newFixedThreadPool(maxConnections));

        logger.info("HTTP server created on {}:{} with max {} connections", host, port, maxConnections);
    }

    /**
     * Start the HTTP server
     */
    public void start() {
        server.start();
        logger.info("Frostlake HTTP server started on port {}", port);
        logger.info("API endpoints:");
        logger.info("  POST /api/execute - Execute SQL");
        logger.info("  GET  /api/health - Health check");
        logger.info("  GET  /api/sessions - Session information");
    }

    /**
     * Stop the HTTP server
     */
    public void stop() {
        logger.info("Stopping Frostlake HTTP server");
        server.stop(3);
        engine.shutdown();
        logger.info("Frostlake HTTP server stopped");
    }

    public ConcurrentDatabaseEngine getEngine() {
        return engine;
    }

    /**
     * Handler for SQL execution
     * POST /api/execute
     * Body: { "sql": "SELECT * FROM users", "sessionId": "optional-session-id" }
     */
    private class ExecuteSqlHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                // Parse request
                String requestBody = readRequestBody(exchange);
                SqlRequest request = MAPPER.readValue(requestBody, SqlRequest.class);

                if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\":\"SQL is required\"}");
                    return;
                }

                // Get or create session
                SessionContext session = engine.getOrCreateSession(request.getSessionId());

                // Execute SQL
                long startTime = System.currentTimeMillis();
                ExecutionResult result = engine.execute(request.getSql(), session);
                long executionTime = System.currentTimeMillis() - startTime;

                // Build response
                SqlResponse response;
                if (result.isSuccess()) {
                    response = SqlResponse.success(
                        session.getSessionId(),
                        result.getResultSets(),
                        executionTime
                    );
                } else {
                    response = SqlResponse.error(
                        session.getSessionId(),
                        result.getErrorMessage()
                    );
                }

                // Send response
                String responseJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
                sendResponse(exchange, 200, responseJson);

            } catch (final Exception e) {
                logger.error("Error handling SQL execution", e);
                // Serialised, not hand-assembled: escaping only the quote left every other character that
                // JSON forbids raw in a string to corrupt the body. A backslash, a tab or a newline was
                // enough — and compilation errors always carry a newline, so this was one message away
                // from emitting a response no client could parse.
                sendResponse(exchange, 500, MAPPER.writeValueAsString(
                    SqlResponse.error(null, e.getMessage())));
            }
        }
    }

    /**
     * Handler for health check
     * GET /api/health
     */
    private class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                int activeSessions = engine.getActiveSessionCount();
                String response = String.format(
                    "{\"status\":\"healthy\",\"activeSessions\":%d}",
                    activeSessions
                );
                sendResponse(exchange, 200, response);
            } catch (final Exception e) {
                logger.error("Error handling health check", e);
                sendResponse(exchange, 500, "{\"status\":\"error\"}");
            }
        }
    }

    /**
     * Handler for session information
     * GET /api/sessions
     */
    private class SessionInfoHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                int activeSessions = engine.getActiveSessionCount();
                String response = String.format(
                    "{\"activeSessions\":%d}",
                    activeSessions
                );
                sendResponse(exchange, 200, response);
            } catch (final Exception e) {
                logger.error("Error handling session info", e);
                sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    // Helper methods

    private String readRequestBody(final HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendResponse(final HttpExchange exchange, final int statusCode, final String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    public EngineConfig getConfig() {
        return config;
    }

    /**
     * Execute an init SQL file statement by statement, respecting $$-quoted blocks.
     * Each semicolon-terminated statement is executed independently so that
     * USE DATABASE / USE SCHEMA changes are visible to subsequent statements.
     */
    private static void executeInitFile(final DatabaseHttpServer server,
                                        final SessionContext initSession,
                                        final String filePath) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        StringBuilder buf = new StringBuilder();
        String line;
        int dollarCount = 0;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            // Skip blank lines and single-line comments outside dollar-quoted blocks
            if (dollarCount == 0 && (trimmed.isEmpty() || trimmed.startsWith("--"))) continue;

            buf.append(line).append("\n");

            // Track $$ pairs to know if we're inside a dollar-quoted string
            for (int i = 0; i < line.length() - 1; i++) {
                if (line.charAt(i) == '$' && line.charAt(i + 1) == '$') {
                    dollarCount++;
                    i++;
                }
            }

            // Statement is complete when semicolon appears at top level (outside $$ blocks)
            if (dollarCount % 2 == 0 && trimmed.endsWith(";")) {
                String stmt = buf.toString().trim();
                if (stmt.endsWith(";")) stmt = stmt.substring(0, stmt.length() - 1).trim();
                if (!stmt.isEmpty()) {
                    try {
                        server.getEngine().execute(stmt, initSession);
                    } catch (final Exception e) {
                        logger.warn("Init file statement failed (continuing): {} — {}", stmt.length() > 60 ? stmt.substring(0, 60) + "..." : stmt, e.getMessage());
                    }
                }
                buf.setLength(0);
                dollarCount = 0;
            }
        }
        reader.close();

        // Execute any remaining SQL without trailing semicolon
        String remaining = buf.toString().trim();
        if (!remaining.isEmpty()) {
            try {
                server.getEngine().execute(remaining, initSession);
            } catch (final Exception e) {
                logger.warn("Init file statement failed (continuing): {} — {}", remaining.length() > 60 ? remaining.substring(0, 60) + "..." : remaining, e.getMessage());
            }
        }
    }

    /**
     * Main method to start the server
     */
    public static void main(final String[] args) {
        try {
            // Load configuration
            EngineConfig config = new EngineConfig();
            String initFile = null;

            // Parse arguments: [port] [configFile] [-f|--file <sqlFile>]
            int positional = 0;
            for (int i = 0; i < args.length; i++) {
                if ("-f".equals(args[i]) || "--file".equals(args[i])) {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: -f/--file requires a file path argument");
                        System.exit(1);
                    }
                    initFile = args[++i];
                } else {
                    switch (positional) {
                        case 0:
                            try {
                                int port = Integer.parseInt(args[i]);
                                config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
                                logger.info("Port overridden via command line: {}", port);
                            } catch (final NumberFormatException e) {
                                logger.warn("Invalid port number argument, using configured value: {}", config.getHttpPort());
                            }
                            break;
                        case 1:
                            config = new EngineConfig(args[i]);
                            logger.info("Configuration loaded from: {}", args[i]);
                            break;
                    }
                    positional++;
                }
            }

            DatabaseHttpServer server = new DatabaseHttpServer(config);

            // Execute init file before starting to accept requests
            if (initFile != null) {
                File f = new File(initFile);
                if (!f.exists()) {
                    System.err.println("Error: init file not found: " + initFile);
                    System.exit(1);
                }
                logger.info("Executing init file: {}", initFile);
                SessionContext initSession = server.getEngine().getOrCreateSession("init");
                executeInitFile(server, initSession, initFile);
                logger.info("Init file executed successfully: {}", initFile);
            }

            server.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                server.stop();
            }));

            logger.info("Server is running. Press Ctrl+C to stop.");

        } catch (final Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }
}
