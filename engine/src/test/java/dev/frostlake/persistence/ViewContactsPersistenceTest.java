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
package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A view's contacts survive a save and a reload, the ones a renamed contact moved into another schema included.
 */
public class ViewContactsPersistenceTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_view_contacts_");
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dataDir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void tearDown() {
        if (dataDir != null) {
            deleteRecursively(dataDir.toFile());
        }
    }

    private DatabaseEngine open(final boolean create) {
        final DatabaseEngine engine = new DatabaseEngine(config);
        if (create) {
            engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
            engine.execute("USE DATABASE test_db");
            engine.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
        } else {
            engine.execute("USE DATABASE test_db");
        }
        engine.execute("USE SCHEMA test_schema");
        return engine;
    }

    @Test
    public void aViewsContactsSurviveAReload() {
        final DatabaseEngine first = open(true);
        first.execute("CREATE TABLE vc_t (a INT)");
        first.execute("CREATE VIEW vc_v AS SELECT a FROM vc_t");
        first.execute("CREATE CONTACT vc_steward EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        first.execute("CREATE CONTACT vc_support URL = 'https://example.com/support'");
        first.execute("ALTER VIEW vc_v SET CONTACT STEWARD = vc_steward, SUPPORT = vc_support");
        first.execute("ALTER CONTACT vc_support RENAME TO test_db.public.vc_moved");
        first.shutdown();

        final DatabaseEngine second = open(false);
        final View view = second.getCatalog().getDatabase("test_db").getSchema("test_schema").getView("VC_V");
        assertEquals("TEST_DB.TEST_SCHEMA.VC_STEWARD", view.getContacts().get("STEWARD"));
        assertEquals("TEST_DB.PUBLIC.VC_MOVED", view.getContacts().get("SUPPORT"));
        final ResultSet contacts = second.executeQuery("SELECT purpose, email, url FROM TABLE("
            + "SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.vc_v', 'VIEW')) ORDER BY purpose");
        final StringBuilder text = new StringBuilder();
        for (final Row row : contacts.getRows()) {
            text.append(row.getValue(0)).append('|').append(row.getValue(1)).append('|').append(row.getValue(2))
                .append(';');
        }
        assertEquals("STEWARD|a@example.com|null;SUPPORT|null|https://example.com/support;", text.toString());
        second.shutdown();
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
