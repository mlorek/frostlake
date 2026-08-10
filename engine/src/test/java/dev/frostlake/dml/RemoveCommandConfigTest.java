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

package dev.frostlake.dml;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The destructive {@code REMOVE}/{@code RM @stage} command is gated by the {@code command.removeEnabled}
 * config flag, which is off by default. When disabled, REMOVE/RM is rejected and the staged files are left
 * intact; when enabled, REMOVE deletes the staged files as usual.
 */
public class RemoveCommandConfigTest {

    private DatabaseEngine engine;
    private Path stageDir;

    private void startEngine(final boolean removeEnabled) throws IOException {
        stageDir = Files.createTempDirectory("remove_cfg_stage_");
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_COMMAND_REMOVE_ENABLED, String.valueOf(removeEnabled));
        config.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine = new DatabaseEngine(config);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        if (stageDir != null) {
            deleteRecursively(stageDir.toFile());
        }
    }

    @Test
    public void removeIsRejectedWhenDisabledByDefault() throws IOException {
        startEngine(false);
        Files.writeString(stageDir.resolve("a.csv"), "x\n");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("REMOVE @st");
            }
        });
        assertTrue(messageChain(ex).contains("command.removeEnabled"),
            "Expected the rejection to name the config flag, got: " + messageChain(ex));
        assertTrue(Files.exists(stageDir.resolve("a.csv")),
            "REMOVE must not delete files when disabled");
    }

    @Test
    public void rmIsAlsoRejectedWhenDisabled() throws IOException {
        startEngine(false);
        Files.writeString(stageDir.resolve("a.csv"), "x\n");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("RM @st");
            }
        });
        assertTrue(Files.exists(stageDir.resolve("a.csv")),
            "RM must not delete files when disabled");
    }

    @Test
    public void removeDeletesWhenEnabled() throws IOException {
        startEngine(true);
        Files.writeString(stageDir.resolve("a.csv"), "x\n");
        Files.writeString(stageDir.resolve("b.csv"), "y\n");

        final ResultSet rs = engine.executeQuery("REMOVE @st");
        assertEquals(2, rs.getRows().size());
        assertFalse(Files.exists(stageDir.resolve("a.csv")), "REMOVE should delete staged files when enabled");
        assertFalse(Files.exists(stageDir.resolve("b.csv")), "REMOVE should delete staged files when enabled");
    }

    private static String messageChain(final Throwable t) {
        final StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(cur.getMessage());
            cur = cur.getCause();
        }
        return sb.toString();
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
