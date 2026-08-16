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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests named FILE FORMAT objects: CREATE / ALTER / DROP / SHOW / DESCRIBE, and resolution of
 * {@code FILE_FORMAT = (FORMAT_NAME = '…')} in a COPY load (the named format supplies TYPE + options).
 */
public class FileFormatTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("file_format_test_");
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE data_stage URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private Map<String, String> describe(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Map<String, String> props = new HashMap<>();
        for (final Row row : rs.getRows()) {
            // Four-column DESC: property(0) | property_type(1) | property_value(2) | property_default(3).
            props.put(String.valueOf(row.getValue(0)), String.valueOf(row.getValue(2)));
        }
        return props;
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void createAndDescribe() {
        engine.execute("CREATE FILE FORMAT my_csv TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1");
        final Map<String, String> p = describe("DESCRIBE FILE FORMAT my_csv");
        assertEquals("CSV", p.get("TYPE"));
        assertEquals("|", p.get("FIELD_DELIMITER"));
        assertEquals("1", p.get("SKIP_HEADER"));
    }

    @Test
    public void showFileFormats() {
        engine.execute("CREATE FILE FORMAT f1 TYPE = JSON");
        final ResultSet rs = engine.executeQuery("SHOW FILE FORMATS");
        boolean found = false;
        // Columns: created_on(0), name(1), database_name(2), schema_name(3), type(4), owner(5), comment(6).
        for (final Row row : rs.getRows()) {
            if ("F1".equalsIgnoreCase(String.valueOf(row.getValue(1))) && "JSON".equals(row.getValue(4))) {
                found = true;
            }
        }
        assertTrue(found, "SHOW FILE FORMATS should list the created format");
    }

    @Test
    public void dropFileFormat() {
        engine.execute("CREATE FILE FORMAT f2 TYPE = CSV");
        engine.execute("DROP FILE FORMAT f2");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE FILE FORMAT f2");
            }
        });
    }

    @Test
    public void alterRenameAndSet() {
        engine.execute("CREATE FILE FORMAT old_ff TYPE = CSV");
        engine.execute("ALTER FILE FORMAT old_ff RENAME TO new_ff");
        assertEquals("CSV", describe("DESCRIBE FILE FORMAT new_ff").get("TYPE"));

        engine.execute("ALTER FILE FORMAT new_ff SET FIELD_DELIMITER = ';'");
        assertEquals(";", describe("DESCRIBE FILE FORMAT new_ff").get("FIELD_DELIMITER"));
    }

    @Test
    public void copyResolvesFormatName() throws IOException {
        Files.writeString(stageDir.resolve("d.csv"), "id|name\n1|Alice\n2|Bob\n");
        engine.execute("CREATE FILE FORMAT pipe_fmt TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");

        // The named format supplies TYPE = CSV, FIELD_DELIMITER = '|', SKIP_HEADER = 1.
        engine.execute("COPY INTO t FROM @data_stage FILE_FORMAT = (FORMAT_NAME = 'pipe_fmt')");

        assertEquals(2, count("t"));
        final ResultSet rs = engine.executeQuery("SELECT name FROM t WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
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
