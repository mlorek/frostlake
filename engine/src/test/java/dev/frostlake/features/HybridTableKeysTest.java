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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A hybrid table holds its PRIMARY KEY, UNIQUE and FOREIGN KEY constraints row by row on INSERT, whatever the engine's
 * switches say, each refusal naming the index the constraint builds; an ordinary table keeps taking duplicates
 * (documented on CREATE HYBRID TABLE). Engine only: hybrid tables are not available on every account.
 */
public class HybridTableKeysTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE hk");
        engine.execute("USE SCHEMA hk.PUBLIC");
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

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void aRepeatedPrimaryKeyIsRefused() {
        engine.execute("CREATE HYBRID TABLE h (id INT PRIMARY KEY, code VARCHAR UNIQUE, v INT)");
        engine.execute("INSERT INTO h VALUES (1, 'a', 10)");
        assertEquals("Primary key already exists", refusal("INSERT INTO h VALUES (1, 'b', 20)"));
        assertEquals("Primary key already exists", refusal("INSERT INTO h VALUES (4, 'd', 1), (4, 'e', 2)"));
        assertEquals("1, a", rows("SELECT id, code FROM h ORDER BY id"));
    }

    @Test
    public void aRepeatedUniqueValueIsRefused() {
        engine.execute("CREATE HYBRID TABLE h (id INT PRIMARY KEY, code VARCHAR UNIQUE, v INT)");
        engine.execute("INSERT INTO h VALUES (1, 'a', 10)");
        assertEquals("Duplicate key value violates unique constraint \"SYS_INDEX_H_UNIQUE_CODE\"",
            refusal("INSERT INTO h VALUES (2, 'a', 30)"));
        engine.execute("INSERT INTO h VALUES (5, NULL, 0)");
        engine.execute("INSERT INTO h VALUES (6, NULL, 0)");
        assertEquals("1, a | 5, null | 6, null", rows("SELECT id, code FROM h ORDER BY id"));
    }

    @Test
    public void aCompositePrimaryKeyIsOneKey() {
        engine.execute("CREATE HYBRID TABLE hc (a INT, b INT, PRIMARY KEY (a, b))");
        engine.execute("INSERT INTO hc VALUES (1, 1), (1, 2)");
        assertEquals("Primary key already exists", refusal("INSERT INTO hc VALUES (1, 1)"));
    }

    @Test
    public void aForeignKeyNeedsItsParentRow() {
        engine.execute("CREATE HYBRID TABLE team (team_id INT PRIMARY KEY, team_name VARCHAR(40))");
        engine.execute("CREATE HYBRID TABLE player (player_id INT PRIMARY KEY, team_id INT,"
            + " FOREIGN KEY (team_id) REFERENCES team (team_id))");
        engine.execute("INSERT INTO team VALUES (1, 'Bayern Munich')");
        engine.execute("INSERT INTO player VALUES (100, 1)");
        final String violated = "Foreign key constraint \"SYS_INDEX_PLAYER_FOREIGN_KEY_TEAM_ID_TEAM_TEAM_ID\" was"
            + " violated.";
        assertEquals(violated, refusal("INSERT INTO player VALUES (301, 3)"));
        assertEquals(violated, refusal("INSERT INTO player VALUES (200, NULL)"));
        assertEquals("100, 1", rows("SELECT player_id, team_id FROM player"));
    }

    @Test
    public void aParentRowOfTheSameTransactionCounts() {
        engine.execute("CREATE HYBRID TABLE team (team_id INT PRIMARY KEY)");
        engine.execute("CREATE HYBRID TABLE player (player_id INT PRIMARY KEY, team_id INT REFERENCES team (team_id))");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO team VALUES (7)");
        engine.execute("INSERT INTO player VALUES (1, 7)");
        engine.execute("COMMIT");
        assertEquals("1, 7", rows("SELECT player_id, team_id FROM player"));
    }

    @Test
    public void anOrdinaryTableStillTakesDuplicates() {
        engine.execute("CREATE TABLE plain (id INT PRIMARY KEY, code VARCHAR UNIQUE)");
        engine.execute("INSERT INTO plain VALUES (1, 'a'), (1, 'a')");
        assertEquals("2", rows("SELECT COUNT(*) FROM plain"));
    }
}
