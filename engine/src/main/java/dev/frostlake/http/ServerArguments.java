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

import dev.frostlake.config.EngineConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The arguments {@link DatabaseHttpServer} is started with: {@code [port] [configFile] [-f|--file <sqlFile>]}.
 * The configuration is the file's when one is named and the discovered one otherwise, and a port given on the
 * command line wins over the port either names: it is applied once every argument has been read.
 */
final class ServerArguments {

    private static final Logger logger = LoggerFactory.getLogger(ServerArguments.class);

    private final EngineConfig config;
    private final String initFile;

    private ServerArguments(final EngineConfig config, final String initFile) {
        this.config = config;
        this.initFile = initFile;
    }

    /**
     * Reads the arguments.
     *
     * @param args the command line
     * @return the configuration to start with and the init file, if any
     * @throws IllegalArgumentException when {@code -f} or {@code --file} names no file
     */
    static ServerArguments parse(final String[] args) {
        String configFile = null;
        String initFile = null;
        String port = null;
        int positional = 0;
        for (int i = 0; i < args.length; i++) {
            if ("-f".equals(args[i]) || "--file".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("-f/--file requires a file path argument");
                }
                initFile = args[++i];
            } else {
                if (positional == 0) {
                    port = args[i];
                } else if (positional == 1) {
                    configFile = args[i];
                }
                positional++;
            }
        }
        final EngineConfig config = configFile == null ? new EngineConfig() : new EngineConfig(configFile);
        if (configFile != null) {
            logger.info("Configuration loaded from: {}", configFile);
        }
        if (port != null) {
            try {
                config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(Integer.parseInt(port)));
                logger.info("Port overridden via command line: {}", port);
            } catch (final NumberFormatException e) {
                logger.warn("Invalid port number argument, using configured value: {}", config.getHttpPort());
            }
        }
        return new ServerArguments(config, initFile);
    }

    /** The configuration the server and its engine start with. */
    EngineConfig config() {
        return config;
    }

    /** The SQL file to run before the server takes requests, or null. */
    String initFile() {
        return initFile;
    }
}
