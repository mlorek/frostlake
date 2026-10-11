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
import dev.frostlake.functions.scalar.file.BuildStageFileUrl;
import dev.frostlake.functions.scalar.file.PresignedUrls;
import dev.frostlake.http.rest.RestApiHandler;
import dev.frostlake.http.rest.RestResults;
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
    private final RestResults restResults;

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
        // The engine runs on the server's own configuration, so a file named on the command line sets the engine's
        // properties (database.default, http.session.idleTimeoutMinutes, ...) as well as the server's.
        this.engine = new ConcurrentDatabaseEngine(config);
        enableNoDelay();

        final String host = config.getHttpHost();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // Set up handlers. Each is wrapped, so a fault its own handling does not answer is still answered
        // instead of leaving the client to time out.
        // Every request passes the guard first: the host it names, the origin it comes from, and for the SQL
        // endpoints the kind of body it carries (see RequestGuard).
        final RequestGuard guard = new RequestGuard(server.getAddress().getAddress(), host,
            config.getCorsAllowedOrigins());
        server.createContext("/api/execute", new ErrorAnsweringHandler(new ExecuteSqlHandler(engine), guard, true));
        server.createContext("/api/health", new ErrorAnsweringHandler(new HealthCheckHandler(engine), guard, false));
        server.createContext("/api/sessions", new ErrorAnsweringHandler(new SessionInfoHandler(engine), guard, true));
        this.restResults = new RestResults(config.getRestSyncWaitMs(), config.getRestResultRetentionMs());
        server.createContext(RestApiHandler.CONTEXT,
            new ErrorAnsweringHandler(new RestApiHandler(engine, restResults), guard, false));
        server.createContext(PresignedUrls.CONTEXT, new ErrorAnsweringHandler(new PresignedFileHandler(), guard,
            false));
        server.createContext(BuildStageFileUrl.CONTEXT, new ErrorAnsweringHandler(new StageFileUrlHandler(engine),
            guard, false));
        // The engine names this server in the presigned and file URLs it hands out.
        engine.getEngine().getConfig().setProperty(EngineConfig.PROP_HTTP_HOST, host);
        engine.getEngine().getConfig().setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));

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
        logger.info("  /api/v2/... - Snowflake REST APIs for resource management");
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
        restResults.shutdown();
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
            // Parse arguments: [port] [configFile] [-f|--file <sqlFile>]
            final ServerArguments arguments;
            try {
                arguments = ServerArguments.parse(args);
            } catch (final IllegalArgumentException malformed) {
                System.err.println("Error: " + malformed.getMessage());
                System.exit(1);
                return;
            }
            final EngineConfig config = arguments.config();
            final String initFile = arguments.initFile();

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
