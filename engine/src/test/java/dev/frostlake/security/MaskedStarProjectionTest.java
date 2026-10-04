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
 * A masking policy applies to a bare {@code SELECT *} as it does to a named column. The policy is applied by the
 * projection step, which a bare star used to skip — so {@code SELECT * FROM t}, a view over it and a CTAS or an
 * INSERT from it handed back the raw values while {@code SELECT s FROM t} and {@code SELECT t.* FROM t} masked them.
 * An unload, {@code COPY INTO @stage FROM t}, read the table's storage directly and wrote them to the stage.
 */
public class MaskedStarProjectionTest {

    private DatabaseEngine engine;
    private Path internalRoot;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("masked_star_stage_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE ms_db");
        engine.execute("USE SCHEMA ms_db.PUBLIC");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE TABLE t (id INT, s STRING WITH MASKING POLICY mp)");
        engine.execute("INSERT INTO t VALUES (1, 'secret'), (2, 'hidden')");
        engine.execute("CREATE TABLE u (id INT, note STRING)");
        engine.execute("INSERT INTO u VALUES (1, 'n1')");
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
    public void aBareStarMasks() {
        assertEquals("1|*** ; 2|***", rows("SELECT * FROM t ORDER BY id"));
        assertEquals("2|***", rows("SELECT * FROM t WHERE id = 2"));
        assertEquals("1|***", rows("SELECT * FROM t ORDER BY id LIMIT 1"));
        assertEquals("1|***|1|n1", rows("SELECT * FROM t JOIN u ON t.id = u.id"));
    }

    @Test
    public void aViewOverABareStarMasks() {
        engine.execute("CREATE VIEW v AS SELECT * FROM t");
        assertEquals("*** ; ***", rows("SELECT s FROM v ORDER BY id"));
    }

    @Test
    public void aCopyFromABareStarStoresMaskedValues() {
        engine.execute("CREATE TABLE c AS SELECT * FROM t");
        engine.execute("CREATE TABLE i (id INT, s STRING)");
        engine.execute("INSERT INTO i SELECT * FROM t");
        assertEquals("*** ; ***", rows("SELECT s FROM c ORDER BY id"));
        assertEquals("*** ; ***", rows("SELECT s FROM i ORDER BY id"));
    }

    @Test
    public void anUnloadFromAMaskedTableWritesMaskedValues() {
        engine.execute("CREATE STAGE unl");
        engine.execute("COPY INTO @unl/out FROM t FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertEquals("*** ; ***", rows("SELECT $2 FROM @unl/out ORDER BY $1"));
    }
}
