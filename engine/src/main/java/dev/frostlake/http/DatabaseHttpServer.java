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

import com.sun.net.httpserver.HttpServer;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.SqlScriptSplitter;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String NO_DELAY_PROPERTY = "sun.net.httpserver.nodelay";
    private final int port;
    private final EngineConfig config;

    public DatabaseHttpServer(final int port) throws IOException {
        this(createConfigWithPort(port));
    }

    private static EngineConfig createConfigWithPort(final int port) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        return config;
    }

    public DatabaseHttpServer(final EngineConfig config) throws IOException {
        this.config = config;
        this.port = config.getHttpPort();
        this.engine = new ConcurrentDatabaseEngine();
        enableNoDelay();

        final String host = config.getHttpHost();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // Set up handlers
        server.createContext("/api/execute", new ExecuteSqlHandler(engine));
        server.createContext("/api/health", new HealthCheckHandler(engine));
        server.createContext("/api/sessions", new SessionInfoHandler(engine));

        // Use a thread pool for handling requests
        final int maxConnections = config.getMaxConnections();
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
        logger.info("  POST /api/sessions - Start a session");
        logger.info("  DELETE /api/sessions/{id} - Release a session");
    }

    /**
     * TCP no-delay for every socket the server accepts. The JDK server leaves Nagle's algorithm on and
     * writes a response's headers and body as two segments, so on a kept-alive connection the body waits
     * for the client's delayed ACK — about 40 ms per statement on Linux and macOS. The JDK reads the
     * property once, when its server configuration first loads, so it is set before the first server is
     * created; a value the launcher chose is left alone.
     */
    private static void enableNoDelay() {
        if (System.getProperty(NO_DELAY_PROPERTY) == null) {
            System.setProperty(NO_DELAY_PROPERTY, "true");
        }
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
     * Execute an init SQL file statement by statement. The split comes from the engine's own
     * grammar ({@link SqlScriptSplitter}) — the previous character-level $$-parity/trailing-';'
     * scan mis-grouped everything after {@code SELECT '$$';} and cut multi-line literals. Each
     * statement still executes independently so USE DATABASE / USE SCHEMA changes are visible to
     * subsequent statements and one failure does not stop the file.
     */
    private static void executeInitFile(final DatabaseHttpServer server,
                                        final SessionContext initSession,
                                        final String filePath) throws Exception {
        final String script = Files.readString(Path.of(filePath));
        for (final String stmt : SqlScriptSplitter.split(script)) {
            try {
                server.getEngine().execute(stmt, initSession);
            } catch (final Exception e) {
                logger.warn("Init file statement failed (continuing): {} — {}",
                    stmt.length() > 60 ? stmt.substring(0, 60) + "..." : stmt, e.getMessage());
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
                                final int port = Integer.parseInt(args[i]);
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

            final DatabaseHttpServer server = new DatabaseHttpServer(config);

            // Execute init file before starting to accept requests
            if (initFile != null) {
                final File f = new File(initFile);
                if (!f.exists()) {
                    System.err.println("Error: init file not found: " + initFile);
                    System.exit(1);
                }
                logger.info("Executing init file: {}", initFile);
                final SessionContext initSession = server.getEngine().getOrCreateSession("init");
                executeInitFile(server, initSession, initFile);
                logger.info("Init file executed successfully: {}", initFile);
            }

            server.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("Shutdown hook triggered");
                    server.stop();
                }
            }));

            logger.info("Server is running. Press Ctrl+C to stop.");

        } catch (final Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }
}
