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
 * A MERGE holds a hybrid table's keys as an UPDATE, a DELETE and an INSERT do, before any row is written
 * (documented): a matched row's new image may not repeat a primary key or point a foreign key at no parent, a
 * matched parent row a child names may neither leave nor change its key, and an inserted row names a parent with
 * each foreign key - inside a transaction too, where a duplicate key is refused at the MERGE. Engine only: hybrid
 * tables are not available on every account.
 */
public class HybridTableMergeKeysTest {

    private static final String PARENT_KEY =
        "Foreign key constraint \"SYS_INDEX_PLAYER_FOREIGN_KEY_TEAM_ID_TEAM_TEAM_ID\" was violated.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE hm");
        engine.execute("USE SCHEMA hm.PUBLIC");
        engine.execute("CREATE HYBRID TABLE team (team_id INT PRIMARY KEY, team_name VARCHAR(40) UNIQUE)");
        engine.execute("CREATE HYBRID TABLE player (player_id INT PRIMARY KEY, last_name VARCHAR(40), team_id INT,"
            + " FOREIGN KEY (team_id) REFERENCES team (team_id))");
        engine.execute("INSERT INTO team VALUES (1, 'Bayern'), (2, 'Dortmund'), (3, 'Leipzig')");
        engine.execute("INSERT INTO player VALUES (100, 'Kane', 1), (200, 'Reus', 2)");
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
    public void aMatchedRowsImageMayNotRepeatAKey() {
        assertEquals("Primary key already exists", refusal(
            "MERGE INTO team USING (SELECT 3 id) s ON team.team_id = s.id WHEN MATCHED THEN UPDATE SET team_id = 1"));
        assertEquals("Duplicate key value violates unique constraint \"SYS_INDEX_TEAM_UNIQUE_TEAM_NAME\"", refusal(
            "MERGE INTO team USING (SELECT 3 id) s ON team.team_id = s.id"
                + " WHEN MATCHED THEN UPDATE SET team_name = 'Bayern'"));
        assertEquals("1, Bayern | 2, Dortmund | 3, Leipzig", rows("SELECT * FROM team ORDER BY team_id"));
        engine.execute(
            "MERGE INTO team USING (SELECT 3 id) s ON team.team_id = s.id WHEN MATCHED THEN UPDATE SET team_id = 13");
        assertEquals("1, Bayern | 2, Dortmund | 13, Leipzig", rows("SELECT * FROM team ORDER BY team_id"));
    }

    @Test
    public void aMatchedRowMayNotPointAForeignKeyAtNoParent() {
        assertEquals(PARENT_KEY, refusal("MERGE INTO player USING (SELECT 100 id) s ON player.player_id = s.id"
            + " WHEN MATCHED THEN UPDATE SET team_id = 7"));
        engine.execute("MERGE INTO player USING (SELECT 100 id) s ON player.player_id = s.id"
            + " WHEN MATCHED THEN UPDATE SET team_id = 3");
        assertEquals("100, 3 | 200, 2", rows("SELECT player_id, team_id FROM player ORDER BY player_id"));
    }

    @Test
    public void aParentRowAChildNamesMayNotLeave() {
        assertEquals(PARENT_KEY, refusal(
            "MERGE INTO team USING (SELECT 1 id) s ON team.team_id = s.id WHEN MATCHED THEN DELETE"));
        assertEquals(PARENT_KEY, refusal(
            "MERGE INTO team USING (SELECT 1 id) s ON team.team_id = s.id WHEN MATCHED THEN UPDATE SET team_id = 11"));
        assertEquals("1, Bayern | 2, Dortmund | 3, Leipzig", rows("SELECT * FROM team ORDER BY team_id"));
        engine.execute("MERGE INTO team USING (SELECT 3 id) s ON team.team_id = s.id WHEN MATCHED THEN DELETE");
        assertEquals("1, Bayern | 2, Dortmund", rows("SELECT * FROM team ORDER BY team_id"));
    }

    @Test
    public void anInsertedRowNamesAParentAndANewKey() {
        assertEquals(PARENT_KEY, refusal("MERGE INTO player USING (SELECT 300 id, 9 team) s ON player.player_id = s.id"
            + " WHEN NOT MATCHED THEN INSERT VALUES (s.id, 'Sane', s.team)"));
        assertEquals("Primary key already exists", refusal("MERGE INTO player USING (SELECT 300 id) s"
            + " ON player.player_id = s.id WHEN NOT MATCHED THEN INSERT VALUES (100, 'Sane', 1)"));
        engine.execute("BEGIN");
        assertEquals("Primary key already exists", refusal("MERGE INTO player USING (SELECT 300 id) s"
            + " ON player.player_id = s.id WHEN NOT MATCHED THEN INSERT VALUES (100, 'Sane', 1)"));
        engine.execute("ROLLBACK");
        engine.execute("MERGE INTO player USING (SELECT 300 id, 3 team) s ON player.player_id = s.id"
            + " WHEN NOT MATCHED THEN INSERT VALUES (s.id, 'Sane', s.team)");
        assertEquals("100, 1 | 200, 2 | 300, 3", rows("SELECT player_id, team_id FROM player ORDER BY player_id"));
    }
}
