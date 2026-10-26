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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CREATE SCHEMA with an unqualified name clones a schema of another database into the current one, as it clones one
 * of its own (see CloneSchemaUnqualifiedTargetTest for the current database, live-verified). The live account allows
 * no CREATE DATABASE, so the other database is the embedded engine's own check.
 */
public class CloneSchemaFromAnotherDatabaseTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE src");
        engine.execute("CREATE TABLE src.PUBLIC.t (a INT)");
        engine.execute("INSERT INTO src.PUBLIC.t VALUES (1), (2)");
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void theCloneLandsInTheCurrentDatabase() {
        assertEquals("Schema S2 successfully created.",
            engine.executeQuery("CREATE SCHEMA s2 CLONE src.PUBLIC").getRows().get(0).getValue(0));
        assertEquals(2L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM d.s2.t").getRows().get(0).getValue(0))
            .longValue());
        // The source is untouched, and a new schema in the current database becomes the session's.
        assertEquals(2L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM src.PUBLIC.t").getRows().get(0)
            .getValue(0)).longValue());
        assertEquals("D|S2", engine.executeQuery("SELECT CURRENT_DATABASE() || '|' || CURRENT_SCHEMA()").getRows()
            .get(0).getValue(0));
    }
}
