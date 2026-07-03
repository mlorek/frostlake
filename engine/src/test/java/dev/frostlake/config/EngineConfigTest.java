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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EngineConfig
 */
public class EngineConfigTest {

    @Test
    public void testDefaultConfiguration() {
        EngineConfig config = new EngineConfig();

        // Test defaults
        assertEquals(8080, config.getHttpPort());
        assertEquals("localhost", config.getHttpHost());
        assertEquals(100, config.getMaxConnections());
        assertEquals("SNOWFLAKE", config.getDefaultDatabase());
        assertEquals("PUBLIC", config.getDefaultSchema());
        assertEquals("ABC12345", config.getAccountId());
        assertTrue(config.isInMemoryStorage());
        assertEquals("INFO", config.getLoggingLevel());
    }

    @Test
    public void testCustomConfiguration(@TempDir final Path tempDir) throws IOException {
        // Create custom config file
        Path configFile = tempDir.resolve("test.properties");
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write("http.port=9090\n");
            writer.write("http.host=0.0.0.0\n");
            writer.write("http.maxConnections=200\n");
            writer.write("database.default=TEST_DB\n");
            writer.write("schema.default=TEST_SCHEMA\n");
            writer.write("account.id=CUSTOM123\n");
            writer.write("storage.inMemory=false\n");
            writer.write("logging.level=DEBUG\n");
        }

        // Load custom config
        EngineConfig config = new EngineConfig(configFile.toString());

        // Verify custom values
        assertEquals(9090, config.getHttpPort());
        assertEquals("0.0.0.0", config.getHttpHost());
        assertEquals(200, config.getMaxConnections());
        assertEquals("TEST_DB", config.getDefaultDatabase());
        assertEquals("TEST_SCHEMA", config.getDefaultSchema());
        assertEquals("CUSTOM123", config.getAccountId());
        assertFalse(config.isInMemoryStorage());
        assertEquals("DEBUG", config.getLoggingLevel());
    }

    @Test
    public void testGetIntProperty() {
        EngineConfig config = new EngineConfig();
        config.setProperty("test.int", "12345");

        assertEquals(12345, config.getIntProperty("test.int", 0));
        assertEquals(999, config.getIntProperty("nonexistent", 999));
    }

    @Test
    public void testGetBooleanProperty() {
        EngineConfig config = new EngineConfig();
        config.setProperty("test.bool.true", "true");
        config.setProperty("test.bool.false", "false");

        assertTrue(config.getBooleanProperty("test.bool.true", false));
        assertFalse(config.getBooleanProperty("test.bool.false", true));
        assertTrue(config.getBooleanProperty("nonexistent", true));
    }

    @Test
    public void testSetProperty() {
        EngineConfig config = new EngineConfig();

        config.setProperty("custom.property", "custom.value");
        assertEquals("custom.value", config.getProperty("custom.property", "default"));
    }

    @Test
    public void testInvalidIntProperty() {
        EngineConfig config = new EngineConfig();
        config.setProperty("test.invalid", "not-a-number");

        // Should return default value when parse fails
        assertEquals(999, config.getIntProperty("test.invalid", 999));
    }

    @Test
    public void testLoadNonExistentFile() {
        // Should not throw exception, just use defaults
        EngineConfig config = new EngineConfig("/nonexistent/path/config.properties");

        // Should still have defaults
        assertEquals(8080, config.getHttpPort());
    }

    @Test
    public void testPropertyOverride() {
        EngineConfig config = new EngineConfig();

        // Initial value
        assertEquals(8080, config.getHttpPort());

        // Override
        config.setProperty(EngineConfig.PROP_HTTP_PORT, "9999");
        assertEquals(9999, config.getHttpPort());
    }

    @Test
    public void testGetProperties() {
        EngineConfig config = new EngineConfig();
        config.setProperty("test.key", "test.value");

        var properties = config.getProperties();
        assertNotNull(properties);
        assertEquals("test.value", properties.getProperty("test.key"));
    }

    @Test
    public void testAccountIdConfiguration() {
        EngineConfig config = new EngineConfig();

        // Test default account ID
        assertEquals("ABC12345", config.getAccountId());

        // Test custom account ID
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "XYZ98765");
        assertEquals("XYZ98765", config.getAccountId());
    }

    @Test
    public void resolveDefaultDataDirPrefersEnvVar() {
        assertEquals("/custom/data", EngineConfig.resolveDefaultDataDir("/custom/data", "/home/u"));
        // Surrounding whitespace is trimmed.
        assertEquals("/custom/data", EngineConfig.resolveDefaultDataDir("  /custom/data  ", "/home/u"));
    }

    @Test
    public void resolveDefaultDataDirFallsBackToHomeDir() {
        assertEquals("/home/u/.frostlake_engine/data", EngineConfig.resolveDefaultDataDir(null, "/home/u"));
        // A blank env var is treated as unset.
        assertEquals("/home/u/.frostlake_engine/data", EngineConfig.resolveDefaultDataDir("", "/home/u"));
        assertEquals("/home/u/.frostlake_engine/data", EngineConfig.resolveDefaultDataDir("   ", "/home/u"));
    }

    @Test
    public void defaultPersistenceDirectoryIsNotCurrentWorkingDir() {
        EngineConfig config = new EngineConfig();
        // The default is now the resolved data dir (env var or home), no longer the CWD-relative ./data.
        assertNotEquals("./data", config.getPersistenceDirectory());
        assertNotEquals("./data/wal.log", config.getWalFile());
        // The WAL file co-locates under the persisted-data directory by default.
        assertTrue(config.getWalFile().startsWith(config.getPersistenceDirectory()));
        assertTrue(config.getWalFile().endsWith("wal.log"));
    }

    @Test
    public void propertyNameConstantsMatchWireKeys() {
        assertEquals("http.port", EngineConfig.PROP_HTTP_PORT);
        assertEquals("http.host", EngineConfig.PROP_HTTP_HOST);
        assertEquals("http.maxConnections", EngineConfig.PROP_HTTP_MAX_CONNECTIONS);
        assertEquals("database.default", EngineConfig.PROP_DATABASE_DEFAULT);
        assertEquals("schema.default", EngineConfig.PROP_SCHEMA_DEFAULT);
        assertEquals("account.id", EngineConfig.PROP_ACCOUNT_ID);
        assertEquals("snowflake.region", EngineConfig.PROP_SNOWFLAKE_REGION);
        assertEquals("constraints.enforce.primaryKey", EngineConfig.PROP_CONSTRAINTS_ENFORCE_PRIMARY_KEY);
        assertEquals("constraints.enforce.uniqueKey", EngineConfig.PROP_CONSTRAINTS_ENFORCE_UNIQUE_KEY);
        assertEquals("constraints.enforce.types", EngineConfig.PROP_CONSTRAINTS_ENFORCE_TYPES);
        assertEquals("timeTravel.enabled", EngineConfig.PROP_TIME_TRAVEL_ENABLED);
        assertEquals("transaction.deferredApply", EngineConfig.PROP_TRANSACTION_DEFERRED_APPLY);
        assertEquals("command.removeEnabled", EngineConfig.PROP_COMMAND_REMOVE_ENABLED);
        assertEquals("default.user", EngineConfig.PROP_DEFAULT_USER);
        assertEquals("storage.inMemory", EngineConfig.PROP_STORAGE_IN_MEMORY);
        assertEquals("persistence.enabled", EngineConfig.PROP_PERSISTENCE_ENABLED);
        assertEquals("persistence.directory", EngineConfig.PROP_PERSISTENCE_DIRECTORY);
        assertEquals("durability.walEnabled", EngineConfig.PROP_DURABILITY_WAL_ENABLED);
        assertEquals("durability.walFile", EngineConfig.PROP_DURABILITY_WAL_FILE);
        assertEquals("durability.checkpointInterval", EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL);
        assertEquals("persistence.autoSave", EngineConfig.PROP_PERSISTENCE_AUTO_SAVE);
        assertEquals("persistence.saveIntervalSeconds", EngineConfig.PROP_PERSISTENCE_SAVE_INTERVAL_SECONDS);
        assertEquals("logging.level", EngineConfig.PROP_LOGGING_LEVEL);
        assertEquals("query.history.size", EngineConfig.PROP_QUERY_HISTORY_SIZE);
        assertEquals("query.result.cache.size", EngineConfig.PROP_QUERY_RESULT_CACHE_SIZE);
        assertEquals("stage.s3.localRoot", EngineConfig.PROP_STAGE_S3_LOCAL_ROOT);
        assertEquals("stage.s3.localMappings", EngineConfig.PROP_STAGE_S3_LOCAL_MAPPINGS);
        assertEquals("stage.internal.localRoot", EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT);
    }
}
