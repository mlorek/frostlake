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


package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
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
 * An unload reads its table as a query does, so a row access policy alone, with no masked column beside it, keeps the
 * rows it withholds off the stage, as the masking policies keep the masked values off it.
 */
public class RowAccessPolicyUnloadTest {

    private DatabaseEngine engine;
    private Path internalRoot;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("rap_unload_stage_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE ru_db");
        engine.execute("USE SCHEMA ru_db.PUBLIC");
        engine.execute("CREATE TABLE t (id INT, s STRING)");
        engine.execute("INSERT INTO t VALUES (1, 'alpha'), (2, 'beta')");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (i INT) RETURNS BOOLEAN -> i = 1");
        engine.execute("ALTER TABLE t ADD ROW ACCESS POLICY rap ON (id)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        deleteRecursively(internalRoot.toFile());
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

    /** Every row of the query, its cells joined by "|", the rows by " ; ". */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void anUnloadWritesOnlyTheRowsThePolicyAllows() {
        assertEquals("1|alpha", rows("SELECT * FROM t ORDER BY id"));
        engine.execute("CREATE STAGE unl");
        engine.execute("COPY INTO @unl/out FROM t FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertEquals("1|alpha", rows("SELECT $1, $2 FROM @unl/out ORDER BY $1"));
    }
}
