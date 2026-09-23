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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE TABLE takes COPY GRANTS among its table options when there is an object to copy the grants from: the one
 * it replaces, clones or copies; a plain create is refused.
 */
public class CreateTableCopyGrantsTest extends BaseDatabaseTest {

    @Test
    public void copyGrantsIsAcceptedInEveryShape() {
        engine.execute("USE SCHEMA test_db.test_schema");
        final String refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE cg_base (a INT) COPY GRANTS COMMENT = 'base'");
            }
        }).getMessage();
        assertTrue(refusal.contains("Invalid operation COPY GRANTS without specifying source object."), refusal);
        engine.execute("CREATE TABLE cg_base (a INT) COMMENT = 'base'");
        engine.execute("INSERT INTO cg_base VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE cg_base (a INT) CLUSTER BY (a) COPY GRANTS COMMENT = 'again'");
        engine.execute("INSERT INTO cg_base VALUES (2)");
        engine.execute("CREATE OR REPLACE TABLE cg_ctas (a INT) COPY GRANTS AS SELECT a FROM cg_base");
        engine.execute("CREATE TABLE cg_clone CLONE cg_base COPY GRANTS");
        engine.execute("CREATE TABLE cg_like LIKE cg_base COPY GRANTS");
        assertEquals("again", engine.executeQuery("SHOW TABLES LIKE 'CG_BASE' ->> SELECT \"comment\" FROM $1")
            .getRows().get(0).getValue(0));
        assertEquals(1L, count("cg_ctas"));
        assertEquals(1L, count("cg_clone"));
        assertEquals(0L, count("cg_like"));
    }

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0))
            .longValue();
    }
}
