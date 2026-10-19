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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warehouses, users and compute pools are held, as roles are, under their names exactly as stored: an unquoted name
 * is its upper-cased self and a quoted one is verbatim, so {@code "wh"} and {@code WH} are two warehouses, each
 * reached by its own spelling alone (documented). Engine only: the live account allows no CREATE WAREHOUSE, USER
 * or COMPUTE POOL.
 */
public class AccountObjectExactNameTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    /** The names a SHOW lists, sorted. */
    private List<String> names(final String show) {
        final ResultSet rs = engine.executeQuery(show);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex("name"))));
        }
        Collections.sort(out);
        return out;
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aQuotedWarehouseAndItsUpperCasedTwinAreTwoWarehouses() {
        engine.execute("CREATE WAREHOUSE \"wh\"");
        engine.execute("CREATE WAREHOUSE WH");
        assertEquals(List.of("COMPUTE_WH", "WH", "wh"), names("SHOW WAREHOUSES LIKE '%wh%'"));
        engine.execute("DROP WAREHOUSE \"wh\"");
        assertEquals(List.of("COMPUTE_WH", "WH"), names("SHOW WAREHOUSES LIKE '%wh%'"));
        engine.execute("DROP WAREHOUSE WH");
        assertEquals(List.of("COMPUTE_WH"), names("SHOW WAREHOUSES LIKE '%wh%'"));
    }

    @Test
    public void aWarehouseIsReachedByItsOwnSpellingAlone() {
        engine.execute("CREATE WAREHOUSE \"wh\"");
        assertTrue(refusal("DROP WAREHOUSE WH").contains("Warehouse 'WH' does not exist"));
        engine.execute("DROP WAREHOUSE IF EXISTS WH");
        assertTrue(refusal("USE WAREHOUSE WH").contains("Object does not exist, or operation cannot be performed."));
        assertTrue(refusal("ALTER WAREHOUSE WH SUSPEND").contains("Warehouse 'WH' does not exist"));
        assertTrue(refusal("DESCRIBE WAREHOUSE WH").contains("Warehouse 'WH' does not exist"));
        engine.execute("USE WAREHOUSE \"wh\"");
        assertEquals("wh", answer("SELECT CURRENT_WAREHOUSE()"));
        engine.execute("ALTER WAREHOUSE \"wh\" RENAME TO \"wh2\"");
        assertEquals(List.of("wh2"), names("SHOW WAREHOUSES LIKE 'wh2'"));
        assertEquals("wh2", String.valueOf(engine.executeQuery("DESCRIBE WAREHOUSE \"wh2\"").getRows().get(0)
            .getValue(1)));
    }

    @Test
    public void aWarehouseAnotherStatementNamesIsMatchedExactly() {
        engine.execute("CREATE WAREHOUSE \"wh\"");
        engine.execute("CREATE DATABASE aon");
        engine.execute("USE SCHEMA aon.PUBLIC");
        assertEquals("Nonexistent warehouse WH was specified.",
            refusal("CREATE TASK t WAREHOUSE = WH SCHEDULE = '1 MINUTE' AS SELECT 1"));
        engine.execute("CREATE TASK t WAREHOUSE = \"wh\" SCHEDULE = '1 MINUTE' AS SELECT 1");
    }

    @Test
    public void aQuotedUserAndItsUpperCasedTwinAreTwoUsers() {
        engine.execute("CREATE USER \"us\"");
        engine.execute("CREATE USER US");
        final List<String> users = names("SHOW USERS LIKE 'us'");
        assertEquals(List.of("US", "us"), users);
        assertTrue(refusal("DESCRIBE USER \"Us\"").contains("User '\"Us\"' does not exist"));
        engine.execute("ALTER USER \"us\" SET COMMENT = 'lower'");
        engine.execute("DROP USER US");
        assertEquals(List.of("us"), names("SHOW USERS LIKE 'us'"));
        assertTrue(refusal("DROP USER US").contains("User 'US' does not exist"));
        engine.execute("ALTER USER \"us\" RENAME TO \"us2\"");
        assertEquals(List.of("us2"), names("SHOW USERS LIKE 'us2'"));
    }

    @Test
    public void aRoleIsAlteredAndRenamedByItsOwnSpelling() {
        engine.execute("CREATE ROLE \"rl\"");
        assertTrue(refusal("ALTER ROLE RL SET COMMENT = 'x'").contains("Role 'RL' does not exist"));
        assertTrue(refusal("DROP ROLE RL").contains("Role 'RL' does not exist"));
        engine.execute("ALTER ROLE \"rl\" RENAME TO \"r2\"");
        assertEquals(List.of("r2"), names("SHOW ROLES LIKE 'r2'"));
        engine.execute("DROP ROLE \"r2\"");
    }

    @Test
    public void aComputePoolIsReachedByItsOwnSpellingAlone() {
        engine.execute("CREATE COMPUTE POOL CP1 MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS");
        assertTrue(refusal("DESCRIBE COMPUTE POOL \"cp1\"").contains("Compute pool '\"cp1\"' does not exist"));
        assertTrue(refusal("DROP COMPUTE POOL \"cp1\"").contains("Compute pool '\"cp1\"' does not exist"));
        engine.execute("DESCRIBE COMPUTE POOL cp1");
        engine.execute("DROP COMPUTE POOL cp1");
    }

    @Test
    public void bothTwinsComeBackFromASnapshot(@TempDir final Path dir) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        final DatabaseEngine first = new DatabaseEngine(config);
        first.execute("CREATE WAREHOUSE \"wh\"");
        first.execute("CREATE WAREHOUSE WH");
        first.execute("CREATE USER \"us\"");
        first.execute("CREATE USER US");
        first.shutdown();
        final DatabaseEngine second = new DatabaseEngine(config);
        try {
            final DatabaseEngine kept = engine;
            engine = second;
            assertEquals(List.of("COMPUTE_WH", "WH", "wh"), names("SHOW WAREHOUSES LIKE '%wh%'"));
            assertEquals(List.of("US", "us"), names("SHOW USERS LIKE 'us'"));
            engine = kept;
        } finally {
            second.shutdown();
        }
    }
}
