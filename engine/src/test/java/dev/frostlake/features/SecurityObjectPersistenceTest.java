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
package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Network rules, network policies, password policies, secrets and their attachments survive a reload. */
public class SecurityObjectPersistenceTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_security_");
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dataDir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dataDir.toFile());
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

    @Test
    public void everyKindAndItsAttachmentsSurviveAReload() {
        final DatabaseEngine first = new DatabaseEngine(config);
        first.execute("CREATE DATABASE persist_db");
        first.execute("CREATE SCHEMA persist_db.s");
        first.execute("USE SCHEMA persist_db.s");
        first.execute("CREATE NETWORK RULE r TYPE = IPV4 VALUE_LIST = ('1.1.1.1', '2.2.2.2') COMMENT = 'rule'");
        first.execute("CREATE NETWORK POLICY np ALLOWED_NETWORK_RULE_LIST = ('r') BLOCKED_IP_LIST = ('3.3.3.3')");
        first.execute("CREATE PASSWORD POLICY pp PASSWORD_MIN_LENGTH = 20");
        first.execute("CREATE SECRET sec TYPE = PASSWORD USERNAME = 'u' PASSWORD = 'p'");
        first.execute("ALTER ACCOUNT SET PASSWORD POLICY pp");
        first.execute("ALTER ACCOUNT SET NETWORK_POLICY = np");
        first.shutdown();

        final DatabaseEngine second = new DatabaseEngine(config);
        second.execute("USE SCHEMA persist_db.s");
        ResultSet rows = second.executeQuery("DESC NETWORK RULE r");
        assertEquals("1.1.1.1,2.2.2.2", rows.getRows().get(0).getValue(rows.getColumnIndex("value_list")));
        assertEquals("rule", rows.getRows().get(0).getValue(rows.getColumnIndex("comment")));
        rows = second.executeQuery("DESC NETWORK POLICY np");
        assertEquals(2, rows.getRows().size());
        rows = second.executeQuery("DESC PASSWORD POLICY pp");
        assertEquals("20", rows.getRows().get(3).getValue(1));
        rows = second.executeQuery("DESC SECRET sec");
        assertEquals("u", rows.getRows().get(0).getValue(rows.getColumnIndex("username")));
        assertEquals(1, second.executeQuery("SHOW PASSWORD POLICIES ON ACCOUNT").getRows().size());
        second.shutdown();
    }
}
