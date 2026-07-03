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

package dev.frostlake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration manager for Frostlake SQL Engine
 * Loads configuration from frostlake.properties file
 */
public class EngineConfig {

    private static final Logger logger = LoggerFactory.getLogger(EngineConfig.class);

    private static final String DEFAULT_CONFIG_FILE = "frostlake.properties";
    private static final String USER_CONFIG_FILE = System.getProperty("user.home") + "/.frostlake/frostlake.properties";

    // Configuration property names (keys). Reference these constants instead of the raw string keys so the
    // property vocabulary lives in one place (defaults, getters, and external setProperty() callers agree).
    public static final String PROP_HTTP_PORT = "http.port";
    public static final String PROP_HTTP_HOST = "http.host";
    public static final String PROP_HTTP_MAX_CONNECTIONS = "http.maxConnections";
    public static final String PROP_DATABASE_DEFAULT = "database.default";
    public static final String PROP_SCHEMA_DEFAULT = "schema.default";
    public static final String PROP_ACCOUNT_ID = "account.id";
    public static final String PROP_SNOWFLAKE_REGION = "snowflake.region";
    public static final String PROP_CONSTRAINTS_ENFORCE_PRIMARY_KEY = "constraints.enforce.primaryKey";
    public static final String PROP_CONSTRAINTS_ENFORCE_UNIQUE_KEY = "constraints.enforce.uniqueKey";
    public static final String PROP_CONSTRAINTS_ENFORCE_TYPES = "constraints.enforce.types";
    public static final String PROP_TIME_TRAVEL_ENABLED = "timeTravel.enabled";
    public static final String PROP_TRANSACTION_DEFERRED_APPLY = "transaction.deferredApply";
    public static final String PROP_COMMAND_REMOVE_ENABLED = "command.removeEnabled";
    public static final String PROP_DEFAULT_USER = "default.user";
    public static final String PROP_STORAGE_IN_MEMORY = "storage.inMemory";
    public static final String PROP_PERSISTENCE_ENABLED = "persistence.enabled";
    public static final String PROP_PERSISTENCE_DIRECTORY = "persistence.directory";
    public static final String PROP_DURABILITY_WAL_ENABLED = "durability.walEnabled";
    public static final String PROP_DURABILITY_WAL_FILE = "durability.walFile";
    public static final String PROP_DURABILITY_CHECKPOINT_INTERVAL = "durability.checkpointInterval";
    public static final String PROP_PERSISTENCE_AUTO_SAVE = "persistence.autoSave";
    public static final String PROP_PERSISTENCE_SAVE_INTERVAL_SECONDS = "persistence.saveIntervalSeconds";
    public static final String PROP_LOGGING_LEVEL = "logging.level";
    public static final String PROP_QUERY_HISTORY_SIZE = "query.history.size";
    public static final String PROP_QUERY_RESULT_CACHE_SIZE = "query.result.cache.size";
    public static final String PROP_STAGE_S3_LOCAL_ROOT = "stage.s3.localRoot";
    public static final String PROP_STAGE_S3_LOCAL_MAPPINGS = "stage.s3.localMappings";
    public static final String PROP_STAGE_INTERNAL_LOCAL_ROOT = "stage.internal.localRoot";

    // Default values
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final String DEFAULT_HTTP_HOST = "localhost";
    private static final int DEFAULT_MAX_CONNECTIONS = 100;
    private static final String DEFAULT_DATABASE = "SNOWFLAKE";
    private static final String DEFAULT_SCHEMA = "PUBLIC";
    private static final String DEFAULT_ACCOUNT_ID = "ABC12345";
    // Default connected user — picked up from the OS login (CURRENT_USER()), falling back to ADMIN.
    // An explicit `default.user` property still overrides this.
    private static final String DEFAULT_USER = System.getProperty("user.name", "ADMIN");
    private static final String DEFAULT_REGION = "PUBLIC.AWS_US_EAST_1";
    private static final String DEFAULT_STAGE_S3_LOCAL_ROOT =
        System.getProperty("user.home") + "/.frostlake_stages/s3";
    // Local root backing the implicit internal stages — the user stage (@~) and per-table stages (@%table),
    // which have no CREATE STAGE / URL. User stages live under <root>/users/<user>, table stages under
    // <root>/tables/<db.schema.table>.
    private static final String DEFAULT_STAGE_INTERNAL_LOCAL_ROOT =
        System.getProperty("user.home") + "/.frostlake_stages/internal";

    // Base directory for persisted data (catalog snapshot + per-table data + WAL). Overridable via the
    // SQL_ENGINE_DATA_DIR environment variable; otherwise defaults under the user's home directory, so a
    // default-configured engine never writes persistence files into the current working directory.
    private static final String DEFAULT_DATA_DIR = resolveDefaultDataDir();
    private static final String DEFAULT_PERSISTENCE_DIRECTORY = DEFAULT_DATA_DIR;
    private static final String DEFAULT_WAL_FILE = DEFAULT_DATA_DIR + "/wal.log";

    private final Properties properties;

    public EngineConfig() {
        this.properties = new Properties();
        loadDefaults();
        loadConfiguration();
    }

    public EngineConfig(final String configFilePath) {
        this.properties = new Properties();
        loadDefaults();
        loadConfigurationFromFile(configFilePath);
    }

    /**
     * Resolve the default base directory for persisted data: the {@code SQL_ENGINE_DATA_DIR} environment
     * variable when set (and non-blank), otherwise {@code <user-home>/.frostlake_engine/data}. Package-visible
     * and pure (env value + home directory injected) so it is unit-testable without mutating the process
     * environment.
     */
    static String resolveDefaultDataDir(final String envValue, final String userHome) {
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        return userHome + "/.frostlake_engine/data";
    }

    private static String resolveDefaultDataDir() {
        return resolveDefaultDataDir(System.getenv("SQL_ENGINE_DATA_DIR"), System.getProperty("user.home"));
    }

    private void loadDefaults() {
        properties.setProperty(PROP_HTTP_PORT, String.valueOf(DEFAULT_HTTP_PORT));
        properties.setProperty(PROP_HTTP_HOST, DEFAULT_HTTP_HOST);
        properties.setProperty(PROP_HTTP_MAX_CONNECTIONS, String.valueOf(DEFAULT_MAX_CONNECTIONS));
        properties.setProperty(PROP_DATABASE_DEFAULT, DEFAULT_DATABASE);
        properties.setProperty(PROP_SCHEMA_DEFAULT, DEFAULT_SCHEMA);
        properties.setProperty(PROP_ACCOUNT_ID, DEFAULT_ACCOUNT_ID);
        properties.setProperty(PROP_SNOWFLAKE_REGION, DEFAULT_REGION);
        properties.setProperty(PROP_CONSTRAINTS_ENFORCE_PRIMARY_KEY, "false");
        properties.setProperty(PROP_CONSTRAINTS_ENFORCE_UNIQUE_KEY, "false");
        properties.setProperty(PROP_CONSTRAINTS_ENFORCE_TYPES, "true");
        properties.setProperty(PROP_TIME_TRAVEL_ENABLED, "true");
        properties.setProperty(PROP_TRANSACTION_DEFERRED_APPLY, "true");
        properties.setProperty(PROP_COMMAND_REMOVE_ENABLED, "false");
        properties.setProperty(PROP_DEFAULT_USER, DEFAULT_USER);
        properties.setProperty(PROP_STORAGE_IN_MEMORY, "true");
        properties.setProperty(PROP_PERSISTENCE_ENABLED, "false");
        properties.setProperty(PROP_PERSISTENCE_DIRECTORY, DEFAULT_PERSISTENCE_DIRECTORY);
        properties.setProperty(PROP_DURABILITY_WAL_ENABLED, "false");
        properties.setProperty(PROP_DURABILITY_WAL_FILE, DEFAULT_WAL_FILE);
        properties.setProperty(PROP_DURABILITY_CHECKPOINT_INTERVAL, "0");
        properties.setProperty(PROP_PERSISTENCE_AUTO_SAVE, "true");
        properties.setProperty(PROP_PERSISTENCE_SAVE_INTERVAL_SECONDS, "60");
        properties.setProperty(PROP_LOGGING_LEVEL, "INFO");
        properties.setProperty(PROP_QUERY_HISTORY_SIZE, "10000");
        properties.setProperty(PROP_QUERY_RESULT_CACHE_SIZE, "500");
        properties.setProperty(PROP_STAGE_S3_LOCAL_ROOT, DEFAULT_STAGE_S3_LOCAL_ROOT);
        properties.setProperty(PROP_STAGE_S3_LOCAL_MAPPINGS, "");
        properties.setProperty(PROP_STAGE_INTERNAL_LOCAL_ROOT, DEFAULT_STAGE_INTERNAL_LOCAL_ROOT);
    }

    private void loadConfiguration() {
        // Try to load from classpath first
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded configuration from classpath: {}", DEFAULT_CONFIG_FILE);
            }
        } catch (final IOException e) {
            logger.debug("Could not load configuration from classpath: {}", e.getMessage());
        }

        // Try to load from user home directory (overrides classpath)
        Path userConfigPath = Paths.get(USER_CONFIG_FILE);
        if (Files.exists(userConfigPath)) {
            loadConfigurationFromFile(USER_CONFIG_FILE);
        }

        // Allow system properties to override
        properties.putAll(System.getProperties());
    }

    private void loadConfigurationFromFile(final String filePath) {
        try (InputStream is = new FileInputStream(filePath)) {
            properties.load(is);
            logger.info("Loaded configuration from file: {}", filePath);
        } catch (final IOException e) {
            logger.warn("Could not load configuration from file {}: {}", filePath, e.getMessage());
        }
    }

    public int getHttpPort() {
        return getIntProperty(PROP_HTTP_PORT, DEFAULT_HTTP_PORT);
    }

    public String getHttpHost() {
        return getProperty(PROP_HTTP_HOST, DEFAULT_HTTP_HOST);
    }

    public int getMaxConnections() {
        return getIntProperty(PROP_HTTP_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
    }

    public String getDefaultDatabase() {
        return getProperty(PROP_DATABASE_DEFAULT, DEFAULT_DATABASE);
    }

    public String getDefaultSchema() {
        return getProperty(PROP_SCHEMA_DEFAULT, DEFAULT_SCHEMA);
    }

    public String getAccountId() {
        return getProperty(PROP_ACCOUNT_ID, DEFAULT_ACCOUNT_ID);
    }

    public boolean isEnforcePrimaryKey() {
        return getBooleanProperty(PROP_CONSTRAINTS_ENFORCE_PRIMARY_KEY, false);
    }

    public boolean isEnforceUniqueKey() {
        return getBooleanProperty(PROP_CONSTRAINTS_ENFORCE_UNIQUE_KEY, false);
    }

    /**
     * When true (the default), DML coerces write values to the column type (VARCHAR(n) length enforcement,
     * numeric-string parsing, rejecting clearly-incompatible values), matching Snowflake. Set false to opt
     * out (lax typing). See docs/acid-snowflake-plan.md.
     */
    public boolean isEnforceTypes() {
        return getBooleanProperty(PROP_CONSTRAINTS_ENFORCE_TYPES, true);
    }

    public boolean isTimeTravelEnabled() {
        return getBooleanProperty(PROP_TIME_TRAVEL_ENABLED, true);
    }

    /**
     * When true (the default), DML buffers into a per-transaction write set and applies it on COMMIT
     * (deferred apply), giving real atomicity + READ COMMITTED across INSERT/UPDATE/DELETE/MERGE. Set to
     * false to use the legacy immediate-apply + undo-log path. See docs/acid-snowflake-plan.md.
     */
    public boolean isDeferredApply() {
        return getBooleanProperty(PROP_TRANSACTION_DEFERRED_APPLY, true);
    }

    /**
     * When true, the destructive {@code REMOVE}/{@code RM @stage} command (which deletes the files backing a
     * stage on the local filesystem) is allowed. Off by default as a safety guard against accidental data
     * loss; enable via {@code command.removeEnabled=true}.
     */
    public boolean isRemoveCommandEnabled() {
        return getBooleanProperty(PROP_COMMAND_REMOVE_ENABLED, false);
    }

    public String getRegion() {
        return getProperty(PROP_SNOWFLAKE_REGION, DEFAULT_REGION);
    }

    public String getDefaultUser() {
        return getProperty(PROP_DEFAULT_USER, DEFAULT_USER).toUpperCase();
    }

    public boolean isInMemoryStorage() {
        return getBooleanProperty(PROP_STORAGE_IN_MEMORY, true);
    }

    public String getLoggingLevel() {
        return getProperty(PROP_LOGGING_LEVEL, "INFO");
    }

    public boolean isPersistenceEnabled() {
        return getBooleanProperty(PROP_PERSISTENCE_ENABLED, false);
    }

    public String getPersistenceDirectory() {
        return getProperty(PROP_PERSISTENCE_DIRECTORY, DEFAULT_PERSISTENCE_DIRECTORY);
    }

    /**
     * When true, committed mutating statements are appended to a write-ahead log (fsync on commit) and
     * replayed on startup, so committed work survives a crash. Off by default — durability matters least
     * for the in-memory local/CI-testing use case. See docs/acid-snowflake-plan.md.
     */
    public boolean isWalEnabled() {
        return getBooleanProperty(PROP_DURABILITY_WAL_ENABLED, false);
    }

    public String getWalFile() {
        return getProperty(PROP_DURABILITY_WAL_FILE, DEFAULT_WAL_FILE);
    }

    /**
     * When &gt; 0 and the WAL is enabled, the engine writes a checkpoint (a full state snapshot plus log
     * truncation) after this many committed transactions, bounding the log's growth and speeding recovery.
     * 0 (the default) disables automatic checkpointing — {@code DatabaseEngine.checkpoint()} can still be
     * called explicitly. See docs/acid-snowflake-plan.md.
     */
    public int getWalCheckpointInterval() {
        return getIntProperty(PROP_DURABILITY_CHECKPOINT_INTERVAL, 0);
    }

    public boolean isAutoSaveEnabled() {
        return getBooleanProperty(PROP_PERSISTENCE_AUTO_SAVE, true);
    }

    public int getSaveIntervalSeconds() {
        return getIntProperty(PROP_PERSISTENCE_SAVE_INTERVAL_SECONDS, 60);
    }

    public int getQueryHistorySize() {
        return getIntProperty(PROP_QUERY_HISTORY_SIZE, 10000);
    }

    public int getQueryResultCacheSize() {
        return getIntProperty(PROP_QUERY_RESULT_CACHE_SIZE, 200);
    }

    /**
     * Local filesystem base for resolving {@code s3://bucket/key} references (an unmapped key resolves to
     * {@code <root>/bucket/key}). Lets JARs that live in S3-backed stages in production be loaded from local
     * disk for local/CI testing. Defaults to {@code ~/.frostlake_stages/s3}. See {@link S3PathResolver}.
     */
    public String getStageS3LocalRoot() {
        return getProperty(PROP_STAGE_S3_LOCAL_ROOT, DEFAULT_STAGE_S3_LOCAL_ROOT);
    }

    /**
     * Local filesystem root backing the implicit internal stages — the user stage ({@code @~}) and per-table
     * stages ({@code @%table}), which have no {@code CREATE STAGE} / URL. User stages resolve to
     * {@code <root>/users/<user>}, table stages to {@code <root>/tables/<db.schema.table>}. Defaults to
     * {@code ~/.frostlake_stages/internal}.
     */
    public String getStageInternalLocalRoot() {
        return getProperty(PROP_STAGE_INTERNAL_LOCAL_ROOT, DEFAULT_STAGE_INTERNAL_LOCAL_ROOT);
    }

    /**
     * Explicit {@code s3://}-prefix → local-directory overrides for S3 resolution: {@code ';'}-separated
     * {@code s3prefix=localdir} pairs (longest matching prefix wins). Empty by default. See {@link S3PathResolver}.
     */
    public String getStageS3LocalMappings() {
        return getProperty(PROP_STAGE_S3_LOCAL_MAPPINGS, "");
    }

    public String getProperty(final String key, final String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(final String key, final int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (final NumberFormatException e) {
                logger.warn("Invalid integer value for property {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    public boolean getBooleanProperty(final String key, final boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }

    public void setProperty(final String key, final String value) {
        properties.setProperty(key, value);
    }

    public Properties getProperties() {
        return new Properties(properties);
    }
}
